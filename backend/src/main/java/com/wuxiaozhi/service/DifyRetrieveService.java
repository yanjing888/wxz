package com.wuxiaozhi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.wuxiaozhi.config.DifyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 调用 Dify Dataset Retrieve API，将召回片段格式化为 kb_context 供 Chatflow 使用。
 * 纯文字与带图求助均会检索（由 LabSessionService 统一触发）。
 */
@Service
public class DifyRetrieveService {

    private static final Logger log = LoggerFactory.getLogger(DifyRetrieveService.class);

    private final DifyProperties difyProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    public DifyRetrieveService(DifyProperties difyProperties) {
        this.difyProperties = difyProperties;
    }

    public String retrieve(String datasetId, String query) {
        if (datasetId == null || datasetId.isBlank()) {
            return "";
        }
        if (query == null || query.isBlank()) {
            return "";
        }
        String apiKey = difyProperties.resolveKnowledgeApiKey();
        if (apiKey.isBlank()) {
            log.debug("Skip KB retrieve: no Dify API key configured");
            return "";
        }

        String url = difyProperties.getBaseUrl().replaceAll("/$", "")
                + "/datasets/" + datasetId.trim() + "/retrieve";

        Map<String, Object> retrievalModel = new LinkedHashMap<>();
        retrievalModel.put("search_method", "semantic_search");
        retrievalModel.put("reranking_enable", false);
        retrievalModel.put("top_k", difyProperties.getKnowledge().getTopK());
        retrievalModel.put("score_threshold_enabled", false);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);
        body.put("retrieval_model", retrievalModel);

        try {
            JsonNode root = postJson(url, apiKey, body);
            return formatRecords(root.path("records"));
        } catch (Exception e) {
            log.warn("KB retrieve failed, datasetId={}, error={}", datasetId, e.getMessage());
            return "";
        }
    }

    private String formatRecords(JsonNode records) {
        if (!records.isArray() || records.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (JsonNode record : records) {
            String content = record.path("segment").path("content").asText("").trim();
            if (content.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append('[').append(index++).append("] ").append(content);
        }
        return sb.toString();
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
                throw new IllegalStateException("Empty Dify retrieve response");
            }
            if (root.has("code") && root.has("message")) {
                throw new IllegalStateException(root.path("message").asText("Dify retrieve error"));
            }
            return root;
        } catch (HttpStatusCodeException ex) {
            String msg = ex.getResponseBodyAsString();
            throw new IllegalStateException(msg.isBlank() ? ex.getMessage() : msg, ex);
        }
    }
}
