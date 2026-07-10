package com.wuxiaozhi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiaozhi.config.DifyProperties;
import com.wuxiaozhi.dto.AssistResponse;
import com.wuxiaozhi.dto.EnvCheckResponse;
import com.wuxiaozhi.dto.experiment.ExperimentConfig;
import com.wuxiaozhi.dto.experiment.MarkDto;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Service
public class DifyService {

    private static final Logger log = LoggerFactory.getLogger(DifyService.class);
    private static final int STATUS_CHECK_TIMEOUT_MS = 1_500;
    private static final long STATUS_CACHE_TTL_MS = 30_000;

    private final DifyProperties difyProperties;
    private final ObjectMapper objectMapper;
    private final FileStorageService fileStorageService;
    private final RestTemplate restTemplate = new RestTemplate();
    private volatile Map<String, Object> cachedStatus;
    private volatile long cachedStatusAt;

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
                log.warn("Dify assist failed: {}", e.getMessage());
            }
        }
        return unavailableAssist(hasImage, hasData);
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
                log.warn("Dify stream failed: {}", e.getMessage());
            }
        }
        boolean hasData = hasDataAssist(inputs);
        AssistResponse unavailable = unavailableAssist(hasImage, hasData);
        streamMockFeedback(unavailable.getFeedback(), onDelta);
        if (onAnswerComplete != null) {
            onAnswerComplete.run();
        }
        return unavailable;
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
                log.warn("Dify env-check failed, returning unavailable: {}", e.getMessage());
            }
        }
        return unavailableEnvCheck();
    }

    public boolean isConfigured() {
        return difyProperties.isConfigured();
    }

    public String getAppMode() {
        return difyProperties.getAppMode();
    }

    public Map<String, Object> status() {
        Map<String, Object> cached = cachedStatus;
        if (cached != null && System.currentTimeMillis() - cachedStatusAt < STATUS_CACHE_TTL_MS) {
            return cached;
        }

        boolean configured = difyProperties.isConfigured();
        Map<String, Boolean> workflowConfigured = new LinkedHashMap<>();
        Map<String, Map<String, Object>> workflowStatuses = new LinkedHashMap<>();
        List<String> workflowKeys = List.of("text-assist", "env-check");
        Map<String, CompletableFuture<Map<String, Object>>> statusFutures = new LinkedHashMap<>();
        for (String key : workflowKeys) {
            boolean canRun = difyProperties.canRun(key);
            workflowConfigured.put(key, canRun);
            statusFutures.put(key, CompletableFuture.supplyAsync(() -> checkWorkflowStatus(key)));
        }
        for (String key : workflowKeys) {
            try {
                workflowStatuses.put(key, statusFutures.get(key).get(STATUS_CHECK_TIMEOUT_MS + 300L, TimeUnit.MILLISECONDS));
            } catch (Exception e) {
                Map<String, Object> unavailable = new LinkedHashMap<>();
                unavailable.put("configured", workflowConfigured.get(key));
                unavailable.put("reachable", false);
                unavailable.put("available", false);
                unavailable.put("reason", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                workflowStatuses.put(key, unavailable);
            }
        }

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("configured", configured);
        status.put("appMode", difyProperties.getAppMode());
        status.put("baseUrl", difyProperties.getBaseUrl());
        status.put("configFile", "config/dify.env");
        status.put("workflows", workflowConfigured);
        status.put("workflowStatuses", workflowStatuses);
        boolean anyReachable = workflowStatuses.values().stream()
                .anyMatch(item -> Boolean.TRUE.equals(item.get("reachable")));
        boolean anyAvailable = workflowStatuses.values().stream()
                .anyMatch(item -> Boolean.TRUE.equals(item.get("available")));
        status.put("reachable", anyReachable);
        status.put("available", anyAvailable);
        status.put("reason", anyAvailable ? "" : workflowStatuses.values().stream()
                .map(item -> String.valueOf(item.getOrDefault("reason", "")))
                .filter(reason -> !reason.isBlank())
                .findFirst()
                .orElse("Dify service is unavailable"));
        cachedStatus = status;
        cachedStatusAt = System.currentTimeMillis();
        return status;
    }

    private Map<String, Object> checkWorkflowStatus(String workflowKey) {
        Map<String, Object> status = new LinkedHashMap<>();
        boolean configured = difyProperties.canRun(workflowKey);
        status.put("configured", configured);
        String baseUrl = difyProperties.getBaseUrl() != null ? difyProperties.getBaseUrl().trim() : "";
        if (!configured) {
            status.put("reachable", false);
            status.put("available", false);
            status.put("reason", "Dify API key is not configured for " + workflowKey);
            return status;
        }
        if (baseUrl.isBlank()) {
            status.put("reachable", false);
            status.put("available", false);
            status.put("reason", "Dify base URL is not configured");
            return status;
        }
        String apiKey = difyProperties.resolveApiKey(workflowKey);
        if (apiKey.isBlank()) {
            status.put("reachable", false);
            status.put("available", false);
            status.put("reason", "Dify API key is not configured for " + workflowKey);
            return status;
        }

        String url = baseUrl.replaceAll("/$", "") + "/parameters";
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(STATUS_CHECK_TIMEOUT_MS);
            conn.setReadTimeout(STATUS_CHECK_TIMEOUT_MS);
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            conn.disconnect();
            boolean ok = code >= 200 && code < 300;
            status.put("reachable", ok);
            status.put("available", ok);
            status.put("reason", ok ? "" : "Dify responded with HTTP " + code);
            return status;
        } catch (Exception e) {
            status.put("reachable", false);
            status.put("available", false);
            status.put("reason", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            return status;
        }
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

    private AssistResponse unavailableAssist(boolean hasImage, boolean hasData) {
        AssistResponse resp = new AssistResponse();
        resp.setFromDify(false);
        resp.setType(hasImage ? "vision_correction" : (hasData ? "data_correction" : "text_assist"));
        resp.setFeedback("**暂时无法连接Dify服务**\n\n我现在连接不上 Dify 服务，因此不能可靠回答这个问题。为避免给出不准确的信息，请稍后再试。\n\n如果多次出现，请联系Dify管理员检查 Dify 服务配置。");
        resp.setMarks(List.of());
        return resp;
    }

    private EnvCheckResponse unavailableEnvCheck() {
        EnvCheckResponse resp = new EnvCheckResponse();
        resp.setFromDify(false);
        resp.setLevel("NA");
        resp.setSummary("**暂时无法连接Dify服务**\n\n我现在连接不上 Dify 服务，因此不能完成本次安全巡检。为避免给出不准确的等级判断，请稍后再试。\n\n如果多次出现，请联系Dify管理员检查 Dify 服务配置。");
        resp.setSuggestion("");
        return resp;
    }

    private String text(JsonNode node, String field, String defaultValue) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? defaultValue : v.asText(defaultValue);
    }
}
