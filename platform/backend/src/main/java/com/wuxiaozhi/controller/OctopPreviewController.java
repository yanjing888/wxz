package com.wuxiaozhi.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiaozhi.security.AuthSupport;
import com.wuxiaozhi.service.ExpertHistoryService;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@ConditionalOnProperty(name = "octop.preview.enabled", havingValue = "true")
public class OctopPreviewController {
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .version(HttpClient.Version.HTTP_1_1).build();
    private final ObjectMapper json;
    private final ExpertHistoryService history;
    private final String baseUrl;
    private final String username;
    private final String password;
    private final String expertId;
    private final String gatewayKey;
    private String serviceToken;
    private Instant renewTokenAt = Instant.EPOCH;

    public OctopPreviewController(
            ObjectMapper json, ExpertHistoryService history,
            @Value("${octop.preview.base-url}") String baseUrl,
            @Value("${octop.preview.username:}") String username,
            @Value("${octop.preview.password:}") String password,
            @Value("${octop.preview.expert-id}") String expertId,
            @Value("${octop.preview.gateway-key:}") String gatewayKey) {
        this.json = json;
        this.history = history;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.username = username;
        this.password = password;
        this.expertId = expertId;
        this.gatewayKey = gatewayKey;
    }

    @GetMapping("/api/ai/octop-preview")
    public Map<String, Boolean> availability() {
        return Map.of("enabled", true);
    }

    @GetMapping("/api/ai/octop-preview/conversations")
    public List<Map<String, Object>> conversations(Authentication authentication) {
        return history.list(AuthSupport.currentUserId(authentication), expertId);
    }

    @GetMapping("/api/ai/octop-preview/conversations/{id}")
    public Map<String, Object> conversation(@PathVariable UUID id, Authentication authentication) {
        return history.get(id.toString(), AuthSupport.currentUserId(authentication), expertId);
    }

    @PostMapping(value = "/api/ai/octop-preview/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody PreviewQuestion question, Authentication authentication) {
        long userId = AuthSupport.currentUserId(authentication);
        String sessionKey = "wuxiaozhi-preview:" + expertId + ":" + userId + ":" + question.conversationId();
        SseEmitter emitter = new SseEmitter(120_000L);
        String mode = question.conversationMode() == null ? "craft" : question.conversationMode();
        String upstreamThreadId = history.begin(question.conversationId().toString(), userId, expertId, question.message().strip(), mode);
        StreamRelay relay = new StreamRelay(emitter, question.message().strip(), sessionKey, mode, userId, question.conversationId(), upstreamThreadId);
        emitter.onCompletion(relay::close);
        emitter.onTimeout(relay::timeout);
        emitter.onError(error -> relay.close());
        CompletableFuture.runAsync(() -> {
            try {
                connect(relay);
            } catch (ResponseStatusException e) {
                relay.finish(e.getReason());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                relay.finish("Octop 调用已中断");
            } catch (IOException | RuntimeException e) {
                relay.finish("无法连接 Octop 对话服务");
            }
        });
        return emitter;
    }

    private void connect(StreamRelay relay) throws IOException, InterruptedException {
        if (!gatewayKey.isBlank()) {
            if (relay.closed.get()) return;
            URI target = UriComponentsBuilder.fromUriString(baseUrl)
                    .scheme(baseUrl.startsWith("https:") ? "wss" : "ws")
                    .pathSegment("v1", "experts", expertId, "stream").build().encode().toUri();
            http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(8))
                    .header("Authorization", "Bearer " + gatewayKey)
                    .header("X-Octop-App", "wuxiaozhi")
                    .buildAsync(target, relay).join();
            return;
        }

        String token = loginToken();
        try {
            openSocket(relay, token);
        } catch (CompletionException e) {
            if (!authenticationRejected(e)) throw e;
            if (relay.closed.get()) return;

            String refreshed = renewRejectedToken(token);
            try {
                openSocket(relay, refreshed);
            } catch (CompletionException retryError) {
                if (!authenticationRejected(retryError)) throw retryError;
                invalidateToken(refreshed);
                throw upstreamError("Octop 拒绝了服务账号访问，请检查专家权限");
            }
        }
    }

    private void openSocket(StreamRelay relay, String token) {
        if (relay.closed.get()) return;
        URI target = UriComponentsBuilder.fromUriString(baseUrl)
                .scheme(baseUrl.startsWith("https:") ? "wss" : "ws")
                .pathSegment("api", "agents", expertId, "chat", "ws")
                .queryParam("token", token).build().encode().toUri();
        http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(8))
                .buildAsync(target, relay).join();
    }

    private static boolean authenticationRejected(CompletionException error) {
        // Octop rejects invalid JWTs before accepting the WebSocket. Uvicorn
        // reports that rejection as HTTP 403, before onOpen can send a prompt.
        if (!(error.getCause() instanceof WebSocketHandshakeException handshake)) return false;
        int status = handshake.getResponse().statusCode();
        return status == 401 || status == 403;
    }

    private synchronized String renewRejectedToken(String rejected) throws IOException, InterruptedException {
        invalidateToken(rejected);
        return loginToken();
    }

    private synchronized void invalidateToken(String rejected) {
        if (rejected.equals(serviceToken)) {
            serviceToken = null;
            renewTokenAt = Instant.EPOCH;
        }
    }

    private final class StreamRelay implements WebSocket.Listener {
        private final SseEmitter emitter;
        private final String prompt;
        private final String sessionKey;
        private final String mode;
        private final long userId;
        private final UUID conversationId;
        private final String upstreamThreadId;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final StringBuilder frame = new StringBuilder();
        private final Map<String, Integer> tools = new HashMap<>();
        private final Map<Integer, Integer> toolIndexes = new HashMap<>();
        private int toolCount;
        private volatile WebSocket socket;
        private volatile String threadId;
        private boolean reasoning;
        private volatile boolean turnStarted;
        private int reasoningBlock;

        private StreamRelay(SseEmitter emitter, String prompt, String sessionKey, String mode, long userId, UUID conversationId, String upstreamThreadId) {
            this.emitter = emitter;
            this.prompt = prompt;
            this.sessionKey = sessionKey;
            this.mode = mode;
            this.userId = userId;
            this.conversationId = conversationId;
            this.upstreamThreadId = upstreamThreadId;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            socket = webSocket;
            if (closed.get()) {
                webSocket.abort();
                return;
            }
            try {
                String body = json.writeValueAsString(!gatewayKey.isBlank()
                        ? Map.of("type", "user_turn", "text", prompt, "user_id", Long.toString(userId),
                                "conversation_id", conversationId.toString(), "conversation_mode", mode,
                                "thread_id", upstreamThreadId)
                        : Map.of(
                        "type", "user_turn", "text", prompt,
                        "session_key", sessionKey, "delivery", "follow_up",
                        "conversation_mode", mode, "reasoning_mode", "enabled"));
                webSocket.sendText(body, true).exceptionally(error -> {
                    finish("无法发送 Octop 问题");
                    return null;
                });
                webSocket.request(1);
            } catch (IOException e) {
                finish("无法发送 Octop 问题");
            }
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            try {
                frame.append(data);
                if (last) {
                    forward(json.readTree(frame.toString()));
                    frame.setLength(0);
                }
            } catch (IOException | IllegalStateException e) {
                finish("Octop 对话连接已中断");
            }
            if (!closed.get()) webSocket.request(1);
            return null;
        }

        private synchronized void forward(JsonNode event) throws IOException {
            String id = event.path("thread_id").asText();
            if (!id.isBlank()) threadId = id;
            String type = event.path("type").asText();
            // Octop can broadcast the preceding turn's remaining frames when
            // a follow-up subscribes to the same thread while that turn is ending.
            if ("steer.ack".equals(type) && "rejected".equals(event.path("status").asText())) {
                String reason = event.path("reason").asText();
                finish("hitl_paused".equals(reason)
                        ? "上一轮正在等待工具审批，请先在 Octop 中处理"
                        : "上一轮还在运行，请等它结束后再发送");
                return;
            }
            if (!turnStarted && !"turn.started".equals(type) && !"error".equals(type)
                    && !"hitl_required".equals(type)) return;

            if ("token".equals(type) || "delta".equals(type) || "tool_call_chunk".equals(type)
                    || "tool_result".equals(type)) reasoning = false;
            switch (type) {
                case "turn.started" -> turnStarted = true;
                case "reasoning" -> {
                    String text = event.path("content").asText();
                    if (!text.isEmpty() && (reasoning || !text.isBlank())) {
                        if (!reasoning) reasoningBlock++;
                        reasoning = true;
                        sendStatus(Map.of("kind", "thinking", "number", reasoningBlock, "text", text));
                    }
                }
                case "tool_call_chunk" -> forwardToolCall(event);
                case "tool_result" -> {
                    for (JsonNode message : event.path("messages")) {
                        forwardToolResult(message);
                    }
                }
                case "token", "delta" -> {
                    String text = event.path("content").asText(event.path("text").asText());
                    if (!text.isEmpty()) {
                        emitter.send(SseEmitter.event().name("chunk").data(Map.of("text", text)));
                    }
                }
                case "hitl_required" -> finish("Octop 专家需要审批，请在 Octop 中处理");
                case "error" -> finish("Octop 专家处理失败");
                case "turn.ended" -> {
                    if (event.path("hitl_paused").asBoolean()) {
                        finish("Octop 专家需要审批，请在 Octop 中处理");
                    } else {
                        finish(event.path("failed").asBoolean() ? "Octop 未能完成本轮回复" : null);
                    }
                }
                default -> { }
            }
        }

        private void forwardToolCall(JsonNode event) throws IOException {
            String id = event.path("id").asText("").strip();
            int index = event.path("index").asInt(-1);
            String label = event.path("display_name").asText("").strip();
            if (label.isBlank()) label = event.path("name").asText("").strip();
            Integer number = id.isBlank() ? null : tools.get(id);
            if (number == null && index >= 0) number = toolIndexes.get(index);

            boolean started = number == null;
            if (started) {
                if (id.isBlank() && (index < 0 || label.isBlank())) return;
                number = ++toolCount;
            }
            if (!id.isBlank()) tools.put(id, number);
            if (index >= 0) toolIndexes.put(index, number);
            if (started || !label.isBlank()) {
                toolStatus(started ? "tool_started" : "tool_updated", number, label, "");
            }
        }

        private void forwardToolResult(JsonNode message) throws IOException {
            String id = message.path("tool_call_id").asText(message.path("toolCallId").asText("")).strip();
            Integer number = tools.remove(id);
            if (number == null) return;
            toolIndexes.values().removeIf(number::equals);

            JsonNode content = message.path("content");
            String output = content.isTextual() ? content.asText() : content.path(0).path("text").asText("");
            boolean blocked = output.contains("is not available in " + mode + " mode.");
            boolean failed = blocked || "error".equals(message.path("status").asText());
            String detail = blocked ? "当前问答模式禁止调用此工具"
                    : failed ? "Octop 工具返回错误" : "";
            toolStatus(failed ? "tool_failed" : "tool_completed", number, "", detail);
        }

        private void toolStatus(String kind, int number, String label, String detail) throws IOException {
            sendStatus(Map.of("kind", kind, "number", number, "label", label, "detail", detail));
        }

        private void sendStatus(Map<String, Object> status) throws IOException {
            emitter.send(SseEmitter.event().name("status").data(status));
        }

        private synchronized void finish(String error) {
            if (!closed.compareAndSet(false, true)) return;
            try {
                emitter.send(SseEmitter.event().name(error == null ? "done" : "error")
                        .data(error == null ? Map.of("ok", true, "threadId", threadId == null ? "" : threadId) : Map.of("message", error)));
            } catch (IOException | IllegalStateException ignored) {
                // The browser may have already closed the stream.
            } finally {
                emitter.complete();
                if (socket != null) socket.abort();
            }
        }

        private void timeout() {
            finish("等待 Octop 回复超时，请稍后重试");
        }

        private synchronized void close() {
            if (!closed.compareAndSet(false, true)) return;
            if (socket == null) return;
            if (!turnStarted || threadId == null) {
                socket.abort();
                return;
            }
            try {
                socket.sendText(json.writeValueAsString(Map.of("type", "cancel", "thread_id", threadId)), true)
                        .whenComplete((result, error) -> socket.abort());
            } catch (IOException | RuntimeException e) {
                socket.abort();
            }
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            finish("Octop 对话连接已关闭");
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            finish("Octop 对话连接已中断");
        }
    }

    private synchronized String loginToken() throws IOException, InterruptedException {
        Instant now = Instant.now();
        if (serviceToken != null && now.isBefore(renewTokenAt)) return serviceToken;
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/auth/login"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(
                        Map.of("username", username, "password", password))))
                .build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        int status = response.statusCode();
        if (status == 401 || status == 403) {
            throw upstreamError("Octop 服务账号认证失败，请检查后台凭据");
        }
        if (status != 200) {
            throw upstreamError("Octop 登录服务返回 HTTP " + status);
        }
        JsonNode login = json.readTree(response.body());
        String token = login.path("access_token").asText();
        if (token.isBlank()) throw upstreamError("Octop 登录未返回令牌");
        serviceToken = token;
        renewTokenAt = now.plusSeconds(Math.max(0, login.path("expires_in").asLong(0) - 60));
        return token;
    }

    private static ResponseStatusException upstreamError(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, reason);
    }

    public record PreviewQuestion(
            @NotBlank @Size(max = 1000) String message,
            @NotNull UUID conversationId,
            @Pattern(regexp = "ask|plan|craft", message = "无效的 Octop 对话模式") String conversationMode) {}
}
