package com.wuxiaozhi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiaozhi.dto.*;
import com.wuxiaozhi.dto.experiment.DataFieldConfig;
import com.wuxiaozhi.dto.experiment.ExperimentConfig;
import com.wuxiaozhi.dto.experiment.ExperimentDifyConfig;
import com.wuxiaozhi.dto.experiment.MarkDto;
import com.wuxiaozhi.dto.experiment.StepConfig;
import com.wuxiaozhi.entity.ChatMessage;
import com.wuxiaozhi.entity.CorrectionLog;
import com.wuxiaozhi.entity.EnvCheckLog;
import com.wuxiaozhi.entity.LabSession;
import com.wuxiaozhi.entity.SessionDataLog;
import com.wuxiaozhi.repository.ChatMessageRepository;
import com.wuxiaozhi.repository.CorrectionLogRepository;
import com.wuxiaozhi.repository.EnvCheckLogRepository;
import com.wuxiaozhi.repository.LabSessionRepository;
import com.wuxiaozhi.repository.MessageFeedbackRepository;
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
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class LabSessionService {

    private static final Logger log = LoggerFactory.getLogger(LabSessionService.class);

    private static final long GUEST_USER_ID = 0L;

    private final LabSessionRepository sessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final CorrectionLogRepository correctionLogRepository;
    private final EnvCheckLogRepository envCheckLogRepository;
    private final ExperimentConfigService experimentConfigService;
    private final DifyService difyService;
    private final DifyRetrieveService difyRetrieveService;
    private final KnowledgeMapService knowledgeMapService;
    private final SessionDataLogRepository sessionDataLogRepository;
    private final DataValidationService dataValidationService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final MessageFeedbackRepository messageFeedbackRepository;
    private final StudentExperimentAccessService accessService;

    public LabSessionService(LabSessionRepository sessionRepository,
                             ChatMessageRepository chatMessageRepository,
                             CorrectionLogRepository correctionLogRepository,
                             EnvCheckLogRepository envCheckLogRepository,
                             SessionDataLogRepository sessionDataLogRepository,
                             ExperimentConfigService experimentConfigService,
                             DifyService difyService,
                             DifyRetrieveService difyRetrieveService,
                             KnowledgeMapService knowledgeMapService,
                             DataValidationService dataValidationService,
                             ObjectMapper objectMapper,
                             PlatformTransactionManager transactionManager,
                             MessageFeedbackRepository messageFeedbackRepository,
                             StudentExperimentAccessService accessService) {
        this.sessionRepository = sessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.correctionLogRepository = correctionLogRepository;
        this.envCheckLogRepository = envCheckLogRepository;
        this.sessionDataLogRepository = sessionDataLogRepository;
        this.experimentConfigService = experimentConfigService;
        this.difyService = difyService;
        this.difyRetrieveService = difyRetrieveService;
        this.knowledgeMapService = knowledgeMapService;
        this.dataValidationService = dataValidationService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.messageFeedbackRepository = messageFeedbackRepository;
        this.accessService = accessService;
    }

    @Transactional
    public LabSession startSession(StartSessionRequest req) {
        return startSession(req, GUEST_USER_ID);
    }

    @Transactional
    public LabSession startSession(StartSessionRequest req, Long userId) {
        if (userId != null && userId > GUEST_USER_ID) {
            accessService.requireAssignedIfStudent(userId, req.getExperimentCode());
        }
        ExperimentConfig exp = experimentConfigService.getByCode(req.getExperimentCode());
        LabSession session = new LabSession();
        session.setUserId(userId != null ? userId : GUEST_USER_ID);
        session.setExperimentCode(exp.getCode());
        session.setExperimentName(exp.getName());
        session.setStudentName(req.getStudentName().trim());
        session.setStudentClass(req.getStudentClass() != null ? req.getStudentClass().trim() : "");
        session.setActiveStep(1);
        session.setStatus("ACTIVE");
        return sessionRepository.save(session);
    }

    public List<LabSession> listSessions(Long userId, String experimentCode) {
        return listSessions(userId, experimentCode, false);
    }

    public List<LabSession> listSessions(Long userId, String experimentCode, boolean includeEmpty) {
        List<LabSession> sessions;
        if (experimentCode != null && !experimentCode.isBlank()) {
            sessions = includeEmpty
                    ? sessionRepository.findByUserIdAndExperimentCodeOrderByStartTimeDesc(userId, experimentCode.trim())
                    : sessionRepository.findConversationSessionsByUserIdAndExperimentCode(userId, experimentCode.trim());
        } else {
            sessions = includeEmpty
                    ? sessionRepository.findByUserIdOrderByStartTimeDesc(userId)
                    : sessionRepository.findConversationSessionsByUserId(userId);
        }
        sessions.forEach(this::attachHistoryTitle);
        return sessions;
    }

    public LabSession getLatestActiveSession(Long userId, String experimentCode) {
        if (experimentCode == null || experimentCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "experimentCode is required");
        }
        return sessionRepository
                .findFirstByUserIdAndExperimentCodeAndStatusOrderByStartTimeDesc(userId, experimentCode.trim(), "ACTIVE")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No active session"));
    }

    /**
     * 恢复实验会话：优先 ACTIVE；否则取该实验下最近一条有问答或数据的会话。
     * 避免刷新/重启后端后误开新会话导致「我的数据」与问答记录看似丢失。
     */
    public LabSession getResumeSession(Long userId, String experimentCode) {
        if (experimentCode == null || experimentCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "experimentCode is required");
        }
        String code = experimentCode.trim();
        Optional<LabSession> active = sessionRepository
                .findFirstByUserIdAndExperimentCodeAndStatusOrderByStartTimeDesc(userId, code, "ACTIVE");
        if (active.isPresent()) {
            return active.get();
        }
        List<LabSession> recent = sessionRepository.findConversationSessionsByUserIdAndExperimentCode(userId, code);
        if (!recent.isEmpty()) {
            return recent.get(0);
        }
        return sessionRepository.findFirstByUserIdAndExperimentCodeOrderByStartTimeDesc(userId, code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No session"));
    }

    public LabSession getSession(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在"));
    }

    public LabSession getSession(Long sessionId, Long userId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在"));
    }

    @Transactional
    public LabSession updateStep(Long sessionId, int stepId) {
        LabSession session = getSession(sessionId);
        session.setActiveStep(stepId);
        return sessionRepository.save(session);
    }

    @Transactional
    public LabSession updateStep(Long sessionId, Long userId, int stepId) {
        LabSession session = getSession(sessionId, userId);
        session.setActiveStep(stepId);
        return sessionRepository.save(session);
    }

    public static final long CAMERA_ACTIVE_TTL_SECONDS = 300;

    public boolean isCameraActiveEffective(LabSession session) {
        if (session == null || !session.isCameraActive()) {
            return false;
        }
        LocalDateTime at = session.getCameraActiveAt();
        if (at == null) {
            return false;
        }
        return java.time.Duration.between(at, LocalDateTime.now()).getSeconds() <= CAMERA_ACTIVE_TTL_SECONDS;
    }

    @Transactional
    public void updateCameraStatus(Long sessionId, Long userId, boolean active) {
        LabSession session = getSession(sessionId, userId);
        applyCameraStatus(session, active);
        sessionRepository.save(session);
    }

    private void applyCameraStatus(LabSession session, boolean active) {
        if ("FINISHED".equals(session.getStatus())) {
            session.setCameraActive(false);
            session.setCameraActiveAt(null);
            return;
        }
        session.setCameraActive(active);
        session.setCameraActiveAt(active ? LocalDateTime.now() : null);
    }

    public Map<String, Object> getSessionData(Long sessionId) {
        return getSessionDataBody(getSession(sessionId));
    }

    public Map<String, Object> getSessionData(Long sessionId, Long userId) {
        return getSessionDataBody(getSession(sessionId, userId));
    }

    public List<ChatMessage> getMessages(Long sessionId, Long userId) {
        getSession(sessionId, userId);
        List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        Map<Long, String> ratings = messageFeedbackRepository.findByUserIdAndSessionIdOrderByCreatedAtDesc(userId, sessionId)
                .stream()
                .collect(Collectors.toMap(com.wuxiaozhi.entity.MessageFeedback::getMessageId,
                        com.wuxiaozhi.entity.MessageFeedback::getRating, (a, b) -> b));
        for (ChatMessage message : messages) {
            if ("ai".equalsIgnoreCase(message.getRole())) {
                message.setFeedbackRating(ratings.get(message.getId()));
            }
        }
        return messages;
    }

    @Transactional
    public ChatMessage attachLatestAiMessageImage(Long sessionId, Long userId, String imageUrl) {
        getSession(sessionId, userId);
        if (imageUrl == null || imageUrl.isBlank() || !imageUrl.startsWith("/uploads/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效的图片地址");
        }
        ChatMessage message = chatMessageRepository
                .findFirstBySessionIdAndRoleOrderByCreatedAtDesc(sessionId, "ai")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到 AI 回复"));
        message.setImageUrl(imageUrl.split("\\?")[0]);
        return chatMessageRepository.save(message);
    }

    private void attachHistoryTitle(LabSession session) {
        List<ChatMessage> firstQuestions = chatMessageRepository.findTop3BySessionIdAndRoleOrderByCreatedAtAsc(session.getId(), "user");
        session.setHistoryTitle(buildHistoryTitle(firstQuestions));
    }

    private String buildHistoryTitle(List<ChatMessage> questions) {
        if (questions == null || questions.isEmpty()) {
            return "";
        }
        List<String> parts = questions.stream()
                .map(ChatMessage::getText)
                .map(this::compactQuestion)
                .filter(s -> !s.isBlank())
                .distinct()
                .limit(3)
                .toList();
        if (parts.isEmpty()) {
            return "";
        }
        return ellipsis(String.join("、", parts), 24);
    }

    private String compactQuestion(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String plain = text
                .replaceAll("<[^>]+>", " ")
                .replaceAll("[#*_>`\\[\\]()]", "")
                .replaceAll("https?://\\S+", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (plain.startsWith("【数据提交】")) {
            return "数据提交纠错";
        }
        if ("请分析上传的实验图片。".equals(plain) || "请分析上传的实验图片".equals(plain)) {
            return "实验图片分析";
        }
        plain = plain.replaceFirst("^(请问|老师|物小智|我想问一下|想问一下|帮我看看|请帮我|请|帮我)", "").trim();
        int splitAt = firstPositive(
                plain.indexOf('？'),
                plain.indexOf('?'),
                plain.indexOf('。'),
                plain.indexOf('；'),
                plain.indexOf(';'),
                plain.indexOf('\n')
        );
        if (splitAt >= 0) {
            plain = plain.substring(0, splitAt).trim();
        }
        return ellipsis(plain, 12);
    }

    private int firstPositive(int... values) {
        int best = -1;
        for (int value : values) {
            if (value >= 0 && (best < 0 || value < best)) {
                best = value;
            }
        }
        return best;
    }

    private String ellipsis(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        String normalized = text.trim();
        if (normalized.length() <= maxLen) {
            return normalized;
        }
        return normalized.substring(0, maxLen - 1) + "…";
    }

    private Map<String, Object> getSessionDataBody(LabSession session) {
        Long sessionId = session.getId();
        ExperimentConfig exp = experimentConfigService.getByCode(session.getExperimentCode());
        Map<String, Object> stepSchemas = buildStepDataSchemas(exp);
        List<SessionDataLog> logs = sessionDataLogRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        List<Map<String, Object>> logEntries = new ArrayList<>();
        Map<String, Object> byStep = new LinkedHashMap<>();
        for (SessionDataLog log : logs) {
            Map<String, Object> row = buildSessionDataRow(log);
            logEntries.add(row);

            String stepKey = String.valueOf(log.getStepId());
            @SuppressWarnings("unchecked")
            Map<String, Object> stepBucket = (Map<String, Object>) byStep.get(stepKey);
            if (stepBucket == null) {
                stepBucket = new LinkedHashMap<>();
                stepBucket.put("stepId", log.getStepId());
                stepBucket.put("stepTitle", log.getStepTitle() != null ? log.getStepTitle() : "");
                stepBucket.put("rows", new ArrayList<Map<String, Object>>());
                attachStepFields(stepBucket, stepSchemas.get(stepKey));
                byStep.put(stepKey, stepBucket);
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) stepBucket.get("rows");
            rows.add(row);
            stepBucket.put("values", row.get("values"));
            stepBucket.put("validation", row.get("validation"));
            stepBucket.put("feedback", row.get("feedback"));
            stepBucket.put("createdAt", row.get("createdAt"));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("experimentCode", session.getExperimentCode());
        body.put("experimentName", session.getExperimentName());
        body.put("stepSchemas", stepSchemas);
        body.put("logs", logEntries);
        body.put("byStep", byStep);
        body.put("totalCount", logs.size());
        return body;
    }

    private Map<String, Object> buildStepDataSchemas(ExperimentConfig exp) {
        Map<String, Object> schemas = new LinkedHashMap<>();
        if (exp == null || exp.getSteps() == null || exp.getSteps().isEmpty()) {
            return schemas;
        }
        exp.getSteps().entrySet().stream()
                .sorted(Comparator.comparingInt(e -> parseStepId(e.getKey())))
                .forEach(entry -> {
                    StepConfig step = entry.getValue();
                    if (!isDataStep(exp, step)) {
                        return;
                    }
                    Map<String, Object> schema = new LinkedHashMap<>();
                    schema.put("stepId", parseStepId(entry.getKey()));
                    schema.put("stepTitle", step.getTitle() != null ? step.getTitle() : ("步骤 " + entry.getKey()));
                    schema.put("fields", buildDataFieldColumns(step.getDataFields()));
                    schemas.put(entry.getKey(), schema);
                });
        return schemas;
    }

    private List<Map<String, Object>> buildDataFieldColumns(List<DataFieldConfig> dataFields) {
        List<Map<String, Object>> fields = new ArrayList<>();
        if (dataFields == null) {
            return fields;
        }
        for (DataFieldConfig field : dataFields) {
            if (field == null || field.getKey() == null || field.getKey().isBlank()) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", field.getKey());
            item.put("label", buildDataFieldLabel(field));
            item.put("type", field.getType() != null ? field.getType() : "number");
            if (field.getUnit() != null && !field.getUnit().isBlank()) {
                item.put("unit", field.getUnit());
            }
            fields.add(item);
        }
        return fields;
    }

    private String buildDataFieldLabel(DataFieldConfig field) {
        String label = field.getLabel() != null && !field.getLabel().isBlank() ? field.getLabel() : field.getKey();
        String unit = field.getUnit();
        if (unit == null || unit.isBlank() || label.contains(unit)) {
            return label;
        }
        return label + " (" + unit + ")";
    }

    @SuppressWarnings("unchecked")
    private void attachStepFields(Map<String, Object> stepBucket, Object schemaObj) {
        if (!(schemaObj instanceof Map<?, ?> schemaMap)) {
            return;
        }
        Object fields = schemaMap.get("fields");
        if (fields instanceof List<?> list && !list.isEmpty()) {
            stepBucket.put("fields", list);
        }
    }

    private Map<String, Object> buildSessionDataRow(SessionDataLog log) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", log.getId());
        entry.put("stepId", log.getStepId());
        entry.put("stepTitle", log.getStepTitle() != null ? log.getStepTitle() : "");
        entry.put("values", readJsonMap(log.getValuesJson()));
        entry.put("validation", readJsonMap(log.getValidationJson()));
        entry.put("feedback", log.getFeedback());
        entry.put("createdAt", log.getCreatedAt() != null ? log.getCreatedAt().toString() : "");
        return entry;
    }

    @Transactional
    public SessionDataSubmitResponse submitSessionData(Long sessionId, SubmitSessionDataRequest req) {
        LabSession session = getSession(sessionId);
        return submitSessionData(session, req);
    }

    @Transactional
    public SessionDataSubmitResponse submitSessionData(Long sessionId, Long userId, SubmitSessionDataRequest req) {
        LabSession session = getSession(sessionId, userId);
        return submitSessionData(session, req);
    }

    private SessionDataSubmitResponse submitSessionData(LabSession session, SubmitSessionDataRequest req) {
        Long sessionId = session.getId();
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
        putExperimentInputs(inputs, exp.getName(), exp.getCode());
        inputs.put("step_id", String.valueOf(stepId));
        putStepContextInputs(inputs, step);
        putDifyRoutingInputs(inputs, step, false);
        attachKnowledgeMapInputs(inputs, exp, stepId, false);
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

        String submittedText = "【数据提交】" + log.getStepTitle() + "\n" + values.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining("；"));
        saveChatMessage(sessionId, "user", stepId, submittedText, null);
        saveChatMessage(sessionId, "ai", stepId, feedback, null);

        if (!validation.isOk() || !validation.getWarnings().isEmpty()) {
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

    @Transactional
    public AssistResponse assist(Long sessionId, Long userId, AssistRequest req) {
        AssistPrepare prepare = prepareAssist(sessionId, userId, req);
        AssistResponse resp = difyService.assist("text-assist", prepare.inputs(), "user-" + userId, prepare.experiment(),
                prepare.session().getActiveStep(), prepare.hasImage(), prepare.hasImage() ? req.getImageUrl() : null);
        persistAssistResult(sessionId, req, prepare, resp);
        return resp;
    }

    public SseEmitter assistStream(Long sessionId, AssistRequest req) {
        return assistStream(sessionId, null, req);
    }

    public SseEmitter assistStream(Long sessionId, Long userId, AssistRequest req) {
        SseEmitter emitter = new SseEmitter(300_000L);
        emitter.onTimeout(emitter::complete);

        AssistPrepare prepare;
        try {
            prepare = userId != null ? prepareAssist(sessionId, userId, req) : prepareAssist(sessionId, req);
            emitter.send(SseEmitter.event().name("start").data(Map.of("ok", true)));
        } catch (Exception e) {
            sendStreamError(emitter, e);
            return emitter;
        }

        CompletableFuture.runAsync(() -> {
            try {
                AssistResponse resp = difyService.streamAssist("text-assist", prepare.inputs(),
                        userId != null ? "user-" + userId : "guest-" + sessionId,
                        prepare.experiment(), prepare.session().getActiveStep(), prepare.hasImage(),
                        prepare.hasImage() ? req.getImageUrl() : null,
                        delta -> sendChunk(emitter, delta),
                        marks -> sendMarks(emitter, marks),
                        () -> sendAnswerEnd(emitter));
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

    private void sendAnswerEnd(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("answer_end").data(Map.of("ok", true)));
        } catch (IOException ignored) {
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
        return prepareAssist(session, req);
    }

    private AssistPrepare prepareAssist(Long sessionId, Long userId, AssistRequest req) {
        LabSession session = getSession(sessionId, userId);
        return prepareAssist(session, req);
    }

    private AssistPrepare prepareAssist(LabSession session, AssistRequest req) {
        Long sessionId = session.getId();

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

        int stepId = resolveAssistStepId(session, req);
        if (session.getActiveStep() != stepId) {
            session.setActiveStep(stepId);
            sessionRepository.save(session);
        }

        StepConfig step = resolveStep(exp, stepId);
        inputs.put("step_id", String.valueOf(stepId));
        putStepContextInputs(inputs, step);
        putDifyRoutingInputs(inputs, step, hasImage);
        attachKnowledgeMapInputs(inputs, exp, stepId, hasImage);
        attachRepeatAssistHint(inputs, session.getId(), stepId);

        attachKnowledgeContext(inputs, exp, session, step, userMessage, hasImage);

        return new AssistPrepare(session, exp, userMessage, hasImage, inputs, stepId);
    }

    private int resolveAssistStepId(LabSession session, AssistRequest req) {
        if (req.getStepId() != null && req.getStepId() > 0) {
            return req.getStepId();
        }
        return Math.max(1, session.getActiveStep());
    }

    /** 同一步骤多次纠错时，提示模型给更短、可执行的下一步 */
    private void attachRepeatAssistHint(Map<String, Object> inputs, Long sessionId, int stepId) {
        List<CorrectionLog> logs = correctionLogRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        if (logs == null || logs.isEmpty()) {
            return;
        }
        long recentOnStep = logs.stream()
                .filter(log -> log.getStepId() == stepId)
                .count();
        if (recentOnStep >= 2) {
            inputs.put("repeat_hint",
                    "学生在本步骤已多次求助/纠错。请只给一条最短可执行指令，并明确要先完成什么再继续提问。");
        }
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

    private void attachKnowledgeMapInputs(Map<String, Object> inputs, ExperimentConfig exp, int stepId, boolean correctionMode) {
        if (knowledgeMapService == null || exp == null || exp.getCode() == null) {
            return;
        }
        knowledgeMapService.enrichInputs(inputs, exp.getCode(), String.valueOf(stepId), correctionMode);
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

        int stepId = prepare.stepId() > 0 ? prepare.stepId() : Math.max(1, session.getActiveStep());
        StepConfig step = resolveStep(prepare.experiment(), stepId);
        String stepTitle = step != null && step.getTitle() != null && !step.getTitle().isBlank()
                ? step.getTitle()
                : ("步骤 " + stepId);

        if ("vision_correction".equals(resp.getType()) || (prepare.hasImage() && resp.getMarks() != null && !resp.getMarks().isEmpty())) {
            CorrectionLog log = new CorrectionLog();
            log.setSessionId(sessionId);
            log.setStepId(stepId);
            log.setStepTitle(stepTitle);
            log.setErrorType(resp.getErrorType() != null ? resp.getErrorType() : "vision");
            log.setDetail(resp.getDetail() != null ? resp.getDetail() : resp.getFeedback());
            log.setFeedback(resp.getFeedback());
            log.setImageUrl(req.getImageUrl());
            try {
                log.setMarksJson(objectMapper.writeValueAsString(resp.getMarks()));
            } catch (Exception ignored) {
            }
            correctionLogRepository.save(log);
        }

        ChatMessage userMessage = saveChatMessage(sessionId, "user", stepId, prepare.userMessage(), req.getImageUrl());
        ChatMessage aiMessage = saveChatMessage(sessionId, "ai", stepId, resp.getFeedback(), null);
        resp.setUserMessageId(userMessage.getId());
        resp.setAiMessageId(aiMessage.getId());
    }

    private ChatMessage saveChatMessage(Long sessionId, String role, int stepId, String text, String imageUrl) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setStepId(stepId);
        message.setText(text != null ? text : "");
        message.setImageUrl(imageUrl != null ? imageUrl : "");
        return chatMessageRepository.save(message);
    }

    private record AssistPrepare(LabSession session, ExperimentConfig experiment, String userMessage,
                                 boolean hasImage, Map<String, Object> inputs, int stepId) {
    }

    @Transactional
    public EnvCheckResponse envCheck(Long sessionId, EnvCheckRequest req) {
        LabSession session = getSession(sessionId);
        return envCheck(session, req, "guest-" + sessionId);
    }

    @Transactional
    public EnvCheckResponse envCheck(Long sessionId, Long userId, EnvCheckRequest req) {
        LabSession session = getSession(sessionId, userId);
        return envCheck(session, req, "user-" + userId);
    }

    private EnvCheckResponse envCheck(LabSession session, EnvCheckRequest req, String difyUser) {
        Long sessionId = session.getId();
        Map<String, Object> inputs = new LinkedHashMap<>();
        if (!"general".equals(session.getExperimentCode())) {
            putExperimentInputs(inputs, session.getExperimentName(), session.getExperimentCode());
        }
        String snapshotUrl = req != null && req.getSnapshotUrl() != null ? req.getSnapshotUrl().trim() : "";
        if (!snapshotUrl.isBlank()) {
            inputs.put("snapshot_url", snapshotUrl);
        }

        EnvCheckResponse resp = difyService.envCheck(inputs, difyUser, snapshotUrl);

        EnvCheckLog log = new EnvCheckLog();
        log.setSessionId(sessionId);
        log.setLevel(resp.getLevel());
        log.setSummary(resp.getSummary());
        log.setSuggestion(resp.getSuggestion());
        if (!snapshotUrl.isBlank()) {
            log.setSnapshotUrl(snapshotUrl);
            resp.setSnapshotUrl(snapshotUrl);
        }
        envCheckLogRepository.save(log);

        if (resp.isFromDify() && "L2".equals(resp.getLevel())) {
            session.setLabL3Count(session.getLabL3Count() + 1);
            sessionRepository.save(session);
        }
        return resp;
    }

    @Transactional(readOnly = true)
    public List<EnvCheckLog> getEnvCheckLogs(Long sessionId) {
        getSession(sessionId);
        return envCheckLogRepository.findBySessionIdOrderByCreatedAtDesc(sessionId);
    }

    @Transactional
    public LabSession incrementTutView(Long sessionId) {
        LabSession session = getSession(sessionId);
        return incrementTutView(session);
    }

    @Transactional
    public LabSession incrementTutView(Long sessionId, Long userId) {
        LabSession session = getSession(sessionId, userId);
        return incrementTutView(session);
    }

    private LabSession incrementTutView(LabSession session) {
        session.setTutViewCount(session.getTutViewCount() + 1);
        return sessionRepository.save(session);
    }

    @Transactional
    public LabSession finishSession(Long sessionId) {
        LabSession session = getSession(sessionId);
        return finishSession(session);
    }

    @Transactional
    public LabSession finishSession(Long sessionId, Long userId) {
        LabSession session = getSession(sessionId, userId);
        return finishSession(session);
    }

    private LabSession finishSession(LabSession session) {
        session.setStatus("FINISHED");
        session.setEndTime(LocalDateTime.now());
        session.setCameraActive(false);
        session.setCameraActiveAt(null);
        return sessionRepository.save(session);
    }

    public Map<String, Object> buildReportData(Long sessionId) {
        LabSession session = getSession(sessionId);
        return buildReportData(session);
    }

    public Map<String, Object> buildReportData(Long sessionId, Long userId) {
        LabSession session = getSession(sessionId, userId);
        return buildReportData(session);
    }

    private Map<String, Object> buildReportData(LabSession session) {
        Long sessionId = session.getId();
        ExperimentConfig exp = experimentConfigService.getByCode(session.getExperimentCode());
        List<CorrectionLog> corrections = correctionLogRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        List<EnvCheckLog> envLogs = envCheckLogRepository.findBySessionIdOrderByCreatedAtDesc(sessionId);
        List<SessionDataLog> dataLogs = sessionDataLogRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("sessionId", sessionId);
        report.put("experimentCode", session.getExperimentCode());
        report.put("experimentName", session.getExperimentName());
        report.put("studentName", session.getStudentName());
        report.put("studentClass", session.getStudentClass());
        report.put("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss")));
        report.put("helpCount", session.getHelpCount());
        report.put("errorPointCount", session.getErrorPointCount());
        report.put("tutViewCount", session.getTutViewCount());
        report.put("labL3Count", session.getLabL3Count());
        report.put("reportKnowledge", exp.getReportKnowledge());
        report.put("reportPath", exp.getReportPath());
        report.put("stepSummaries", buildStepSummaries(exp));
        report.put("stepSchemas", buildStepDataSchemas(exp));
        report.put("dataLogEntries", buildDataLogEntries(dataLogs));
        report.put("corrections", corrections);
        report.put("dataLogs", dataLogs);
        report.put("envLogs", envLogs);
        return report;
    }

    private List<Map<String, Object>> buildStepSummaries(ExperimentConfig exp) {
        List<Map<String, Object>> summaries = new ArrayList<>();
        if (exp.getSteps() == null || exp.getSteps().isEmpty()) {
            return summaries;
        }
        exp.getSteps().entrySet().stream()
                .sorted(Comparator.comparingInt(e -> parseStepId(e.getKey())))
                .forEach(entry -> {
                    StepConfig step = entry.getValue();
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("stepNo", entry.getKey());
                    item.put("title", step.getTitle() != null ? step.getTitle() : ("步骤 " + entry.getKey()));
                    item.put("desc", step.getDesc() != null ? step.getDesc() : "");
                    summaries.add(item);
                });
        return summaries;
    }

    private List<Map<String, Object>> buildDataLogEntries(List<SessionDataLog> dataLogs) {
        List<Map<String, Object>> entries = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (SessionDataLog log : dataLogs) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("stepId", log.getStepId());
            item.put("stepTitle", log.getStepTitle());
            item.put("submittedAt", log.getCreatedAt() != null ? log.getCreatedAt().format(fmt) : "");
            item.put("values", parseValuesJson(log.getValuesJson()));
            item.put("valuesSummary", summarizeValuesJson(log.getValuesJson()));
            item.put("validationSummary", summarizeValidationJson(log.getValidationJson()));
            entries.add(item);
        }
        return entries;
    }

    private Map<String, Object> parseValuesJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> values = objectMapper.readValue(json, new TypeReference<>() {});
            return values != null ? values : Map.of();
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String summarizeValuesJson(String json) {
        if (json == null || json.isBlank()) {
            return "—";
        }
        try {
            Map<String, Object> values = objectMapper.readValue(json, new TypeReference<>() {});
            if (values.isEmpty()) {
                return "—";
            }
            return values.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("，"));
        } catch (Exception e) {
            return json;
        }
    }

    private String summarizeValidationJson(String json) {
        if (json == null || json.isBlank()) {
            return "—";
        }
        try {
            Map<String, Object> validation = objectMapper.readValue(json, new TypeReference<>() {});
            Object ok = validation.get("ok");
            if (Boolean.TRUE.equals(ok)) {
                return "通过";
            }
            Object errors = validation.get("errors");
            if (errors instanceof List<?> list && !list.isEmpty()) {
                return list.stream().map(String::valueOf).collect(Collectors.joining("；"));
            }
            return "未通过";
        } catch (Exception e) {
            return "—";
        }
    }

    private int parseStepId(String key) {
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException e) {
            return 0;
        }
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

    private void putStepContextInputs(Map<String, Object> inputs, StepConfig step) {
        if (step == null) {
            return;
        }
        if (step.getTitle() != null && !step.getTitle().isBlank()) {
            inputs.put("step_title", step.getTitle());
        }
        if (step.getDesc() != null && !step.getDesc().isBlank()) {
            inputs.put("step_desc", step.getDesc());
        }
        if (step.getCorrectionMode() != null && !step.getCorrectionMode().isBlank()) {
            inputs.put("step_correction_mode", step.getCorrectionMode());
        }
        String guide = formatStepGuide(step);
        if (!guide.isBlank()) {
            inputs.put("step_guide", guide);
        }
    }

    private String formatStepGuide(StepConfig step) {
        if (step == null || step.getTut() == null) {
            return "";
        }
        var tut = step.getTut();
        StringBuilder sb = new StringBuilder();
        if (tut.getSteps() != null && !tut.getSteps().isEmpty()) {
            sb.append("操作要点:\n");
            for (String item : tut.getSteps()) {
                sb.append("- ").append(item).append('\n');
            }
        }
        if (tut.getWarnings() != null && !tut.getWarnings().isEmpty()) {
            sb.append("注意:\n");
            for (String item : tut.getWarnings()) {
                sb.append("- ").append(item).append('\n');
            }
        }
        return sb.toString().trim();
    }

    /**
     * Dify 工作流只按是否带图路由：有图走 vision，无图统一走文本教学/指导。
     * 数据提交仍通过 query/data_json 进入文本分支，不再要求 Dify 单独维护 data 分支。
     */
    private void putDifyRoutingInputs(Map<String, Object> inputs, StepConfig step, boolean hasImage) {
        if (hasImage) {
            inputs.put("category", "vision");
            inputs.put("correction_mode", "vision");
            return;
        }
        inputs.put("category", "teaching");
        inputs.put("correction_mode", "auto");
    }
}
