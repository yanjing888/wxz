package com.wuxiaozhi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiaozhi.config.DifyProperties;
import com.wuxiaozhi.dto.AssistResponse;
import com.wuxiaozhi.dto.AiToolInvokeResponse;
import com.wuxiaozhi.dto.EnvCheckResponse;
import com.wuxiaozhi.dto.experiment.ExperimentConfig;
import com.wuxiaozhi.dto.experiment.MarkDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

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
    private static final int STATUS_CHECK_TIMEOUT_MS = 5_000;
    private static final long STATUS_CACHE_TTL_MS = 30_000;
    private static final long MAX_AUDIO_BYTES = 30L * 1024 * 1024;

    private final DifyProperties difyProperties;
    private final ObjectMapper objectMapper;
    private final FileStorageService fileStorageService;
    private final RestTemplate restTemplate = buildRestTemplate();
    private volatile Map<String, Object> cachedStatus;
    private volatile long cachedStatusAt;

    public DifyService(DifyProperties difyProperties, ObjectMapper objectMapper,
                       FileStorageService fileStorageService) {
        this.difyProperties = difyProperties;
        this.objectMapper = objectMapper;
        this.fileStorageService = fileStorageService;
    }

    private static RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30_000);
        factory.setReadTimeout(180_000);
        return new RestTemplate(factory);
    }

    public AssistResponse assist(String workflowKey, Map<String, Object> inputs, String userId,
                                 ExperimentConfig experiment, int stepId, boolean hasImage,
                                 String imageUrl) {
        return assist(workflowKey, inputs, userId, experiment, stepId, hasImage, imageUrl, false);
    }

    public AssistResponse assist(String workflowKey, Map<String, Object> inputs, String userId,
                                 ExperimentConfig experiment, int stepId, boolean hasImage,
                                 String imageUrl, boolean hasData) {
        if (canCall(workflowKey)) {
            try {
                JsonNode payload = difyProperties.isChatMode(workflowKey)
                        ? runChat(workflowKey, inputs, userId, buildAssistQuery(inputs, hasImage), imageUrl)
                        : runWorkflow(workflowKey, inputs, userId);
                return parseAssistPayload(payload, true, hasImage);
            } catch (Throwable e) {
                log.warn("Dify assist failed: {}", e.getMessage());
            }
        }
        return unavailableAssist(hasImage);
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
                String query = buildAssistQuery(inputs, hasImage);
                Map<String, Object> body = buildChatBody(workflowKey, inputs, userId, query, imageUrl);
                body.put("response_mode", "streaming");
                String apiKey = difyProperties.resolveApiKey(workflowKey);
                String url = difyProperties.getBaseUrl().replaceAll("/$", "") + "/chat-messages";
                StreamCapture capture = new StreamCapture();
                AtomicBoolean answerEnded = new AtomicBoolean(false);
                streamChatSse(url, apiKey, body, node -> {
                    notifyAnswerComplete(node, answerEnded, onAnswerComplete);
                    absorbStreamMeta(node, capture.marks, onMarks);
                    absorbStreamAnswer(node, capture, onDelta);
                    absorbStreamFailure(node, capture);
                    String delta = extractStreamDelta(node);
                    if (!delta.isEmpty()) {
                        capture.full.append(delta);
                        onDelta.accept(delta);
                    }
                });
                finalizeEmptyStreamAnswer(capture, onDelta);
                if (!answerEnded.get() && onAnswerComplete != null && capture.full.length() > 0) {
                    onAnswerComplete.run();
                }
                log.info("Dify stream ok, workflowKey={}, length={}, marks={}, workflowError={}",
                        workflowKey, capture.full.length(), capture.marks.size(),
                        capture.workflowError.isBlank() ? "none" : capture.workflowError);
                return buildStreamAssistResponse(capture.full.toString(), capture.marks, hasImage);
            } catch (Throwable e) {
                log.warn("Dify stream failed: {}", e.getMessage());
            }
        }
        AssistResponse unavailable = unavailableAssist(hasImage);
        streamMockFeedback(unavailable.getFeedback(), onDelta);
        if (onAnswerComplete != null) {
            onAnswerComplete.run();
        }
        return unavailable;
    }

    public AiToolInvokeResponse invokeTool(String workflowKey, Map<String, Object> inputs, String userId) {
        return invokeTool(workflowKey, inputs, userId, null);
    }

    public AiToolInvokeResponse invokeTool(String workflowKey, Map<String, Object> inputs, String userId,
                                           String imageUrl) {
        boolean hasImage = imageUrl != null && !imageUrl.isBlank();
        if (!canCall(workflowKey)) {
            AiToolInvokeResponse resp = new AiToolInvokeResponse();
            resp.setFromDify(false);
            resp.setText(unavailableAssist(hasImage).getFeedback());
            return resp;
        }
        try {
            if (difyProperties.isChatMode(workflowKey)) {
                AssistResponse assist = assist(workflowKey, inputs, userId, null, 0, hasImage, imageUrl);
                return toInvokeResponse(assist.getFeedback(), assist.isFromDify());
            }
            JsonNode outputs = runWorkflow(workflowKey, buildToolWorkflowInputs(workflowKey, inputs, imageUrl, userId), userId);
            AssistResponse parsed = parseAssistResponse(outputs, true);
            String text = parsed.getFeedback() != null && !parsed.getFeedback().isBlank()
                    ? parsed.getFeedback()
                    : outputs.toString();
            AiToolInvokeResponse resp = toInvokeResponse(text, true);
            mergeStructuredData(resp, text);
            return resp;
        } catch (Throwable e) {
            log.warn("Dify invoke failed, workflowKey={}: {}", workflowKey, e.getMessage());
            AiToolInvokeResponse resp = new AiToolInvokeResponse();
            resp.setFromDify(false);
            resp.setText(unavailableAssist(false).getFeedback());
            return resp;
        }
    }

    private Map<String, Object> buildToolWorkflowInputs(String workflowKey, Map<String, Object> inputs,
                                                        String imageUrl, String userId) {
        Map<String, Object> workflowInputs = new LinkedHashMap<>(inputs);
        if (imageUrl != null && !imageUrl.isBlank()) {
            String apiKey = difyProperties.resolveApiKey(workflowKey);
            Map<String, Object> fileRef = difyFileRef(uploadImageToDify(imageUrl, userId, apiKey));
            workflowInputs.put("image", fileRef);
            workflowInputs.putIfAbsent("image_url", imageUrl);
        }
        return workflowInputs;
    }

    private AiToolInvokeResponse toInvokeResponse(String text, boolean fromDify) {
        AiToolInvokeResponse resp = new AiToolInvokeResponse();
        resp.setText(text != null ? text : "");
        resp.setFromDify(fromDify);
        mergeStructuredData(resp, resp.getText());
        return resp;
    }

    private void mergeStructuredData(AiToolInvokeResponse resp, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        JsonNode json = extractJsonObject(text);
        if (json == null || !json.isObject()) {
            return;
        }
        try {
            resp.setData(objectMapper.convertValue(json, new TypeReference<Map<String, Object>>() {}));
        } catch (Exception ignored) {
            return;
        }
        applyReviewFields(resp, json);
    }

    private JsonNode extractJsonObject(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        String candidate = trimmed;
        if (candidate.startsWith("```")) {
            int start = candidate.indexOf('\n');
            int end = candidate.lastIndexOf("```");
            if (start > 0 && end > start) {
                candidate = candidate.substring(start + 1, end).trim();
            }
        }
        try {
            JsonNode json = objectMapper.readTree(candidate);
            if (json.isObject()) {
                return json;
            }
        } catch (Exception ignored) {
        }
        int from = candidate.indexOf('{');
        int to = candidate.lastIndexOf('}');
        if (from >= 0 && to > from) {
            try {
                JsonNode json = objectMapper.readTree(candidate.substring(from, to + 1));
                if (json.isObject()) {
                    return json;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private void applyReviewFields(AiToolInvokeResponse resp, JsonNode json) {
        Double score = firstNumber(json, "score", "score10", "suggestedScore");
        if (score == null) {
            Double score100 = firstNumber(json, "score100");
            if (score100 != null) {
                score = Math.round(score100 / 10.0 * 10.0) / 10.0;
            }
        }
        if (score != null) {
            resp.setScore(score);
        }
        Double maxScore = firstNumber(json, "maxScore", "scoreMax");
        resp.setMaxScore(maxScore != null ? maxScore : 10.0);
        String comment = firstText(json, "comment", "评语", "feedback", "summary");
        if (comment != null && !comment.isBlank()) {
            resp.setComment(comment);
        }
        String band = firstText(json, "gradeBand", "band");
        if (band != null && !band.isBlank()) {
            resp.setGradeBand(band);
        }
        if (json.has("needsTeacherConfirm")) {
            resp.setNeedsTeacherConfirm(json.path("needsTeacherConfirm").asBoolean(true));
        } else if (resp.getScore() != null) {
            resp.setNeedsTeacherConfirm(true);
        }
    }

    private Double firstNumber(JsonNode json, String... keys) {
        for (String key : keys) {
            JsonNode node = json.get(key);
            if (node != null && node.isNumber()) {
                return node.asDouble();
            }
            if (node != null && node.isTextual()) {
                try {
                    return Double.parseDouble(node.asText().trim());
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private String firstText(JsonNode json, String... keys) {
        for (String key : keys) {
            String value = json.path(key).asText("").trim();
            if (!value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /** Dify 正文流结束后仍会跑知识库等节点；Answer 节点完成或 message_end 时通知前端收起光标。 */
    private void notifyAnswerComplete(JsonNode node, AtomicBoolean answerEnded, Runnable onAnswerComplete) {
        if (onAnswerComplete == null || answerEnded.get()) {
            return;
        }
        String event = node.path("event").asText("");
        if ("message_end".equals(event) || "agent_message_end".equals(event)) {
            answerEnded.set(true);
            onAnswerComplete.run();
            return;
        }
        if ("node_finished".equals(event)) {
            JsonNode data = node.path("data");
            if ("answer".equals(data.path("node_type").asText(""))
                    && "succeeded".equalsIgnoreCase(data.path("status").asText(""))) {
                answerEnded.set(true);
                onAnswerComplete.run();
            }
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
        } else if ("code".equals(nodeType) && title.contains("视觉 JSON")) {
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

    private AssistResponse buildStreamAssistResponse(String answer, List<MarkDto> streamMarks, boolean hasImage) {
        AssistResponse resp = new AssistResponse();
        resp.setFromDify(true);
        if (!streamMarks.isEmpty() || hasImage) {
            resp.setType("vision_correction");
        } else {
            resp.setType("text_assist");
        }
        resp.setFeedback(answer != null ? answer.trim() : "");
        resp.setMarks(streamMarks);
        return resp;
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
        if ("message_replace".equals(event)) {
            return node.path("answer").asText("");
        }
        if ("text_chunk".equals(event)) {
            return node.path("data").path("text").asText("");
        }
        return "";
    }

    private void absorbStreamFailure(JsonNode node, StreamCapture capture) {
        String event = node.path("event").asText("");
        if ("error".equals(event)) {
            capture.workflowError = node.path("message").asText("Dify stream error");
            return;
        }
        if ("workflow_finished".equals(event)) {
            JsonNode data = node.path("data");
            if ("failed".equalsIgnoreCase(data.path("status").asText(""))) {
                String err = data.path("error").asText("");
                if (err.isBlank()) {
                    err = data.path("message").asText("");
                }
                if (!err.isBlank()) {
                    capture.workflowError = err;
                }
            }
            return;
        }
        if (!"node_finished".equals(event)) {
            return;
        }
        JsonNode data = node.path("data");
        if ("failed".equalsIgnoreCase(data.path("status").asText(""))) {
            String err = data.path("error").asText("");
            if (!err.isBlank()) {
                capture.workflowError = err;
            }
        }
    }

    /** Chatflow 的 Answer 节点可能只在 node_finished 输出正文，而不发 message 流。 */
    private void absorbStreamAnswer(JsonNode node, StreamCapture capture, Consumer<String> onDelta) {
        if (!"node_finished".equals(node.path("event").asText(""))) {
            return;
        }
        JsonNode data = node.path("data");
        if (!"succeeded".equalsIgnoreCase(data.path("status").asText(""))) {
            return;
        }
        if (!"answer".equals(data.path("node_type").asText(""))) {
            return;
        }
        appendStreamAnswerIfMissing(capture, extractAnswerFromOutputs(data.path("outputs")), onDelta);
    }

    private void finalizeEmptyStreamAnswer(StreamCapture capture, Consumer<String> onDelta) {
        if (capture.full.length() > 0) {
            return;
        }
        if (capture.workflowError.isBlank()) {
            return;
        }
        String feedback = formatStreamWorkflowError(capture.workflowError);
        capture.full.append(feedback);
        onDelta.accept(feedback);
    }

    private void appendStreamAnswerIfMissing(StreamCapture capture, String answer, Consumer<String> onDelta) {
        if (answer == null || answer.isBlank() || capture.full.length() > 0) {
            return;
        }
        capture.full.append(answer);
        onDelta.accept(answer);
    }

    private String extractAnswerFromOutputs(JsonNode outputs) {
        if (outputs == null || outputs.isMissingNode() || outputs.isNull()) {
            return "";
        }
        for (String key : List.of("answer", "text", "output", "result")) {
            String value = outputs.path(key).asText("");
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    String formatStreamWorkflowError(String rawError) {
        String err = rawError != null ? rawError.trim() : "";
        if (err.contains("127.0.0.1:8082") || err.contains("WXZ_BACKEND_BASE_URL")) {
            log.warn("AI workflow could not load experiment config: {}", err);
            return "**AI 助教配置异常**\n\n"
                    + "智能助教暂时无法读取当前实验配置，因此本次无法可靠回复。请稍后再试。\n\n"
                    + "如果多次出现，请联系管理员检查 AI 助教服务配置。";
        }
        if (err.contains("/api/public/experiments/")) {
            log.warn("AI workflow could not load experiment manifest: {}", err);
            return "**AI 助教配置异常**\n\n"
                    + "智能助教暂时无法读取当前实验信息，因此本次无法可靠回复。请稍后再试。\n\n"
                    + "如果多次出现，请联系管理员检查 AI 助教服务配置。";
        }
        log.warn("AI workflow failed: {}", err);
        return "**AI 助教暂时不可用**\n\n"
                + "智能助教服务开小差了，本次无法可靠回复。请稍后再试。";
    }

    private static final class StreamCapture {
        private final StringBuilder full = new StringBuilder();
        private final List<MarkDto> marks = new ArrayList<>();
        private String workflowError = "";
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

    public String transcribeAudio(MultipartFile file, String userId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请先录制一段语音");
        }
        if (file.getSize() > MAX_AUDIO_BYTES) {
            throw new IllegalArgumentException("语音文件超过 30MB 限制，请缩短录音后重试");
        }
        if (!difyProperties.canRun("voice-input")) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "语音识别服务暂时不可用，请联系管理员检查 AI 服务配置"
            );
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("读取录音失败，请重新录制");
        }
        if (bytes.length == 0) {
            throw new IllegalArgumentException("录音内容为空，请重新录制");
        }

        String uploadName = audioUploadFilename(file);
        String url = difyProperties.getBaseUrl().replaceAll("/$", "") + "/audio-to-text";

        ByteArrayResource audioResource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return uploadName;
            }
        };
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(resolveAudioMediaType(uploadName));

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new HttpEntity<>(audioResource, fileHeaders));
        form.add("user", userId != null && !userId.isBlank() ? userId : "anonymous");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(difyProperties.resolveApiKey("voice-input"));

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(form, headers);
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.POST, entity, JsonNode.class);
            JsonNode root = response.getBody();
            String text = root != null ? root.path("text").asText("").trim() : "";
            if (text.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "语音识别没有返回文字，请检查浏览器麦克风权限、输入设备是否选对，并尽量靠近麦克风后重试"
                );
            }
            return text;
        } catch (HttpStatusCodeException ex) {
            String msg = ex.getResponseBodyAsString();
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    formatAudioTranscriptionError(ex, msg),
                    ex
            );
        }
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
        List<String> workflowKeys = List.of(
                "text-assist",
                "env-check",
                "report-assist",
                "report-review",
                "lab-recap",
                "think-questions",
                "instrument-reading",
                "data-doctor"
        );
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

    private String buildAssistQuery(Map<String, Object> inputs, boolean hasImage) {
        Object q = inputs.get("query");
        if (q == null) q = inputs.get("user_query");
        String userQuery = q != null ? String.valueOf(q).trim() : "";
        if (!userQuery.isBlank()) {
            return userQuery;
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

    public String uploadDocument(byte[] bytes, String filename, String userId, String workflowKey) {
        String apiKey = difyProperties.resolveApiKey(workflowKey);
        return uploadBytesToDify(bytes, filename, userId, apiKey);
    }

    public Map<String, Object> documentFileRef(String uploadFileId) {
        Map<String, Object> fileRef = new LinkedHashMap<>();
        fileRef.put("type", "document");
        fileRef.put("transfer_method", "local_file");
        fileRef.put("upload_file_id", uploadFileId);
        return fileRef;
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
        return uploadBytesToDify(bytes, uploadName, userId, apiKey);
    }

    private String uploadBytesToDify(byte[] bytes, String filename, String userId, String apiKey) {
        String uploadUrl = difyProperties.getBaseUrl().replaceAll("/$", "") + "/files/upload";
        String uploadName = filename == null || filename.isBlank() ? "wxz-upload.bin" : filename;
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

    private String audioUploadFilename(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name != null) {
            String lower = name.toLowerCase(Locale.ROOT);
            for (String ext : List.of(".wav", ".mp3", ".m4a", ".amr", ".mpga")) {
                if (lower.endsWith(ext)) {
                    return "wxz-voice" + ext;
                }
            }
        }
        String type = file.getContentType() != null ? file.getContentType().toLowerCase(Locale.ROOT) : "";
        if (type.contains("mpeg") || type.contains("mp3")) return "wxz-voice.mp3";
        if (type.contains("mp4") || type.contains("m4a")) return "wxz-voice.m4a";
        if (type.contains("amr")) return "wxz-voice.amr";
        if (type.contains("mpga")) return "wxz-voice.mpga";
        return "wxz-voice.wav";
    }

    private MediaType resolveAudioMediaType(String uploadName) {
        String lower = uploadName != null ? uploadName.toLowerCase(Locale.ROOT) : "";
        if (lower.endsWith(".mp3") || lower.endsWith(".mpga")) return MediaType.parseMediaType("audio/mp3");
        if (lower.endsWith(".m4a")) return MediaType.parseMediaType("audio/m4a");
        if (lower.endsWith(".amr")) return MediaType.parseMediaType("audio/amr");
        return MediaType.parseMediaType("audio/wav");
    }

    private String formatAudioTranscriptionError(HttpStatusCodeException ex, String rawBody) {
        String body = rawBody != null ? rawBody.trim() : "";
        if (ex.getStatusCode().value() == 404 || body.contains("资源不存在") || body.contains("not_found")) {
            log.warn("Voice transcription endpoint unavailable: {}", body.isBlank() ? ex.getMessage() : body);
            return "语音识别服务暂时不可用，请联系管理员检查 AI 服务配置";
        }
        if (body.contains("unsupported_audio_type")) {
            return "语音识别不支持当前录音格式，请使用 WAV、MP3、M4A、AMR 或 MPGA";
        }
        if (body.contains("unauthorized") || ex.getStatusCode().value() == 401) {
            log.warn("Voice transcription authorization failed: {}", body.isBlank() ? ex.getMessage() : body);
            return "语音识别服务暂时不可用，请联系管理员检查 AI 服务配置";
        }
        return body.isBlank() ? ex.getMessage() : body;
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

    private AssistResponse parseAssistPayload(JsonNode payload, boolean fromDify, boolean hasImage) {
        if (payload.has("answer")) {
            return parseAnswerAsAssist(payload.path("answer").asText(""), fromDify, hasImage);
        }
        return parseAssistResponse(payload, fromDify);
    }

    private AssistResponse parseAnswerAsAssist(String answer, boolean fromDify, boolean hasImage) {
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
        resp.setType(hasImage ? "vision_correction" : "text_assist");
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

    private AssistResponse unavailableAssist(boolean hasImage) {
        AssistResponse resp = new AssistResponse();
        resp.setFromDify(false);
        resp.setType(hasImage ? "vision_correction" : "text_assist");
        resp.setFeedback("**AI 助教暂时不可用**\n\n我现在连接不上智能助教服务，因此不能可靠回答这个问题。为避免给出不准确的信息，请稍后再试。\n\n如果多次出现，请联系管理员检查 AI 助教服务配置。");
        resp.setMarks(List.of());
        return resp;
    }

    private EnvCheckResponse unavailableEnvCheck() {
        EnvCheckResponse resp = new EnvCheckResponse();
        resp.setFromDify(false);
        resp.setLevel("NA");
        resp.setSummary("**安全巡检暂时不可用**\n\n我现在连接不上智能安全巡检服务，因此不能完成本次安全巡检。为避免给出不准确的等级判断，请稍后再试。\n\n如果多次出现，请联系管理员检查 AI 助教服务配置。");
        resp.setSuggestion("");
        return resp;
    }

    private String text(JsonNode node, String field, String defaultValue) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? defaultValue : v.asText(defaultValue);
    }
}
