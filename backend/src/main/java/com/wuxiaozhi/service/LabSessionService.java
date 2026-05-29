package com.wuxiaozhi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiaozhi.dto.*;
import com.wuxiaozhi.dto.experiment.ExperimentConfig;
import com.wuxiaozhi.dto.experiment.StepConfig;
import com.wuxiaozhi.entity.CorrectionLog;
import com.wuxiaozhi.entity.EnvCheckLog;
import com.wuxiaozhi.entity.LabSession;
import com.wuxiaozhi.repository.CorrectionLogRepository;
import com.wuxiaozhi.repository.EnvCheckLogRepository;
import com.wuxiaozhi.repository.LabSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
public class LabSessionService {

    private static final long GUEST_USER_ID = 0L;

    private final LabSessionRepository sessionRepository;
    private final CorrectionLogRepository correctionLogRepository;
    private final EnvCheckLogRepository envCheckLogRepository;
    private final ExperimentConfigService experimentConfigService;
    private final DifyService difyService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public LabSessionService(LabSessionRepository sessionRepository,
                             CorrectionLogRepository correctionLogRepository,
                             EnvCheckLogRepository envCheckLogRepository,
                             ExperimentConfigService experimentConfigService,
                             DifyService difyService,
                             ObjectMapper objectMapper,
                             PlatformTransactionManager transactionManager) {
        this.sessionRepository = sessionRepository;
        this.correctionLogRepository = correctionLogRepository;
        this.envCheckLogRepository = envCheckLogRepository;
        this.experimentConfigService = experimentConfigService;
        this.difyService = difyService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public LabSession startSession(StartSessionRequest req) {
        ExperimentConfig exp = experimentConfigService.getByCode(req.getExperimentCode());
        LabSession session = new LabSession();
        session.setUserId(GUEST_USER_ID);
        session.setExperimentCode(exp.getCode());
        session.setExperimentName(exp.getName());
        session.setStudentName(req.getStudentName().trim());
        session.setStudentClass(req.getStudentClass() != null ? req.getStudentClass().trim() : "");
        session.setActiveStep(1);
        session.setStatus("ACTIVE");
        return sessionRepository.save(session);
    }

    public LabSession getSession(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在"));
    }

    @Transactional
    public LabSession updateStep(Long sessionId, int stepId) {
        LabSession session = getSession(sessionId);
        session.setActiveStep(stepId);
        return sessionRepository.save(session);
    }

    @Transactional
    public AssistResponse assist(Long sessionId, AssistRequest req) {
        AssistPrepare prepare = prepareAssist(sessionId, req);
        AssistResponse resp = difyService.assist("text-assist", prepare.inputs(), "guest-" + sessionId, prepare.experiment(),
                prepare.session().getActiveStep(), prepare.hasImage(), prepare.hasImage() ? req.getImageUrl() : null);
        persistAssistResult(sessionId, req, prepare, resp);
        return resp;
    }

    public SseEmitter assistStream(Long sessionId, AssistRequest req) {
        SseEmitter emitter = new SseEmitter(300_000L);
        emitter.onTimeout(emitter::complete);

        AssistPrepare prepare;
        try {
            prepare = prepareAssist(sessionId, req);
            emitter.send(SseEmitter.event().name("start").data(Map.of("ok", true)));
        } catch (Exception e) {
            sendStreamError(emitter, e);
            return emitter;
        }

        CompletableFuture.runAsync(() -> {
            try {
                AssistResponse resp = difyService.streamAssist("text-assist", prepare.inputs(), "guest-" + sessionId,
                        prepare.experiment(), prepare.session().getActiveStep(), prepare.hasImage(),
                        prepare.hasImage() ? req.getImageUrl() : null, delta -> sendChunk(emitter, delta));
                transactionTemplate.executeWithoutResult(status ->
                        persistAssistResult(sessionId, req, prepare, resp));
                emitter.send(SseEmitter.event().name("done").data(resp));
                emitter.complete();
            } catch (Exception e) {
                sendStreamError(emitter, e);
            }
        });
        return emitter;
    }

    private void sendStreamError(SseEmitter emitter, Exception e) {
        String message = e instanceof ResponseStatusException rse
                ? (rse.getReason() != null ? rse.getReason() : rse.getMessage())
                : (e.getMessage() != null ? e.getMessage() : "流式请求失败");
        try {
            emitter.send(SseEmitter.event().name("error").data(Map.of("message", message)));
            emitter.complete();
        } catch (IOException ignored) {
            emitter.completeWithError(e);
        }
    }

    private void sendChunk(SseEmitter emitter, String delta) {
        try {
            emitter.send(SseEmitter.event().name("chunk").data(new AssistStreamChunk(delta)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private AssistPrepare prepareAssist(Long sessionId, AssistRequest req) {
        LabSession session = getSession(sessionId);

        boolean hasImage = req.getImageUrl() != null && !req.getImageUrl().isBlank();
        String userMessage = req.getUserMessage() != null ? req.getUserMessage().trim() : "";
        if (userMessage.isBlank() && !hasImage) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入问题或上传图片");
        }
        if (userMessage.isBlank()) {
            userMessage = "请分析上传的实验图片。";
        }

        ExperimentConfig exp = experimentConfigService.getByCode(session.getExperimentCode());
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("query", userMessage);
        return new AssistPrepare(session, exp, userMessage, hasImage, inputs);
    }

    private void persistAssistResult(Long sessionId, AssistRequest req, AssistPrepare prepare, AssistResponse resp) {
        LabSession session = getSession(sessionId);
        session.setHelpCount(session.getHelpCount() + 1);
        if (resp.getMarks() != null) {
            session.setErrorPointCount(session.getErrorPointCount() + resp.getMarks().size());
        }
        sessionRepository.save(session);

        if ("vision_correction".equals(resp.getType()) || (prepare.hasImage() && resp.getMarks() != null && !resp.getMarks().isEmpty())) {
            CorrectionLog log = new CorrectionLog();
            log.setSessionId(sessionId);
            log.setStepId(0);
            log.setStepTitle("对话");
            log.setErrorType(resp.getErrorType());
            log.setDetail(resp.getDetail());
            log.setFeedback(resp.getFeedback());
            log.setImageUrl(req.getImageUrl());
            try {
                log.setMarksJson(objectMapper.writeValueAsString(resp.getMarks()));
            } catch (Exception ignored) {
            }
            correctionLogRepository.save(log);
        }
    }

    private record AssistPrepare(LabSession session, ExperimentConfig experiment, String userMessage,
                                 boolean hasImage, Map<String, Object> inputs) {
    }

    @Transactional
    public EnvCheckResponse envCheck(Long sessionId) {
        LabSession session = getSession(sessionId);
        Map<String, Object> inputs = new LinkedHashMap<>();
        if (!"general".equals(session.getExperimentCode())) {
            putExperimentInputs(inputs, session.getExperimentName(), session.getExperimentCode());
        }
        inputs.put("note", "camera_ui_only");

        EnvCheckResponse resp = difyService.envCheck(inputs, "guest-" + sessionId);

        EnvCheckLog log = new EnvCheckLog();
        log.setSessionId(sessionId);
        log.setLevel(resp.getLevel());
        log.setSummary(resp.getSummary());
        log.setSuggestion(resp.getSuggestion());
        envCheckLogRepository.save(log);

        if ("L3".equals(resp.getLevel())) {
            session.setLabL3Count(session.getLabL3Count() + 1);
            sessionRepository.save(session);
        }
        return resp;
    }

    @Transactional
    public LabSession incrementTutView(Long sessionId) {
        LabSession session = getSession(sessionId);
        session.setTutViewCount(session.getTutViewCount() + 1);
        return sessionRepository.save(session);
    }

    @Transactional
    public LabSession finishSession(Long sessionId) {
        LabSession session = getSession(sessionId);
        session.setStatus("FINISHED");
        session.setEndTime(LocalDateTime.now());
        return sessionRepository.save(session);
    }

    public Map<String, Object> buildReportData(Long sessionId) {
        LabSession session = getSession(sessionId);
        ExperimentConfig exp = experimentConfigService.getByCode(session.getExperimentCode());
        List<CorrectionLog> corrections = correctionLogRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        List<EnvCheckLog> envLogs = envCheckLogRepository.findBySessionIdOrderByCreatedAtDesc(sessionId);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("sessionId", sessionId);
        report.put("experimentName", session.getExperimentName());
        report.put("studentName", session.getStudentName());
        report.put("studentClass", session.getStudentClass());
        report.put("generatedAt", LocalDateTime.now().toString());
        report.put("helpCount", session.getHelpCount());
        report.put("errorPointCount", session.getErrorPointCount());
        report.put("tutViewCount", session.getTutViewCount());
        report.put("labL3Count", session.getLabL3Count());
        report.put("reportKnowledge", exp.getReportKnowledge());
        report.put("reportPath", exp.getReportPath());
        report.put("corrections", corrections);
        report.put("envLogs", envLogs);
        return report;
    }

    private String resolveExperimentType(String fromRequest, String fromSession, String fromConfig) {
        if (fromRequest != null && !fromRequest.isBlank()) return fromRequest.trim();
        if (fromSession != null && !fromSession.isBlank()) return fromSession.trim();
        return fromConfig != null ? fromConfig : "";
    }

    private void putExperimentInputs(Map<String, Object> inputs, String experimentType, String experimentCode) {
        inputs.put("experiment_type", experimentType);
        inputs.put("experiment_name", experimentType);
        inputs.put("experiment_code", experimentCode);
    }
}
