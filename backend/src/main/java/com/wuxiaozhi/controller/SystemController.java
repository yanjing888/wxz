package com.wuxiaozhi.controller;

import com.wuxiaozhi.config.DifyProperties;
import com.wuxiaozhi.service.DifyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final DifyService difyService;
    private final DifyProperties difyProperties;

    public SystemController(DifyService difyService, DifyProperties difyProperties) {
        this.difyService = difyService;
        this.difyProperties = difyProperties;
    }

    @GetMapping("/dify-status")
    public Map<String, Object> difyStatus() {
        return Map.of(
                "configured", difyService.isConfigured(),
                "appMode", difyService.getAppMode(),
                "baseUrl", difyProperties.getBaseUrl()
        );
    }
}
