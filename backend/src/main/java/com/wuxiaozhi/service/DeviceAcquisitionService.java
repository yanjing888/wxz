package com.wuxiaozhi.service;

import com.wuxiaozhi.dto.device.DeviceStatusDto;
import com.wuxiaozhi.dto.device.TelemetrySampleDto;
import com.wuxiaozhi.dto.experiment.ExperimentConfig;
import com.wuxiaozhi.dto.experiment.StepConfig;
import com.wuxiaozhi.entity.LabSession;
import com.wuxiaozhi.repository.LabSessionRepository;
import com.wuxiaozhi.service.device.DeviceSessionState;
import com.wuxiaozhi.service.device.TensileCurveGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class DeviceAcquisitionService {

    private final LabSessionRepository sessionRepository;
    private final ExperimentConfigService experimentConfigService;
    private final Map<Long, DeviceSessionState> states = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public DeviceAcquisitionService(LabSessionRepository sessionRepository,
                                    ExperimentConfigService experimentConfigService) {
        this.sessionRepository = sessionRepository;
        this.experimentConfigService = experimentConfigService;
    }

    public DeviceStatusDto connect(Long sessionId, int stepId) {
        LabSession session = getSession(sessionId);
        if (!"tensile_steel".equals(session.getExperimentCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前实验未配置试验机采集");
        }
        StepConfig step = resolveStep(session.getExperimentCode(), stepId);
        if (!isDeviceStep(step)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前步骤不支持仪器采集");
        }

        DeviceSessionState state = states.computeIfAbsent(sessionId, id -> new DeviceSessionState());
        state.setSessionId(sessionId);
        DeviceNames names = resolveDeviceNames(step.getDeviceType());
        state.resetForStep(stepId, step.getDeviceType(), names.displayName(), names.samplingHz());

        DeviceStatusDto dto = toStatus(state);
        dto.setSnapshot(copySnapshot(state));
        return dto;
    }

    public DeviceStatusDto status(Long sessionId, int stepId) {
        DeviceSessionState state = states.get(sessionId);
        if (state == null || !state.isConnected() || state.getStepId() != stepId) {
            DeviceStatusDto dto = new DeviceStatusDto();
            dto.setConnected(false);
            dto.setState("idle");
            dto.setStepId(stepId);
            return dto;
        }
        DeviceStatusDto dto = toStatus(state);
        dto.setSnapshot(copySnapshot(state));
        return dto;
    }

    /**
     * 同步完成当前步骤仪器采集（拉伸曲线一次返回），避免 SSE 在代理下缓冲导致前端收不到结束事件。
     */
    public Map<String, Object> acquireSync(Long sessionId, int stepId) {
        connect(sessionId, stepId);
        DeviceSessionState state = requireState(sessionId, stepId);
        String type = nullToEmpty(state.getDeviceType());

        if ("dimension_measure".equals(type) || "post_measure".equals(type)) {
            readOnce(sessionId, stepId);
            return snapshot(sessionId, stepId);
        }
        if ("universal_tester".equals(type)) {
            if (state.getAcquiring().get()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "采集已在进行中");
            }
            state.getAcquiring().set(true);
            try {
                state.setState("acquiring");
                state.getCurve().clear();
                state.getSnapshot().clear();
                List<TelemetrySampleDto> full = buildTensileCurve(sessionId, state);
                state.getCurve().addAll(full);
                state.getSnapshot().putAll(summarizeTensileTest(full, state));
                state.setState("completed");
            } finally {
                state.getAcquiring().set(false);
            }
            return snapshot(sessionId, stepId);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前步骤不支持仪器采集");
    }

    public DeviceStatusDto readOnce(Long sessionId, int stepId) {
        DeviceSessionState state = requireState(sessionId, stepId);
        if (state.getAcquiring().get()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "试验机正在采集，请稍候");
        }

        switch (nullToEmpty(state.getDeviceType())) {
            case "dimension_measure" -> acquireSpecimenDimensions(sessionId, state);
            case "post_measure" -> acquirePostFracture(state);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前步骤请使用拉伸采集");
        }
        state.setState("completed");
        DeviceStatusDto dto = toStatus(state);
        dto.setSnapshot(copySnapshot(state));
        return dto;
    }

    public SseEmitter stream(Long sessionId, int stepId) {
        DeviceSessionState state = requireState(sessionId, stepId);
        if (!"universal_tester".equals(state.getDeviceType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前步骤不支持实时曲线采集");
        }
        if (!state.getAcquiring().compareAndSet(false, true)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "采集已在进行中");
        }

        SseEmitter emitter = new SseEmitter(600_000L);
        emitter.onCompletion(() -> state.getAcquiring().set(false));
        emitter.onTimeout(() -> {
            state.getAcquiring().set(false);
            emitter.complete();
        });

        state.setState("acquiring");
        state.getCurve().clear();
        state.getSnapshot().clear();

        List<TelemetrySampleDto> full = buildTensileCurve(sessionId, state);
        state.setStreamTask(executor.submit(() -> {
            try {
                sendEvent(emitter, "status", toStatus(state));
                int intervalMs = Math.max(50, 1000 / state.getSamplingHz());
                for (int i = 0; i < full.size(); i++) {
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    TelemetrySampleDto sample = full.get(i);
                    state.getCurve().add(sample);
                    sendEvent(emitter, "sample", sample);
                    Thread.sleep(intervalMs);
                }
                Map<String, Object> snap = summarizeTensileTest(full, state);
                state.getSnapshot().putAll(snap);
                state.setState("completed");
                state.getAcquiring().set(false);
                sendEvent(emitter, "complete", Map.of(
                        "snapshot", snap,
                        "pointCount", full.size()
                ));
                emitter.complete();
            } catch (Exception e) {
                state.getAcquiring().set(false);
                state.setState("ready");
                try {
                    sendEvent(emitter, "error", Map.of("message", e.getMessage() != null ? e.getMessage() : "采集中断"));
                } catch (IOException ignored) {
                }
                emitter.completeWithError(e);
            }
        }));

        return emitter;
    }

    public void stop(Long sessionId, int stepId) {
        DeviceSessionState state = states.get(sessionId);
        if (state == null) {
            return;
        }
        if (state.getStepId() == stepId) {
            state.cancelStream();
            if ("acquiring".equals(state.getState()) || "completed".equals(state.getState())) {
                if (!state.getCurve().isEmpty() && state.getSnapshot().isEmpty()) {
                    state.getSnapshot().putAll(summarizeTensileTest(state.getCurve(), state));
                }
                if (!state.getSnapshot().isEmpty()) {
                    state.setState("completed");
                } else {
                    state.setState("ready");
                }
            }
        }
    }

    public Map<String, Object> snapshot(Long sessionId, int stepId) {
        DeviceSessionState state = requireState(sessionId, stepId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("values", new LinkedHashMap<>(state.getSnapshot()));
        body.put("state", state.getState());
        body.put("pointCount", state.getCurve().size());
        if (!state.getCurve().isEmpty()) {
            TelemetrySampleDto last = state.getCurve().get(state.getCurve().size() - 1);
            body.put("live", Map.of(
                    "forceKn", last.getForceKn(),
                    "strainPct", last.getStrainPct(),
                    "stressMpa", last.getStressMpa(),
                    "displacementMm", last.getDisplacementMm()
            ));
        }
        List<Map<String, Object>> curveLite = new ArrayList<>();
        int stride = Math.max(1, state.getCurve().size() / 120);
        for (int i = 0; i < state.getCurve().size(); i += stride) {
            TelemetrySampleDto s = state.getCurve().get(i);
            curveLite.add(Map.of(
                    "strainPct", s.getStrainPct(),
                    "stressMpa", s.getStressMpa()
            ));
        }
        body.put("curve", curveLite);
        List<Map<String, Object>> samples = new ArrayList<>();
        for (TelemetrySampleDto s : state.getCurve()) {
            samples.add(Map.of(
                    "strainPct", s.getStrainPct(),
                    "stressMpa", s.getStressMpa(),
                    "forceKn", s.getForceKn(),
                    "displacementMm", s.getDisplacementMm()
            ));
        }
        body.put("samples", samples);
        return body;
    }

    public Map<String, Object> getSpecimen(Long sessionId) {
        DeviceSessionState state = states.get(sessionId);
        if (state == null || state.getSpecimen().isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(state.getSpecimen());
    }

    public void clearSession(Long sessionId) {
        DeviceSessionState state = states.remove(sessionId);
        if (state != null) {
            state.cancelStream();
        }
    }

    private void acquireSpecimenDimensions(Long sessionId, DeviceSessionState state) {
        Random r = new Random(sessionId * 31L + 7);
        double d = 9.96 + r.nextDouble() * 0.08;
        double l0 = 100.0 + r.nextDouble() * 0.4;
        String id = String.format("S-%03d", (sessionId % 900) + 100);
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("specimen_id", id);
        snap.put("d_mm", round(d, 2));
        snap.put("L0_mm", round(l0, 1));
        state.getSnapshot().clear();
        state.getSnapshot().putAll(snap);
        state.getSpecimen().clear();
        state.getSpecimen().putAll(snap);
    }

    private void acquirePostFracture(DeviceSessionState state) {
        double d = toDouble(state.getSpecimen().get("d_mm"), 10.0);
        double l0 = toDouble(state.getSpecimen().get("L0_mm"), 100.0);
        double d1 = d * (0.62 + new Random((long) (d * 1000)).nextDouble() * 0.06);
        double lu = l0 * (1.28 + new Random((long) (l0 * 10)).nextDouble() * 0.08);
        double delta = (lu - l0) / l0 * 100.0;
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("d1_mm", round(d1, 2));
        snap.put("Lu_mm", round(lu, 1));
        snap.put("delta_pct", round(delta, 2));
        state.getSnapshot().clear();
        state.getSnapshot().putAll(snap);
    }

    private List<TelemetrySampleDto> buildTensileCurve(Long sessionId, DeviceSessionState state) {
        double d = toDouble(state.getSpecimen().get("d_mm"), 10.0);
        double l0 = toDouble(state.getSpecimen().get("L0_mm"), 100.0);
        if (state.getSpecimen().isEmpty()) {
            acquireSpecimenDimensions(sessionId, state);
            d = toDouble(state.getSpecimen().get("d_mm"), d);
            l0 = toDouble(state.getSpecimen().get("L0_mm"), l0);
        }
        TensileCurveGenerator gen = new TensileCurveGenerator(d, l0);
        return gen.buildCurve(160);
    }

    private Map<String, Object> summarizeTensileTest(List<TelemetrySampleDto> curve, DeviceSessionState state) {
        double fMax = 0;
        double fYield = 0;
        double strainYield = 0;
        boolean yieldSet = false;
        for (TelemetrySampleDto p : curve) {
            if (p.getForceKn() > fMax) {
                fMax = p.getForceKn();
            }
            if (!yieldSet && p.getStressMpa() >= 230 && p.getStressMpa() <= 250) {
                fYield = p.getForceKn();
                strainYield = p.getStrainPct();
                yieldSet = true;
            }
        }
        if (!yieldSet && curve.size() > 20) {
            TelemetrySampleDto p = curve.get(25);
            fYield = p.getForceKn();
            strainYield = p.getStrainPct();
        }
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("F_max_kN", round(fMax, 3));
        snap.put("F_yield_kN", round(fYield, 3));
        snap.put("strain_yield_pct", round(strainYield, 3));
        snap.put("point_count", curve.size());
        return snap;
    }

    private DeviceSessionState requireState(Long sessionId, int stepId) {
        DeviceSessionState state = states.get(sessionId);
        if (state == null || !state.isConnected() || state.getStepId() != stepId) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先连接实验仪器");
        }
        return state;
    }

    private StepConfig resolveStep(String experimentCode, int stepId) {
        ExperimentConfig exp = experimentConfigService.getByCode(experimentCode);
        if (exp.getSteps() == null) {
            return null;
        }
        return exp.getSteps().get(String.valueOf(stepId));
    }

    private boolean isDeviceStep(StepConfig step) {
        return step != null && "device".equalsIgnoreCase(step.getDataSource());
    }

    private DeviceNames resolveDeviceNames(String deviceType) {
        return switch (nullToEmpty(deviceType)) {
            case "dimension_measure" -> new DeviceNames("电子游标卡尺采集终端", 2);
            case "universal_tester" -> new DeviceNames("微机控制电子万能试验机", 10);
            case "post_measure" -> new DeviceNames("断后尺寸测量系统", 2);
            default -> new DeviceNames("实验数据采集仪", 5);
        };
    }

    private DeviceStatusDto toStatus(DeviceSessionState state) {
        DeviceStatusDto dto = new DeviceStatusDto();
        dto.setConnected(state.isConnected());
        dto.setState(state.getState());
        dto.setDeviceName(state.getDeviceName());
        dto.setDeviceType(state.getDeviceType());
        dto.setSamplingHz(state.getSamplingHz());
        dto.setStepId(state.getStepId());
        return dto;
    }

    private Map<String, Object> copySnapshot(DeviceSessionState state) {
        return state.getSnapshot().isEmpty() ? Map.of() : new LinkedHashMap<>(state.getSnapshot());
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(name).data(data, org.springframework.http.MediaType.APPLICATION_JSON));
    }

    private static double toDouble(Object v, double def) {
        if (v == null) return def;
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static double round(double v, int scale) {
        double p = Math.pow(10, scale);
        return Math.round(v * p) / p;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private record DeviceNames(String displayName, int samplingHz) {
    }

    private LabSession getSession(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在"));
    }
}
