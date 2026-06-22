package com.wuxiaozhi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiaozhi.config.DifyProperties;
import com.wuxiaozhi.dto.AssistResponse;
import com.wuxiaozhi.dto.EnvCheckResponse;
import com.wuxiaozhi.dto.experiment.AssistMock;
import com.wuxiaozhi.dto.experiment.ExperimentConfig;
import com.wuxiaozhi.dto.experiment.MarkDto;
import com.wuxiaozhi.dto.experiment.StepConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Service
public class DifyService {

    private static final Logger log = LoggerFactory.getLogger(DifyService.class);

    private final DifyProperties difyProperties;
    private final ObjectMapper objectMapper;
    private final FileStorageService fileStorageService;
    private final RestTemplate restTemplate = new RestTemplate();

    public DifyService(DifyProperties difyProperties, ObjectMapper objectMapper,
                       FileStorageService fileStorageService) {
        this.difyProperties = difyProperties;
        this.objectMapper = objectMapper;
        this.fileStorageService = fileStorageService;
    }

    public AssistResponse assist(String workflowKey, Map<String, Object> inputs, String userId,
                                 ExperimentConfig experiment, int stepId, boolean hasImage,
                                 String imageUrl) {
        return assist(workflowKey, inputs, userId, experiment, stepId, hasImage, imageUrl, hasDataAssist(inputs));
    }

    public AssistResponse assist(String workflowKey, Map<String, Object> inputs, String userId,
                                 ExperimentConfig experiment, int stepId, boolean hasImage,
                                 String imageUrl, boolean hasData) {
        if (canCall(workflowKey)) {
            try {
                JsonNode payload = difyProperties.isChatMode(workflowKey)
                        ? runChat(workflowKey, inputs, userId, buildAssistQuery(inputs, hasImage, hasData), imageUrl)
                        : runWorkflow(workflowKey, inputs, userId);
                return parseAssistPayload(payload, true, hasImage, hasData);
            } catch (Throwable e) {
                log.warn("Dify assist failed, fallback to mock: {}", e.getMessage());
            }
        }
        return mockAssist(experiment, stepId, hasImage, hasData);
    }

    /**
     * 流式调用 Dify：answer 仅含用户可见文本；regions 从 node_finished（参数提取器）单独采集。
     * Dify 不可用时才回退 Mock。
     */
    public AssistResponse streamAssist(String workflowKey, Map<String, Object> inputs, String userId,
                                       ExperimentConfig experiment, int stepId, boolean hasImage,
                                       String imageUrl, Consumer<String> onDelta,
                                       Consumer<List<MarkDto>> onMarks, Runnable onAnswerComplete) {
        if (canCall(workflowKey) && difyProperties.isChatMode(workflowKey)) {
            try {
                boolean hasData = hasDataAssist(inputs);
                String query = buildAssistQuery(inputs, hasImage, hasData);
                Map<String, Object> body = buildChatBody(workflowKey, inputs, userId, query, imageUrl);
                body.put("response_mode", "streaming");
                String apiKey = difyProperties.resolveApiKey(workflowKey);
                String url = difyProperties.getBaseUrl().replaceAll("/$", "") + "/chat-messages";
                StringBuilder full = new StringBuilder();
                List<MarkDto> streamMarks = new ArrayList<>();
                AtomicBoolean answerEnded = new AtomicBoolean(false);
                streamChatSse(url, apiKey, body, node -> {
                    notifyAnswerComplete(node, answerEnded, onAnswerComplete);
                    absorbStreamMeta(node, streamMarks, onMarks);
                    String delta = extractStreamDelta(node);
                    if (!delta.isEmpty()) {
                        full.append(delta);
                        onDelta.accept(delta);
                    }
                });
                log.info("Dify stream ok, workflowKey={}, length={}, marks={}",
                        workflowKey, full.length(), streamMarks.size());
                return buildStreamAssistResponse(full.toString(), streamMarks, hasImage, hasData);
            } catch (Throwable e) {
                log.warn("Dify stream failed, fallback to mock: {}", e.getMessage());
            }
        }
        boolean hasData = hasDataAssist(inputs);
        AssistResponse mock = mockAssist(experiment, stepId, hasImage, hasData);
        if (onMarks != null && mock.getMarks() != null && !mock.getMarks().isEmpty()) {
            onMarks.accept(mock.getMarks());
        }
        streamMockFeedback(mock.getFeedback(), onDelta);
        if (onAnswerComplete != null) {
            onAnswerComplete.run();
        }
        return mock;
    }

    /** Dify 正文流结束后仍会跑知识库等节点；在 message_end 时通知前端收起光标。 */
    private void notifyAnswerComplete(JsonNode node, AtomicBoolean answerEnded, Runnable onAnswerComplete) {
        if (onAnswerComplete == null || answerEnded.get()) {
            return;
        }
        String event = node.path("event").asText("");
        if ("message_end".equals(event) || "agent_message_end".equals(event)) {
            answerEnded.set(true);
            onAnswerComplete.run();
        }
    }

    /** 从 Dify SSE 的 node_finished 采集参数提取器 / 多模态 LLM 的 regions，并即时回调 */
    private void absorbStreamMeta(JsonNode node, List<MarkDto> marksHolder, Consumer<List<MarkDto>> onMarks) {
        if (!"node_finished".equals(node.path("event").asText(""))) {
            return;
        }
        JsonNode data = node.path("data");
        JsonNode outputs = data.path("outputs");
        if (outputs.isMissingNode() || outputs.isNull()) {
            return;
        }
        String nodeType = data.path("node_type").asText("");
        String title = data.path("title").asText("");

        List<MarkDto> parsed = List.of();
        if ("parameter-extractor".equals(nodeType) || title.contains("参数提取")) {
            parsed = DifyRegionParser.fromOutputs(outputs, objectMapper);
        } else if ("llm".equals(nodeType) && (title.contains("多模态") || title.contains("理解"))) {
            parsed = DifyRegionParser.fromOutputs(outputs, objectMapper);
        }

        if (!parsed.isEmpty()) {
            marksHolder.clear();
            marksHolder.addAll(parsed);
            log.info("Dify regions captured from node '{}', count={}", title, parsed.size());
            if (onMarks != null) {
                onMarks.accept(List.copyOf(parsed));
            }
        }
    }

    private AssistResponse buildStreamAssistResponse(String answer, List<MarkDto> streamMarks, boolean hasImage, boolean hasData) {
        AssistResponse resp = new AssistResponse();
        resp.setFromDify(true);
        if (!streamMarks.isEmpty() || hasImage) {
            resp.setType("vision_correction");
        } else if (hasData) {
            resp.setType("data_correction");
        } else {
            resp.setType("text_assist");
        }
        resp.setFeedback(answer != null ? answer.trim() : "");
        resp.setMarks(streamMarks);
        return resp;
    }

    private boolean hasDataAssist(Map<String, Object> inputs) {
        if (inputs == null) {
            return false;
        }
        Object mode = inputs.get("correction_mode");
        if ("data".equals(String.valueOf(mode))) {
            return true;
        }
        Object data = inputs.get("data_json");
        return data != null && !String.valueOf(data).isBlank();
    }

    /** mock 兜底：按字符逐字推送，模拟真实流式打字效果 */
    private void streamMockFeedback(String text, Consumer<String> onDelta) {
        if (text == null || text.isBlank()) return;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            onDelta.accept(new String(Character.toChars(cp)));
            i += Character.charCount(cp);
            try {
                Thread.sleep(18);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private Map<String, Object> buildChatBody(String workflowKey, Map<String, Object> inputs, String userId,
                                              String query, String imageUrl) {
        String apiKey = difyProperties.resolveApiKey(workflowKey);
        Map<String, Object> mergedInputs = new LinkedHashMap<>(inputs);
        if (!mergedInputs.containsKey("query") || String.valueOf(mergedInputs.get("query")).isBlank()) {
            mergedInputs.put("query", query);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inputs", mergedInputs);
        body.put("query", query);
        body.put("user", userId);

        if (imageUrl != null && !imageUrl.isBlank()) {
            String uploadFileId = uploadImageToDify(imageUrl, userId, apiKey);
            Map<String, Object> fileRef = difyFileRef(uploadFileId);
            mergedInputs.put("image", fileRef);
            body.put("inputs", mergedInputs);
            body.put("files", List.of(fileRef));
            if (!query.contains("图")) {
                query = "用户已上传实验图片，请结合图片回答：" + query;
                body.put("query", query);
                mergedInputs.put("query", query);
                body.put("inputs", mergedInputs);
            }
            log.info("Dify chat with image, workflowKey={}, uploadFileId={}, inputVar=image", workflowKey, uploadFileId);
        }
        return body;
    }

    private String extractStreamDelta(JsonNode node) {
        String event = node.path("event").asText("");
        if ("error".equals(event)) {
            throw new IllegalStateException(node.path("message").asText("Dify stream error"));
        }
        if ("message".equals(event) || "agent_message".equals(event)) {
            return node.path("answer").asText("");
        }
        if ("text_chunk".equals(event)) {
            return node.path("data").path("text").asText("");
        }
        return "";
    }

    private void streamChatSse(String url, String apiKey, Map<String, Object> body,
                               Consumer<JsonNode> onEvent) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(300_000);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Accept", "text/event-stream");

        byte[] payload = objectMapper.writeValueAsBytes(body);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload);
        }

        int code = conn.getResponseCode();
        if (code >= 400) {
            try (BufferedReader err = new BufferedReader(
                    new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = err.readLine()) != null) sb.append(line);
                throw new IllegalStateException(sb.isEmpty() ? "HTTP " + code : sb.toString());
            }
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) continue;
                JsonNode node = objectMapper.readTree(data);
                if (node.has("code") && node.has("message")) {
                    throw new IllegalStateException(node.path("message").asText("Dify error"));
                }
                onEvent.accept(node);
            }
        } finally {
            conn.disconnect();
        }
    }

    public EnvCheckResponse envCheck(Map<String, Object> inputs, String userId, String imageUrl) {
        if (canCall("env-check")) {
            try {
                boolean hasImage = imageUrl != null && !imageUrl.isBlank();
                JsonNode payload = difyProperties.isChatMode("env-check")
                        ? runChat("env-check", inputs, userId, buildEnvCheckQuery(inputs, hasImage), imageUrl)
                        : runWorkflow("env-check", buildEnvWorkflowInputs(inputs, imageUrl, userId), userId);
                return parseEnvPayload(payload);
            } catch (Exception e) {
                log.warn("Dify env-check failed, fallback to mock: {}", e.getMessage());
            }
        }
        return mockEnvCheck();
    }

    public boolean isConfigured() {
        return difyProperties.isConfigured();
    }

    public String getAppMode() {
        return difyProperties.getAppMode();
    }

    private boolean canCall(String key) {
        return difyProperties.canRun(key);
    }

    private String buildAssistQuery(Map<String, Object> inputs, boolean hasImage, boolean hasData) {
        Object q = inputs.get("query");
        if (q == null) q = inputs.get("user_query");
        String userQuery = q != null ? String.valueOf(q).trim() : "";
        if (!userQuery.isBlank()) {
            return userQuery;
        }
        if (hasData) {
            Object dataJson = inputs.get("data_json");
            return "请检查以下实验测量数据是否合理，并指出可能的操作或计算错误：\n"
                    + (dataJson != null ? dataJson : "");
        }
        if (hasImage) {
            return "请分析上传的实验图片。";
        }
        return "你好";
    }

    private String buildEnvCheckQuery(Map<String, Object> inputs, boolean hasImage) {
        String experimentType = experimentTypeFromInputs(inputs);
        String action = hasImage
                ? "请结合本次监控抽帧画面，对实验台环境进行安全巡检"
                : "请对当前实验台环境进行巡检";
        String suffix = "，返回等级 L0-L2、摘要与建议。";
        if (experimentType.isBlank()) {
            return action + suffix;
        }
        return "实验类型：" + experimentType + "。" + action + suffix;
    }

    private Map<String, Object> buildEnvWorkflowInputs(Map<String, Object> inputs, String imageUrl, String userId) {
        Map<String, Object> workflowInputs = new LinkedHashMap<>(inputs);
        if (imageUrl != null && !imageUrl.isBlank()) {
            String apiKey = difyProperties.resolveApiKey("env-check");
            Map<String, Object> fileRef = difyFileRef(uploadImageToDify(imageUrl, userId, apiKey));
            workflowInputs.put("frame_image", fileRef);
            workflowInputs.put("image", fileRef);
            workflowInputs.putIfAbsent("frame_url", imageUrl);
        }
        return workflowInputs;
    }

    private String textFromInputs(Map<String, Object> inputs, String key, String defaultValue) {
        Object v = inputs.get(key);
        if (v == null) return defaultValue;
        String s = String.valueOf(v).trim();
        return s.isBlank() ? defaultValue : s;
    }

    /** 与 Dify 开始节点 experiment_type 及页面左上角实验名称对齐 */
    private String experimentTypeFromInputs(Map<String, Object> inputs) {
        return textFromInputs(inputs, "experiment_type",
                textFromInputs(inputs, "experiment_name", ""));
    }

    private JsonNode runWorkflow(String workflowKey, Map<String, Object> inputs, String userId) {
        String url = difyProperties.getBaseUrl().replaceAll("/$", "") + "/workflows/run";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inputs", inputs);
        body.put("response_mode", "blocking");
        body.put("user", userId);

        JsonNode root = postJson(url, difyProperties.resolveApiKey(workflowKey), body);
        return root.path("data").path("outputs");
    }

    private JsonNode runChat(String workflowKey, Map<String, Object> inputs, String userId, String query,
                             String imageUrl) {
        String url = difyProperties.getBaseUrl().replaceAll("/$", "") + "/chat-messages";
        Map<String, Object> body = buildChatBody(workflowKey, inputs, userId, query, imageUrl);
        body.put("response_mode", "blocking");
        JsonNode root = postJson(url, difyProperties.resolveApiKey(workflowKey), body);
        log.info("Dify chat ok, workflowKey={}, query={}", workflowKey, query);
        return root;
    }

    private Map<String, Object> difyFileRef(String uploadFileId) {
        Map<String, Object> fileRef = new LinkedHashMap<>();
        fileRef.put("type", "image");
        fileRef.put("transfer_method", "local_file");
        fileRef.put("upload_file_id", uploadFileId);
        return fileRef;
    }

    private String uploadImageToDify(String imageUrl, String userId, String apiKey) {
        Path path = fileStorageService.resolve(imageUrl);
        if (!Files.exists(path)) {
            throw new IllegalStateException("Image file not found: " + imageUrl);
        }

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read image: " + imageUrl, e);
        }
        String uploadName = buildDifyUploadFilename(path.getFileName().toString(), bytes);
        log.info("Uploading image to Dify, stored={}, uploadName={}, bytes={}", path.getFileName(), uploadName, bytes.length);

        String uploadUrl = difyProperties.getBaseUrl().replaceAll("/$", "") + "/files/upload";

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return uploadName;
            }
        });
        form.add("user", userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(apiKey);

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(form, headers);
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(uploadUrl, HttpMethod.POST, entity, JsonNode.class);
            JsonNode root = response.getBody();
            if (root == null || !root.has("id")) {
                throw new IllegalStateException("Dify file upload returned no id");
            }
            return root.path("id").asText();
        } catch (HttpStatusCodeException ex) {
            String msg = ex.getResponseBodyAsString();
            throw new IllegalStateException(msg.isBlank() ? ex.getMessage() : msg, ex);
        }
    }

    /** 按文件头决定上传文件名，避免 JPEG 内容却带 .png 后缀导致 Dify 视觉模型无法识别。 */
    private String buildDifyUploadFilename(String storedName, byte[] bytes) {
        String ext = FileStorageService.resolveExtension(storedName, null, bytes);
        return "wxz-upload" + ext;
    }

    private JsonNode postJson(String url, String apiKey, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.POST, entity, JsonNode.class);
            JsonNode root = response.getBody();
            if (root == null) {
                throw new IllegalStateException("Empty Dify response");
            }
            if (root.has("code") && root.has("message")) {
                throw new IllegalStateException(root.path("message").asText("Dify error"));
            }
            return root;
        } catch (HttpStatusCodeException ex) {
            String msg = ex.getResponseBodyAsString();
            throw new IllegalStateException(msg.isBlank() ? ex.getMessage() : msg, ex);
        }
    }

    private AssistResponse parseAssistPayload(JsonNode payload, boolean fromDify, boolean hasImage, boolean hasData) {
        if (payload.has("answer")) {
            return parseAnswerAsAssist(payload.path("answer").asText(""), fromDify, hasImage, hasData);
        }
        return parseAssistResponse(payload, fromDify);
    }

    private AssistResponse parseAnswerAsAssist(String answer, boolean fromDify, boolean hasImage, boolean hasData) {
        String trimmed = answer != null ? answer.trim() : "";
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                JsonNode json = objectMapper.readTree(trimmed);
                if (json.isObject()) {
                    AssistResponse resp = parseAssistResponse(json, fromDify);
                    if (resp.getFeedback() != null && !resp.getFeedback().isBlank()) {
                        return resp;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        AssistResponse resp = new AssistResponse();
        resp.setFromDify(fromDify);
        resp.setType(hasImage ? "vision_correction" : (hasData ? "data_correction" : "text_assist"));
        resp.setFeedback(trimmed);
        resp.setMarks(List.of());
        return resp;
    }

    private EnvCheckResponse parseEnvPayload(JsonNode payload) {
        EnvCheckResponse resp = new EnvCheckResponse();
        resp.setFromDify(true);
        if (payload.has("answer")) {
            String answer = payload.path("answer").asText("").trim();
            if (answer.startsWith("{")) {
                try {
                    JsonNode json = objectMapper.readTree(answer);
                    resp.setLevel(normalizeEnvLevel(text(json, "level", "L0")));
                    resp.setSummary(text(json, "summary", "暂无异常"));
                    resp.setSuggestion(text(json, "suggestion", ""));
                    return resp;
                } catch (Exception ignored) {
                }
            }
            resp.setLevel(extractLevel(answer));
            resp.setSummary(answer);
            resp.setSuggestion("");
            return resp;
        }
        resp.setLevel(normalizeEnvLevel(text(payload, "level", "L0")));
        resp.setSummary(text(payload, "summary", "暂无异常"));
        resp.setSuggestion(text(payload, "suggestion", ""));
        return resp;
    }

    private String extractLevel(String text) {
        if (text == null) return "L0";
        for (String lv : List.of("L2", "L1", "L0")) {
            if (text.contains(lv)) return lv;
        }
        return "L0";
    }

    private String normalizeEnvLevel(String level) {
        if ("L3".equalsIgnoreCase(level)) {
            return "L2";
        }
        if ("L2".equalsIgnoreCase(level)) return "L2";
        if ("L1".equalsIgnoreCase(level)) return "L1";
        return "L0";
    }

    private AssistResponse parseAssistResponse(JsonNode outputs, boolean fromDify) {
        AssistResponse resp = new AssistResponse();
        resp.setFromDify(fromDify);
        resp.setType(text(outputs, "type", "text_assist"));
        resp.setFeedback(text(outputs, "feedback", text(outputs, "answer", "")));
        resp.setErrorType(text(outputs, "error_type", ""));
        resp.setDetail(text(outputs, "detail", ""));
        JsonNode marksNode = outputs.get("marks");
        if (marksNode != null && marksNode.isArray()) {
            resp.setMarks(objectMapper.convertValue(marksNode, new TypeReference<List<MarkDto>>() {}));
        } else {
            String marksJson = text(outputs, "marks_json", "");
            if (!marksJson.isBlank()) {
                try {
                    resp.setMarks(objectMapper.readValue(marksJson, new TypeReference<List<MarkDto>>() {}));
                } catch (Exception ignored) {
                    resp.setMarks(List.of());
                }
            } else {
                resp.setMarks(List.of());
            }
        }
        return resp;
    }

    private AssistResponse mockAssist(ExperimentConfig experiment, int stepId, boolean hasImage, boolean hasData) {
        AssistResponse resp = new AssistResponse();
        resp.setFromDify(false);
        if (experiment == null) {
            resp.setType(hasImage ? "vision_correction" : (hasData ? "data_correction" : "text_assist"));
            resp.setFeedback(hasImage
                    ? "已收到图片。当前 Dify 不可用，请稍后重试。"
                    : (hasData ? "已收到实验数据。当前 Dify 不可用，请稍后重试。" : "已收到您的问题。当前 Dify 不可用，请稍后重试。"));
            resp.setMarks(List.of());
            return resp;
        }
        StepConfig step = experiment.getSteps().get(String.valueOf(stepId > 0 ? stepId : 1));
        if (step == null) {
            step = experiment.getSteps().get("1");
        }
        AssistMock mock = step != null ? step.getAssistMock() : null;
        if (hasImage && mock != null) {
            resp.setType("vision_correction");
            resp.setErrorType(mock.getErrorType());
            resp.setDetail(mock.getDetail());
            List<MarkDto> jsonMarks = mock.getMarks() != null ? mock.getMarks() : List.of();
            resp.setFeedback(mock.getFeedback());
            resp.setMarks(jsonMarks);
        } else if (hasImage) {
            resp.setType("vision_correction");
            resp.setFeedback("已收到图片。当前 Dify 不可用，请稍后重试。");
            resp.setMarks(List.of());
        } else if (hasData && mock != null) {
            resp.setType("data_correction");
            resp.setErrorType(mock.getErrorType());
            resp.setDetail(mock.getDetail());
            resp.setFeedback(mock.getFeedback());
            resp.setMarks(List.of());
        } else if (hasData && step != null) {
            resp.setType("data_correction");
            resp.setFeedback("**" + step.getTitle() + "**\n\n" + step.getDesc()
                    + "\n\n请核对记录表中的读数、单位与有效数字；若装夹或仪表有疑问，可切换至需拍照的步骤上传实拍图。");
            resp.setMarks(List.of());
        } else if (step != null) {
            resp.setType("text_assist");
            resp.setFeedback("**" + step.getTitle() + "**\n\n" + step.getDesc()
                    + "\n\n请对照教程逐步操作；若仍不确定，可上传台面实拍图后再次求助。");
            resp.setMarks(List.of());
        } else {
            resp.setType("text_assist");
            resp.setFeedback("请描述具体问题，或上传实验台实拍图以便视觉纠错。");
            resp.setMarks(List.of());
        }
        return resp;
    }

    private EnvCheckResponse mockEnvCheck() {
        String[] levels = {"L0", "L0", "L0", "L1", "L2"};
        String level = levels[new Random().nextInt(levels.length)];
        EnvCheckResponse resp = new EnvCheckResponse();
        resp.setFromDify(false);
        resp.setLevel(level);
        switch (level) {
            case "L1" -> {
                resp.setSummary("台面存在轻微杂物或摆放偏移（示意）");
                resp.setSuggestion("整理台面，确保试样与仪表区域清晰可见");
            }
            case "L2" -> {
                resp.setSummary("检测到可能的违规操作或遮挡（示意）");
                resp.setSuggestion("严重告警：暂停操作，请教师或助教确认后再继续");
            }
            default -> {
                resp.setSummary("暂无异常");
                resp.setSuggestion("");
            }
        }
        return resp;
    }

    private String text(JsonNode node, String field, String defaultValue) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? defaultValue : v.asText(defaultValue);
    }
}
