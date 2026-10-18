package com.wuxiaozhi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "wuxiaozhi.ai-tools")
public class AiToolsProperties {
    private List<AiToolDefinition> items = new ArrayList<>();
}
