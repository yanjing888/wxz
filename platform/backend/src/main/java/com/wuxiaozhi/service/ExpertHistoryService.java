package com.wuxiaozhi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiaozhi.entity.ExpertConversation;
import com.wuxiaozhi.repository.ExpertConversationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/** Only ownership/routing metadata lives here; messages are read from Octop. */
@Service
public class ExpertHistoryService {
    private final ExpertConversationRepository repository;
    private final ObjectMapper json;
    private final String baseUrl;
    private final String gatewayKey;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    public ExpertHistoryService(ExpertConversationRepository repository, ObjectMapper json,
            @Value("${octop.preview.history-url:}") String baseUrl,
            @Value("${octop.preview.gateway-key:}") String gatewayKey) {
        this.repository = repository; this.json = json; this.baseUrl = baseUrl; this.gatewayKey = gatewayKey;
    }
    public List<Map<String, Object>> list(long userId, String expertId) {
        return repository.findByUserIdAndExpertIdOrderByUpdatedAtDesc(userId, expertId).stream()
                .filter(c -> c.getThreadId() != null)
                .map(c -> Map.<String, Object>of("id", c.getId(), "title", c.getTitle(), "updatedAt", c.getUpdatedAt())).toList();
    }
    public Map<String, Object> get(String id, long userId, String expertId) {
        ExpertConversation c = repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        checkOwner(c, userId, expertId);
        if (baseUrl.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "专家历史读取服务尚未配置");
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/history/" + c.getThreadId()))
                    .header("Authorization", "Bearer " + gatewayKey).timeout(Duration.ofSeconds(60)).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "无法读取专家历史对话，请稍后重试");
            return Map.of("id", id, "mode", c.getMode(), "messages", json.readTree(response.body()).path("messages"));
        } catch (java.io.IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "专家历史读取服务连接失败");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new ResponseStatusException(HttpStatus.BAD_GATEWAY);
        }
    }
    public synchronized String begin(String id, long userId, String expertId, String prompt, String mode) {
        ExpertConversation c = repository.findById(id).orElseGet(() -> {
            ExpertConversation created = new ExpertConversation();
            created.setId(id); created.setUserId(userId); created.setExpertId(expertId);
            created.setTitle(prompt.substring(0, Math.min(60, prompt.length())));
            return created;
        });
        checkOwner(c, userId, expertId);
        if (c.getThreadId() == null) {
            if (baseUrl.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "专家会话服务尚未配置");
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/threads"))
                        .header("Authorization", "Bearer " + gatewayKey).timeout(Duration.ofSeconds(30))
                        .POST(HttpRequest.BodyPublishers.noBody()).build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "无法创建专家会话");
                String threadId = json.readTree(response.body()).path("thread_id").asText();
                if (!threadId.matches("thr_[a-zA-Z0-9]+")) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY);
                c.setThreadId(threadId);
            } catch (java.io.IOException e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "无法创建专家会话"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new ResponseStatusException(HttpStatus.BAD_GATEWAY); }
        }
        c.setMode(mode); c.setUpdatedAt(LocalDateTime.now()); repository.saveAndFlush(c);
        return c.getThreadId();
    }
    private void checkOwner(ExpertConversation c, long userId, String expertId) {
        if (!Objects.equals(c.getUserId(), userId) || !c.getExpertId().equals(expertId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
