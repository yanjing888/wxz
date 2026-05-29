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
}
