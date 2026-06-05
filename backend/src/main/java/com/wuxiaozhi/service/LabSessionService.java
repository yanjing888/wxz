package com.wuxiaozhi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiaozhi.dto.*;
import com.wuxiaozhi.dto.experiment.ExperimentConfig;
import com.wuxiaozhi.dto.experiment.ExperimentDifyConfig;
import com.wuxiaozhi.dto.experiment.MarkDto;
import com.wuxiaozhi.dto.experiment.StepConfig;
import com.wuxiaozhi.entity.CorrectionLog;
import com.wuxiaozhi.entity.EnvCheckLog;
import com.wuxiaozhi.entity.LabSession;
import com.wuxiaozhi.entity.SessionDataLog;
import com.wuxiaozhi.repository.CorrectionLogRepository;
import com.wuxiaozhi.repository.EnvCheckLogRepository;
import com.wuxiaozhi.repository.LabSessionRepository;
import com.wuxiaozhi.repository.SessionDataLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(LabSessionService.class);

    private static final long GUEST_USER_ID = 0L;

    private final LabSessionRepository sessionRepository;
    private final CorrectionLogRepository correctionLogRepository;
    private final EnvCheckLogRepository envCheckLogRepository;
    private final ExperimentConfigService experimentConfigService;
    private final DifyService difyService;
    private final DifyRetrieveService difyRetrieveService;
    private final SessionDataLogRepository sessionDataLogRepository;
    private final DataValidationService dataValidationService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public LabSessionService(LabSessionRepository sessionRepository,
                             CorrectionLogRepository correctionLogRepository,
                             EnvCheckLogRepository envCheckLogRepository,
                             SessionDataLogRepository sessionDataLogRepository,
                             ExperimentConfigService experimentConfigService,
                             DifyService difyService,
                             DifyRetrieveService difyRetrieveService,
                             DataValidationService dataValidationService,
                             ObjectMapper objectMapper,
                             PlatformTransactionManager transactionManager) {
        this.sessionRepository = sessionRepository;
        this.correctionLogRepository = correctionLogRepository;
        this.envCheckLogRepository = envCheckLogRepository;
        this.sessionDataLogRepository = sessionDataLogRepository;
        this.experimentConfigService = experimentConfigService;
        this.difyService = difyService;
        this.difyRetrieveService = difyRetrieveService;
        this.dataValidationService = dataValidationService;
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

    public Map<String, Object> getSessionData(Long sessionId) {
        getSession(sessionId);
        List<SessionDataLog> logs = sessionDataLogRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        Map<String, Object> byStep = new LinkedHashMap<>();
        for (SessionDataLog log : logs) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("stepId", log.getStepId());
            entry.put("stepTitle", log.getStepTitle());
            entry.put("values", readJsonMap(log.getValuesJson()));
            entry.put("validation", readJsonMap(log.getValidationJson()));
            entry.put("feedback", log.getFeedback());
            entry.put("createdAt", log.getCreatedAt() != null ? log.getCreatedAt().toString() : "");
            byStep.put(String.valueOf(log.getStepId()), entry);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("byStep", byStep);
        return body;
    }

    @Transactional
    public SessionDataSubmitResponse submitSessionData(Long sessionId, SubmitSessionDataRequest req) {
        LabSession session = getSession(sessionId);
        ExperimentConfig exp = experimentConfigService.getByCode(session.getExperimentCode());
        int stepId = req.getStepId() != null ? req.getStepId() : session.getActiveStep();
        StepConfig step = resolveStep(exp, stepId);

        if (!isDataStep(exp, step)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前步骤请使用拍照纠错，不支持数据提交");
        }

        Map<String, Object> values = req.getValues() != null ? new LinkedHashMap<>(req.getValues()) : Map.of();
        DataValidationResult validation = dataValidationService.validate(exp, step, values);

        Map<String, Object> inputs = new LinkedHashMap<>();
        String dataJson = writeJson(values);
        inputs.put("query", buildDataAssistQuery(step, validation, values));
        inputs.put("data_json", dataJson);
        inputs.put("correction_mode", "data");
        putExperimentInputs(inputs, exp.getName(), exp.getCode());
        if (exp.getCategory() != null && !exp.getCategory().isBlank()) {
            inputs.put("category", exp.getCategory());
        }
        inputs.put("step_id", stepId);
        if (step.getTitle() != null) {
            inputs.put("step_title", step.getTitle());
        }
        if (step.getDesc() != null) {
            inputs.put("step_desc", step.getDesc());
        }
        attachKnowledgeContext(inputs, exp, session, step, inputs.get("query").toString(), false);

        AssistResponse assist = difyService.assist("text-assist", inputs, "guest-" + sessionId, exp, stepId, false, null, true);
        String feedback = prependValidationSummary(assist.getFeedback(), validation);
        assist.setFeedback(feedback);

        SessionDataLog log = new SessionDataLog();
        log.setSessionId(sessionId);
        log.setStepId(stepId);
        log.setStepTitle(step != null && step.getTitle() != null ? step.getTitle() : "");
        log.setValuesJson(dataJson);
        log.setValidationJson(writeJson(validation));
        log.setFeedback(feedback);
        sessionDataLogRepository.save(log);

        session.setHelpCount(session.getHelpCount() + 1);
        sessionRepository.save(session);

        if ("data_correction".equals(assist.getType()) || !validation.isOk() || !validation.getWarnings().isEmpty()) {
            CorrectionLog correction = new CorrectionLog();
            correction.setSessionId(sessionId);
            correction.setStepId(stepId);
            correction.setStepTitle(log.getStepTitle());
            correction.setErrorType(assist.getErrorType() != null ? assist.getErrorType() : "实验数据");
            correction.setDetail(assist.getDetail() != null ? assist.getDetail() : dataJson);
            correction.setFeedback(feedback);
            correctionLogRepository.save(correction);
        }

        SessionDataSubmitResponse resp = new SessionDataSubmitResponse();
        resp.setStepId(stepId);
        resp.setValues(values);
        resp.setValidation(validation);
        resp.setAssist(assist);
        return resp;
    }

    public static boolean isDataStep(ExperimentConfig exp, StepConfig step) {
        if (exp.getDataCollection() == null || !exp.getDataCollection().isEnabled()) {
            return false;
        }
        if (step == null) {
            return false;
        }
        if ("vision".equalsIgnoreCase(step.getCorrectionMode())) {
            return false;
        }
        if ("data".equalsIgnoreCase(step.getCorrectionMode())) {
            return true;
        }
        return step.getDataFields() != null && !step.getDataFields().isEmpty();
    }

    private String buildDataAssistQuery(StepConfig step, DataValidationResult validation, Map<String, Object> values) {
        StringBuilder q = new StringBuilder("请根据本步骤实验测量数据，检查操作与计算是否合理，并给出纠错建议。");
        if (step != null && step.getTitle() != null) {
            q.append("\n步骤：").append(step.getTitle());
        }
        if (!validation.getErrors().isEmpty()) {
            q.append("\n系统预检发现：").append(String.join("；", validation.getErrors()));
        }
        if (!validation.getWarnings().isEmpty()) {
            q.append("\n系统提示：").append(String.join("；", validation.getWarnings()));
        }
        q.append("\n数据：").append(writeJson(values));
        return q.toString();
    }

    private String prependValidationSummary(String feedback, DataValidationResult validation) {
        StringBuilder sb = new StringBuilder();
        if (!validation.getErrors().isEmpty()) {
            sb.append("**数据预检**\n");
            for (String err : validation.getErrors()) {
                sb.append("- ").append(err).append('\n');
            }
            sb.append('\n');
        }
        if (!validation.getWarnings().isEmpty()) {
            sb.append("**提示**\n");
            for (String w : validation.getWarnings()) {
                sb.append("- ").append(w).append('\n');
            }
            sb.append('\n');
        }
        if (feedback != null && !feedback.isBlank()) {
            sb.append(feedback);
        }
        return sb.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
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
                        prepare.hasImage() ? req.getImageUrl() : null,
                        delta -> sendChunk(emitter, delta),
                        marks -> sendMarks(emitter, marks));
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

    private void sendMarks(SseEmitter emitter, List<MarkDto> marks) {
        if (marks == null || marks.isEmpty()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name("marks").data(new AssistStreamMarks(marks)));
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

        log.info("assist prepare sessionId={}, hasImage={}, imageUrl={}", sessionId, hasImage, req.getImageUrl());

        ExperimentConfig exp = experimentConfigService.getByCode(session.getExperimentCode());
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("query", userMessage);
        putExperimentInputs(inputs, exp.getName(), exp.getCode());
        if (exp.getCategory() != null && !exp.getCategory().isBlank()) {
            inputs.put("category", exp.getCategory());
        }

        StepConfig step = resolveStep(exp, session.getActiveStep());
        if (step != null) {
            inputs.put("step_id", session.getActiveStep());
            if (step.getTitle() != null) {
                inputs.put("step_title", step.getTitle());
            }
            if (step.getDesc() != null) {
                inputs.put("step_desc", step.getDesc());
            }
        }

        attachKnowledgeContext(inputs, exp, session, step, userMessage, hasImage);

        return new AssistPrepare(session, exp, userMessage, hasImage, inputs);
    }

    /** 纯文字与带图均检索当前实验知识库（若已配置 datasetId） */
    private void attachKnowledgeContext(Map<String, Object> inputs, ExperimentConfig exp, LabSession session,
                                        StepConfig step, String userMessage, boolean hasImage) {
        ExperimentDifyConfig dify = exp.getDify();
        if (dify == null || dify.getDatasetId() == null || dify.getDatasetId().isBlank()) {
            return;
        }
        String datasetId = dify.getDatasetId().trim();
        String retrievalQuery = buildRetrievalQuery(exp, step, userMessage, hasImage);
        String kbContext = difyRetrieveService.retrieve(datasetId, retrievalQuery);

        inputs.put("dataset_id", datasetId);
        inputs.put("kb_context", kbContext);
        log.info("KB retrieve sessionId={}, experiment={}, hasImage={}, retrievalQueryLen={}, kbContextLen={}",
                session.getId(), exp.getCode(), hasImage, retrievalQuery.length(), kbContext.length());
    }

    private String buildRetrievalQuery(ExperimentConfig exp, StepConfig step, String userMessage, boolean hasImage) {
        StringBuilder q = new StringBuilder();
        if (exp.getName() != null) {
            q.append(exp.getName());
        }
        if (step != null && step.getTitle() != null && !step.getTitle().isBlank()) {
            q.append(' ').append(step.getTitle());
        }
        if (userMessage != null && !userMessage.isBlank()) {
            q.append(' ').append(userMessage);
        }
        if (hasImage) {
            q.append(" 实验装置 仪器接线 操作规范 常见错误 读数方法");
        }
        return q.toString().trim();
    }

    private StepConfig resolveStep(ExperimentConfig exp, int stepId) {
        if (exp.getSteps() == null) {
            return null;
        }
        return exp.getSteps().get(String.valueOf(stepId));
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
        List<SessionDataLog> dataLogs = sessionDataLogRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);

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
        report.put("dataLogs", dataLogs);
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
