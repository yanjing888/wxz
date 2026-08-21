package com.wuxiaozhi.controller;

import com.wuxiaozhi.dto.experiment.ExperimentConfig;
import com.wuxiaozhi.service.ExperimentConfigService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 供 Dify 工作流 HTTP 节点只读拉取实验 manifest（含步骤 desc / tut），无需登录。
 */
@RestController
@RequestMapping("/api/public/experiments")
public class PublicExperimentController {

    private final ExperimentConfigService experimentConfigService;

    public PublicExperimentController(ExperimentConfigService experimentConfigService) {
        this.experimentConfigService = experimentConfigService;
    }

    @GetMapping("/{code}")
    public ExperimentConfig get(@PathVariable String code) {
        ExperimentConfig config = experimentConfigService.getByCode(code);
        if (Boolean.FALSE.equals(config.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "实验未启用");
        }
        return config;
    }
}
