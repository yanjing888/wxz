package com.wuxiaozhi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "wuxiaozhi.dify")
public class DifyProperties {
    private String baseUrl = "http://localhost/v1";
    private String apiKey = "";
    /** workflow = /workflows/run；chat = /chat-messages（对话型/Chatflow 应用） */
    private String appMode = "chat";
    private Map<String, String> workflows = new HashMap<>();
    /** 单个 Dify 应用可覆盖全局 appMode，例如 env-check=workflow。 */
    private Map<String, String> workflowModes = new HashMap<>();
    private Knowledge knowledge = new Knowledge();

    @Data
    public static class Knowledge {
        /** 每次检索返回的最大片段数 */
        private int topK = 5;
        /** 知识库检索 API Key；留空则回退 text-assist → 全局 api-key */
        private String apiKey = "";
    }

    public String resolveKnowledgeApiKey() {
        if (knowledge.getApiKey() != null && !knowledge.getApiKey().isBlank()) {
            return knowledge.getApiKey();
        }
        return resolveApiKey("text-assist");
    }

    public boolean isConfigured() {
        if (apiKey != null && !apiKey.isBlank()) return true;
        return workflows.values().stream().anyMatch(v -> v != null && !v.isBlank());
    }

    public String resolveApiKey(String workflowKey) {
        String perWorkflow = workflows.get(workflowKey);
        if (perWorkflow != null && !perWorkflow.isBlank()) return perWorkflow;
        return apiKey != null ? apiKey : "";
    }

    public boolean canRun(String workflowKey) {
        return !resolveApiKey(workflowKey).isBlank();
    }

    public boolean isChatMode() {
        return "chat".equalsIgnoreCase(appMode);
    }

    public boolean isChatMode(String workflowKey) {
        return "chat".equalsIgnoreCase(resolveAppMode(workflowKey));
    }

    public boolean isWorkflowMode(String workflowKey) {
        return "workflow".equalsIgnoreCase(resolveAppMode(workflowKey));
    }

    private String resolveAppMode(String workflowKey) {
        String perWorkflow = workflowModes.get(workflowKey);
        if (perWorkflow != null && !perWorkflow.isBlank()) {
            return perWorkflow.trim();
        }
        return appMode != null && !appMode.isBlank() ? appMode.trim() : "chat";
    }
}
