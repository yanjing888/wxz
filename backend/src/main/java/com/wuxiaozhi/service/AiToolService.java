package com.wuxiaozhi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiaozhi.config.AiToolDefinition;
import com.wuxiaozhi.dto.*;
import com.wuxiaozhi.dto.experiment.ExperimentConfig;
import com.wuxiaozhi.dto.experiment.ExperimentDifyConfig;
import com.wuxiaozhi.dto.experiment.StepConfig;
import com.wuxiaozhi.entity.AiConversation;
import com.wuxiaozhi.entity.AiMessage;
import com.wuxiaozhi.entity.LabSession;
import com.wuxiaozhi.repository.AiConversationRepository;
import com.wuxiaozhi.repository.AiMessageRepository;
import com.wuxiaozhi.repository.LabSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class AiToolService {

    private final AiToolCatalogService catalogService;
    private final AiConversationRepository conversationRepository;
    private final AiMessageRepository messageRepository;
    private final LabSessionRepository sessionRepository;
    private final LabSessionService labSessionService;
    private final ExperimentConfigService experimentConfigService;
    private final DifyService difyService;
    private final DifyRetrieveService difyRetrieveService;
    private final StudentExperimentService studentExperimentService;
    private final ObjectMapper objectMapper;

    public AiToolService(AiToolCatalogService catalogService,
                         AiConversationRepository conversationRepository,
                         AiMessageRepository messageRepository,
                         LabSessionRepository sessionRepository,
                         LabSessionService labSessionService,
                         ExperimentConfigService experimentConfigService,
                         DifyService difyService,
                         DifyRetrieveService difyRetrieveService,
                         StudentExperimentService studentExperimentService,
                         ObjectMapper objectMapper) {
        this.catalogService = catalogService;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.sessionRepository = sessionRepository;
        this.labSessionService = labSessionService;
        this.experimentConfigService = experimentConfigService;
        this.difyService = difyService;
        this.difyRetrieveService = difyRetrieveService;
        this.studentExperimentService = studentExperimentService;
        this.objectMapper = objectMapper;
    }

    public List<AiConversationItemDto> listConversations(Long userId, String toolCode) {
        catalogService.requireTool(toolCode);
        return conversationRepository.findByUserIdAndToolCodeOrderByUpdatedAtDesc(userId, toolCode).stream()
                .map(this::toConversationItem)
                .toList();
    }

    @Transactional
    public AiConversationItemDto createConversation(Long userId, String toolCode) {
        catalogService.requireTool(toolCode);
        AiConversation conversation = new AiConversation();
        conversation.setUserId(userId);
        conversation.setToolCode(toolCode);
        conversation.setTitle("");
        return toConversationItem(conversationRepository.save(conversation));
    }

    public List<AiMessageItemDto> listMessages(Long userId, Long conversationId) {
        AiConversation conversation = requireConversation(userId, conversationId);
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId()).stream()
                .map(this::toMessageItem)
                .toList();
    }

    public SseEmitter chatStream(Long userId, Long conversationId, AiChatRequest req, Authentication authentication) {
        AiConversation conversation = requireConversation(userId, conversationId);
        AiToolDefinition tool = catalogService.requireTool(conversation.getToolCode(), authentication);
        String userMessage = req.getUserMessage().trim();
        if (userMessage.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入问题");
        }

        SseEmitter emitter = new SseEmitter(300_000L);
        emitter.onTimeout(emitter::complete);

        AiMessage userMsg = new AiMessage();
        userMsg.setConversationId(conversation.getId());
        userMsg.setRole("user");
        userMsg.setText(userMessage);
        userMsg = messageRepository.save(userMsg);
        final Long userMessageId = userMsg.getId();

        if (conversation.getTitle() == null || conversation.getTitle().isBlank()) {
            conversation.setTitle(truncateTitle(userMessage));
        }
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        try {
            emitter.send(SseEmitter.event().name("start").data(Map.of("ok", true)));
        } catch (IOException e) {
            emitter.completeWithError(e);
            return emitter;
        }

        String workflowKey = catalogService.resolveWorkflowKey(tool);
        Map<String, Object> inputs = buildToolInputs(tool, userMessage, req.getExperimentCode(), conversation.getId());
        String difyUser = "ai-tool-" + userId + "-" + conversation.getId();

        CompletableFuture.runAsync(() -> {
            try {
                AssistResponse resp = difyService.streamAssist(
                        workflowKey,
                        inputs,
                        difyUser,
                        null,
                        0,
                        false,
                        null,
                        delta -> sendChunk(emitter, delta),
                        marks -> {
                        },
                        () -> sendAnswerEnd(emitter)
                );

                AiMessage aiMsg = new AiMessage();
                aiMsg.setConversationId(conversation.getId());
                aiMsg.setRole("ai");
                aiMsg.setText(resp.getFeedback() != null ? resp.getFeedback() : "");
                aiMsg = messageRepository.save(aiMsg);

                Map<String, Object> done = new LinkedHashMap<>();
                done.put("feedback", resp.getFeedback());
                done.put("aiMessageId", aiMsg.getId());
                done.put("userMessageId", userMessageId);
                emitter.send(SseEmitter.event().name("done").data(done));
                emitter.complete();
            } catch (Exception e) {
                sendStreamError(emitter, e);
            }
        });
        return emitter;
    }

    public List<AiRecapSessionItemDto> listRecapSessions(Long userId) {
        return sessionRepository.findByUserIdOrderByStartTimeDesc(userId).stream()
                .filter(session -> "FINISHED".equalsIgnoreCase(session.getStatus()))
                .limit(20)
                .map(this::toRecapItem)
                .toList();
    }

    public AiToolInvokeResponse invoke(Long userId, String toolCode, AiToolInvokeRequest request) {
        AiToolDefinition tool = catalogService.requireTool(toolCode);
        String action = request.getAction() != null ? request.getAction().trim() : "run";
        Map<String, Object> inputs = new LinkedHashMap<>();
        if (request.getInputs() != null) {
            inputs.putAll(request.getInputs());
        }
        inputs.put("action", action);
        inputs.put("tool_code", tool.getCode());
        inputs.put("tool_name", tool.getName());
        inputs.put("category", tool.getCategory());
        inputs.put("page_type", tool.getPageType());

        if ("recap".equals(tool.getPageType()) || "lab-recap".equals(tool.getCode())) {
            enrichRecapInputs(userId, inputs);
        }
        if ("report-assist".equals(tool.getCode())) {
            enrichReportAssistInputs(userId, inputs);
        }
        if ("report-review".equals(tool.getCode())) {
            enrichReportAssistInputs(userId, inputs);
        }
        if ("error-trace".equals(tool.getCode())) {
            enrichErrorTraceInputs(userId, inputs);
        }
        if ("instrument-reading".equals(tool.getCode()) || "instrument-guide".equals(tool.getCode())) {
            enrichInstrumentInputs(inputs);
        }
        if ("equipment-check".equals(tool.getCode()) || "prep-report".equals(tool.getCode())) {
            enrichStepOutlineInputs(inputs);
        }
        enrichExperimentInputs(inputs);

        String query = buildInvokeQuery(tool, action, inputs);
        inputs.put("query", query);

        String workflowKey = catalogService.resolveWorkflowKey(tool);
        AiToolInvokeResponse response = difyService.invokeTool(
                workflowKey, inputs, "ai-tool-" + userId, request.getImageUrl());
        if ("lab-recap".equals(tool.getCode()) || "recap".equals(tool.getPageType())) {
            String experimentCode = stringValue(inputs.get("experimentCode"));
            if (!experimentCode.isBlank()) {
                studentExperimentService.markRecapCompleted(userId, experimentCode);
            }
        }
        return response;
    }

    private void enrichRecapInputs(Long userId, Map<String, Object> inputs) {
        Long sessionId = parseLong(inputs.get("sessionId"));
        if (sessionId != null) {
            try {
                Map<String, Object> report = labSessionService.buildReportData(sessionId, userId);
                inputs.put("report_context", report);
                inputs.put("experiment_name", stringValue(report.get("experimentName")));
                inputs.put("step_summaries_json", writeJson(report.get("stepSummaries")));
                inputs.put("data_logs_json", writeJson(report.get("dataLogEntries")));
                inputs.put("corrections_json", writeJson(report.get("corrections")));
                inputs.put("env_logs_json", writeJson(report.get("envLogs")));
                inputs.put("help_count", report.get("helpCount"));
                inputs.put("error_point_count", report.get("errorPointCount"));
                sessionRepository.findByIdAndUserId(sessionId, userId).ifPresent(session -> {
                    inputs.put("experiment_code", session.getExperimentCode());
                    inputs.put("session_summary", formatSessionSummary(session));
                });
            } catch (ResponseStatusException ignored) {
                sessionRepository.findByIdAndUserId(sessionId, userId).ifPresent(session -> {
                    inputs.put("session_summary", formatSessionSummary(session));
                    inputs.put("experiment_code", session.getExperimentCode());
                    inputs.put("experiment_name", session.getExperimentName());
                });
            }
            return;
        }
        String experimentCode = stringValue(inputs.get("experimentCode"));
        List<LabSession> sessions = sessionRepository.findByUserIdOrderByStartTimeDesc(userId).stream()
                .filter(s -> "FINISHED".equalsIgnoreCase(s.getStatus()))
                .filter(s -> experimentCode.isBlank() || experimentCode.equals(s.getExperimentCode()))
                .limit(5)
                .toList();
        StringBuilder summary = new StringBuilder();
        for (LabSession session : sessions) {
            if (!summary.isEmpty()) {
                summary.append("\n\n");
            }
            summary.append(formatSessionSummary(session));
        }
        inputs.put("session_summary", summary.toString());
        inputs.put("finished_count", sessions.size());
    }

    private void enrichReportAssistInputs(Long userId, Map<String, Object> inputs) {
        Long sessionId = parseLong(inputs.get("sessionId"));
        if (sessionId == null) {
            return;
        }
        try {
            Map<String, Object> report = labSessionService.buildReportData(sessionId, userId);
            inputs.put("report_context", report);
            inputs.put("experiment_name", stringValue(report.get("experimentName")));
            if (report.get("dataLogEntries") != null) {
                inputs.put("data_logs_json", writeJson(report.get("dataLogEntries")));
            }
            sessionRepository.findByIdAndUserId(sessionId, userId).ifPresent(session -> {
                inputs.put("experiment_code", session.getExperimentCode());
                inputs.put("session_summary", formatSessionSummary(session));
            });
        } catch (ResponseStatusException ignored) {
            // session not accessible
        }
    }

    /** 误差溯源：把整场实验的操作、数据与环境记录一起交给模型反推误差来源 */
    private void enrichErrorTraceInputs(Long userId, Map<String, Object> inputs) {
        Long sessionId = parseLong(inputs.get("sessionId"));
        if (sessionId == null) {
            return;
        }
        try {
            Map<String, Object> report = labSessionService.buildReportData(sessionId, userId);
            inputs.put("experiment_name", stringValue(report.get("experimentName")));
            inputs.put("step_summaries_json", writeJson(report.get("stepSummaries")));
            inputs.put("data_logs_json", writeJson(report.get("dataLogEntries")));
            inputs.put("corrections_json", writeJson(report.get("corrections")));
            inputs.put("env_logs_json", writeJson(report.get("envLogs")));
            inputs.put("help_count", report.get("helpCount"));
            inputs.put("error_point_count", report.get("errorPointCount"));
            sessionRepository.findByIdAndUserId(sessionId, userId).ifPresent(session -> {
                inputs.put("experiment_code", session.getExperimentCode());
                inputs.put("session_summary", formatSessionSummary(session));
            });
        } catch (ResponseStatusException ignored) {
            // session not accessible
        }
    }

    /** 器材核对 / 预习报告：把整套实验步骤与所用仪器交给模型，避免生成通用模板 */
    private void enrichStepOutlineInputs(Map<String, Object> inputs) {
        String experimentCode = resolveExperimentCode(inputs);
        if (experimentCode.isBlank()) {
            return;
        }
        try {
            ExperimentConfig exp = experimentConfigService.getByCode(experimentCode);
            if (exp.getSteps() == null || exp.getSteps().isEmpty()) {
                return;
            }
            List<Map<String, Object>> outline = exp.getSteps().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> {
                        StepConfig step = entry.getValue();
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("stepNo", entry.getKey());
                        item.put("title", step.getTitle() != null ? step.getTitle() : "");
                        item.put("desc", step.getDesc() != null ? step.getDesc() : "");
                        item.put("deviceType", step.getDeviceType() != null ? step.getDeviceType() : "");
                        return item;
                    })
                    .collect(Collectors.toList());
            inputs.put("step_outline_json", writeJson(outline));
        } catch (RuntimeException ignored) {
            // unknown experiment code
        }
    }

    /** 读数助手 / 仪器速查：补上当前步骤的仪器类型与待测字段 */
    private void enrichInstrumentInputs(Map<String, Object> inputs) {
        String experimentCode = resolveExperimentCode(inputs);
        int stepNo = (int) Math.max(0, orZero(parseLong(inputs.get("stepNo"))));
        if (experimentCode.isBlank() || stepNo <= 0) {
            return;
        }
        try {
            ExperimentConfig exp = experimentConfigService.getByCode(experimentCode);
            StepConfig step = exp.getSteps() != null ? exp.getSteps().get(String.valueOf(stepNo)) : null;
            if (step == null) {
                return;
            }
            inputs.putIfAbsent("stepTitle", step.getTitle() != null ? step.getTitle() : "");
            inputs.putIfAbsent("stepDesc", step.getDesc() != null ? step.getDesc() : "");
            inputs.putIfAbsent("deviceType", step.getDeviceType() != null ? step.getDeviceType() : "");
            if (step.getDataFields() != null && !step.getDataFields().isEmpty()) {
                inputs.put("data_fields_json", writeJson(step.getDataFields()));
            }
        } catch (RuntimeException ignored) {
            // unknown experiment code
        }
    }

    /** 所有智能体通用：带上实验名称、步骤概览与报告知识点，避免模型泛泛而谈 */
    private void enrichExperimentInputs(Map<String, Object> inputs) {
        String experimentCode = resolveExperimentCode(inputs);
        if (experimentCode.isBlank()) {
            return;
        }
        try {
            ExperimentConfig exp = experimentConfigService.getByCode(experimentCode);
            inputs.putIfAbsent("experiment_code", exp.getCode());
            inputs.putIfAbsent("experiment_name", exp.getName());
            inputs.putIfAbsent("experiment_type", exp.getName());
            if (exp.getMenuLabels() != null && !exp.getMenuLabels().isEmpty()) {
                inputs.putIfAbsent("step_labels", String.join("、", exp.getMenuLabels()));
            }
            if (exp.getReportKnowledge() != null && !exp.getReportKnowledge().isEmpty()) {
                inputs.putIfAbsent("experiment_knowledge", String.join("\n", exp.getReportKnowledge()));
            }
        } catch (RuntimeException ignored) {
            // unknown experiment code
        }
    }

    private String resolveExperimentCode(Map<String, Object> inputs) {
        String code = stringValue(inputs.get("experimentCode"));
        return code.isBlank() ? stringValue(inputs.get("experiment_code")) : code;
    }

    private long orZero(Long value) {
        return value != null ? value : 0L;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String buildInvokeQuery(AiToolDefinition tool, String action, Map<String, Object> inputs) {
        Object explicit = inputs.get("query");
        if (explicit != null && !String.valueOf(explicit).isBlank()) {
            return String.valueOf(explicit).trim();
        }
        String experimentName = stringValue(inputs.get("experimentName"));
        if (experimentName.isBlank()) {
            experimentName = stringValue(inputs.get("experiment_name"));
        }
        return switch (action) {
            case "generate" -> "请为「" + tool.getName() + "」生成内容"
                    + (experimentName.isBlank() ? "" : "，实验：" + experimentName);
            case "grade" -> "请批改学生作答";
            case "analyze" -> buildAnalyzeQuery(inputs);
            case "review" -> "请作为大学物理实验教师对学生实验报告做预评（非终裁）："
                    + "按完整性、数据可信度、误差分析、结论、思考题质量给出建议分档/分数区间与可编辑批注，"
                    + "并标记疑似空套模板或数据异常风险。不要直接给出最终成绩。";
            case "draft" -> "请根据实验记录与数据，生成实验报告初稿";
            case "polish" -> "请润色以下报告段落，保持学生表述风格";
            case "check" -> "请检查实验报告是否完整规范，列出缺失项与修改建议（不要打分）";
            case "brief" -> "请给出本次实验的课前要点：实验目标、需携带/检查事项、一个核心公式、两个易错点，控制在300字内";
            case "equipment" -> "请列出本实验所需的仪器与耗材清单。严格返回 JSON："
                    + "{\"items\":[{\"name\":\"\",\"spec\":\"\",\"purpose\":\"\",\"checkPoint\":\"\",\"bySelf\":true}]}，"
                    + "其中 checkPoint 是进实验室后要先确认的状态（如是否调平、是否有划痕），"
                    + "bySelf 表示是否需要学生自带。不要输出 JSON 以外的任何文字。";
            case "prepReport" -> "请生成本次实验的预习报告初稿，按「实验目的 / 实验原理 / 实验仪器 / 实验步骤 / 数据记录表设计 / 注意事项」"
                    + "六个部分组织。实验原理需含核心公式及各符号含义；数据记录表设计需给出表头与行数建议。"
                    + "这是学生动笔前的参考框架，请避免直接给出实验结论与测量结果。";
            case "ocr" -> "请识别图片中的手写实验数据记录表。严格返回 JSON："
                    + "{\"headers\":[\"\"],\"rows\":[[\"\"]],\"warnings\":[\"\"]}，"
                    + "headers 为表头（含单位），rows 为各行单元格文本，warnings 记录字迹模糊或存在歧义的位置。"
                    + "不要修改或“修正”学生写下的数值。不要输出 JSON 以外的任何文字。";
            case "think" -> "请针对学生提出的实验思考题，给出解题思路与作答要点。要求："
                    + "1) 先点明这道题考查的物理概念；2) 给出分析思路的关键几步；3) 提示需要结合本次实验的哪些数据或现象；"
                    + "4) 列出常见的错误答法。不要直接写出可以照抄的完整答案。";
            case "instrument" -> "请给出当前实验步骤的仪器操作与读数要点，简洁分条列出";
            case "reading" -> buildReadingQuery(inputs);
            case "doctor" -> "请判断以下测量数据在物理上是否合理：结合本实验的典型量级与常见错误，"
                    + "指出可疑数据、可能原因与复测建议。已附本地统计结果，请勿重复计算。";
            case "trace" -> buildTraceQuery(inputs);
            case "quiz" -> "请围绕本实验出 5 道预习自测题（3 道单选 + 2 道判断），"
                    + "严格返回 JSON：{\"questions\":[{\"type\":\"single|judge\",\"question\":\"\","
                    + "\"options\":[\"\"],\"answer\":0,\"explain\":\"\"}]}，不要输出 JSON 以外的任何文字。";
            case "recap" -> "请基于本次实验的真实纠错、数据与过程记录做个性化复盘。必须输出："
                    + "1) 三条个人薄弱点（有过程依据）；2) 一条最可能的误差来源假设（说明依据）；"
                    + "3) 一条下次实验可执行的行动建议。不要空泛说教，不要编造未出现的问题。";
            default -> "请处理「" + tool.getName() + "」请求";
        };
    }

    private String buildReadingQuery(Map<String, Object> inputs) {
        String instrument = stringValue(inputs.get("instrumentLabel"));
        String precision = stringValue(inputs.get("precision"));
        String experimentName = stringValue(inputs.get("experimentName"));
        if (experimentName.isBlank()) {
            experimentName = stringValue(inputs.get("experiment_name"));
        }
        String stepTitle = stringValue(inputs.get("stepTitle"));
        String targetLabel = stringValue(inputs.get("targetFieldLabel"));
        String targetUnit = stringValue(inputs.get("targetFieldUnit"));
        String dataFields = stringValue(inputs.get("dataFields"));
        StringBuilder sb = new StringBuilder("请结合当前实验步骤识别图片中的测量读数。");
        if (!experimentName.isBlank()) sb.append("实验：").append(experimentName).append("。");
        if (!stepTitle.isBlank()) sb.append("步骤：").append(stepTitle).append("。");
        if (!targetLabel.isBlank()) {
            sb.append("本次要填入的数据项：").append(targetLabel);
            if (!targetUnit.isBlank()) sb.append("（单位 ").append(targetUnit).append("）");
            sb.append("。");
        }
        if (!instrument.isBlank() && !"自动识别".equals(instrument)) {
            sb.append("识别对象优先按：").append(instrument).append("。");
        }
        if (!dataFields.isBlank()) {
            sb.append("当前步骤数据项包括：").append(dataFields).append("。");
        }
        sb.append("要求：1) 给出建议读数并带单位；");
        if (!precision.isBlank()) {
            sb.append("2) 按分度值 ").append(precision).append(" 保留正确位数；");
        } else {
            sb.append("2) 如果能判断分度值，请说明应保留的位数；不能判断则说明需要学生确认；");
        }
        sb.append("3) 按图片实际可见结构讲解读数过程，不要套用无关仪器模板；");
        sb.append("4) 指出本次读数容易出错的位置。若图片模糊或字段不匹配，请直接说明并给出重拍建议。");
        return sb.toString();
    }

    private String buildTraceQuery(Map<String, Object> inputs) {
        String symptom = stringValue(inputs.get("symptom"));
        String expected = stringValue(inputs.get("expectedValue"));
        String actual = stringValue(inputs.get("actualValue"));
        StringBuilder sb = new StringBuilder("请依据本次实验的全过程记录，分析结果偏差的最可能来源。");
        if (!symptom.isBlank()) {
            sb.append("学生描述的问题：").append(symptom).append("。");
        }
        if (!expected.isBlank() || !actual.isBlank()) {
            sb.append("理论值：").append(expected.isBlank() ? "未提供" : expected);
            sb.append("，实测值：").append(actual.isBlank() ? "未提供" : actual).append("。");
        }
        sb.append("请按可能性从高到低列出 2-3 个嫌疑步骤，每个说明：判断依据（须引用具体的数据或纠错记录）、"
                + "影响方向（偏大/偏小）、以及一个可以在实验室快速验证的方法。不要泛泛罗列所有误差来源。");
        return sb.toString();
    }

    private String buildAnalyzeQuery(Map<String, Object> inputs) {
        String type = stringValue(inputs.get("analysisType"));
        return switch (type) {
            case "uncertainty" -> "请对以下测量数据进行不确定度分析：含仪器误差、合成不确定度、相对不确定度与结果有效数字";
            case "significant" -> "请检查以下数据的有效数字、单位与书写规范，并给出改正建议";
            case "trend" -> "请分析以下测量数据的趋势与规律";
            case "outlier" -> "请检测以下测量数据中的异常点并分析可能原因";
            case "fit" -> "请对以下数据进行拟合分析并给出物理结论";
            default -> "请对以下实验测量数据进行误差分析";
        };
    }

    private AiRecapSessionItemDto toRecapItem(LabSession session) {
        AiRecapSessionItemDto dto = new AiRecapSessionItemDto();
        dto.setSessionId(session.getId());
        dto.setExperimentCode(session.getExperimentCode());
        dto.setExperimentName(session.getExperimentName());
        dto.setEndTime(session.getEndTime());
        dto.setHelpCount(session.getHelpCount());
        dto.setErrorPointCount(session.getErrorPointCount());
        return dto;
    }

    private String formatSessionSummary(LabSession session) {
        return """
                实验：%s（%s）
                完成时间：%s
                问答次数：%d
                纠错点数：%d
                """.formatted(
                session.getExperimentName(),
                session.getExperimentCode(),
                session.getEndTime() != null ? session.getEndTime() : session.getStartTime(),
                session.getHelpCount(),
                session.getErrorPointCount()
        ).trim();
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value != null ? String.valueOf(value).trim() : "";
    }

    private Map<String, Object> buildToolInputs(AiToolDefinition tool, String userMessage,
                                                String experimentCode, Long conversationId) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("query", userMessage);
        inputs.put("category", tool.getCategory());
        inputs.put("correction_mode", "teaching");
        inputs.put("tool_code", tool.getCode());
        inputs.put("tool_name", tool.getName());
        inputs.put("experiment_code", "general");
        inputs.put("experiment_name", tool.getName());
        inputs.put("step_id", 0);
        inputs.put("step_title", tool.getName());
        inputs.put("step_desc", tool.getDescription());

        ExperimentConfig exp = findExperiment(experimentCode);
        if (exp != null) {
            inputs.put("experiment_code", exp.getCode());
            inputs.put("experiment_name", exp.getName());
            inputs.put("experiment_type", exp.getName());
            if (exp.getReportKnowledge() != null && !exp.getReportKnowledge().isEmpty()) {
                inputs.put("experiment_knowledge", String.join("\n", exp.getReportKnowledge()));
            }
            attachKnowledgeContext(inputs, exp, userMessage);
        }

        String history = recentHistory(conversationId);
        if (!history.isBlank()) {
            inputs.put("chat_history", history);
        }
        return inputs;
    }

    private ExperimentConfig findExperiment(String experimentCode) {
        if (experimentCode == null || experimentCode.isBlank()) {
            return null;
        }
        try {
            return experimentConfigService.getByCode(experimentCode.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 原理答疑挂实验知识库：manifest 配了 dify.datasetId 才检索 */
    private void attachKnowledgeContext(Map<String, Object> inputs, ExperimentConfig exp, String userMessage) {
        ExperimentDifyConfig dify = exp.getDify();
        if (dify == null || dify.getDatasetId() == null || dify.getDatasetId().isBlank()) {
            return;
        }
        String datasetId = dify.getDatasetId().trim();
        String query = (exp.getName() != null ? exp.getName() + " " : "") + userMessage;
        inputs.put("dataset_id", datasetId);
        inputs.put("kb_context", difyRetrieveService.retrieve(datasetId, query));
    }

    /** 多轮问答需要上下文，Dify 侧会话不共享，这里带最近几轮进去 */
    private String recentHistory(Long conversationId) {
        if (conversationId == null) {
            return "";
        }
        List<AiMessage> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        if (messages.size() <= 1) {
            return "";
        }
        int from = Math.max(0, messages.size() - 7);
        return messages.subList(from, messages.size() - 1).stream()
                .map(m -> ("user".equals(m.getRole()) ? "学生：" : "助教：")
                        + (m.getText() != null ? m.getText().trim() : ""))
                .filter(s -> s.length() > 3)
                .collect(Collectors.joining("\n"));
    }

    private AiConversation requireConversation(Long userId, Long conversationId) {
        return conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "对话不存在"));
    }

    private AiConversationItemDto toConversationItem(AiConversation conversation) {
        AiConversationItemDto dto = new AiConversationItemDto();
        dto.setId(conversation.getId());
        dto.setToolCode(conversation.getToolCode());
        dto.setTitle(conversation.getTitle());
        return dto;
    }

    private AiMessageItemDto toMessageItem(AiMessage message) {
        AiMessageItemDto dto = new AiMessageItemDto();
        dto.setId(message.getId());
        dto.setRole(message.getRole());
        dto.setText(message.getText());
        dto.setCreatedAt(message.getCreatedAt());
        return dto;
    }

    private String truncateTitle(String text) {
        String plain = text.replaceAll("\\s+", " ").trim();
        return plain.length() <= 24 ? plain : plain.substring(0, 23) + "…";
    }

    private void sendChunk(SseEmitter emitter, String delta) {
        try {
            emitter.send(SseEmitter.event().name("chunk").data(new AssistStreamChunk(delta)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendAnswerEnd(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("answer_end").data(Map.of("ok", true)));
        } catch (IOException ignored) {
        }
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
}
