package com.wuxiaozhi.controller;

import com.wuxiaozhi.config.AppProperties;
import com.wuxiaozhi.config.DifyProperties;
import com.wuxiaozhi.service.DifyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final DifyService difyService;
    private final DifyProperties difyProperties;
    private final AppProperties appProperties;

    public SystemController(DifyService difyService, DifyProperties difyProperties, AppProperties appProperties) {
        this.difyService = difyService;
        this.difyProperties = difyProperties;
        this.appProperties = appProperties;
    }

    @GetMapping("/dify-status")
    public Map<String, Object> difyStatus() {
        Map<String, Boolean> workflowConfigured = new LinkedHashMap<>();
        for (String key : List.of("vision-correction", "text-assist", "env-check", "report-generate")) {
            workflowConfigured.put(key, difyProperties.canRun(key));
        }
        return Map.of(
                "configured", difyService.isConfigured(),
                "appMode", difyService.getAppMode(),
                "baseUrl", difyProperties.getBaseUrl(),
                "configFile", "config/dify.env",
                "workflows", workflowConfigured
        );
    }

    @GetMapping("/bench-camera")
    public AppProperties.BenchCamera benchCamera() {
        return appProperties.getBenchCamera();
    }
}
