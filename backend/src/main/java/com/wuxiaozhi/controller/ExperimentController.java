package com.wuxiaozhi.controller;

import com.wuxiaozhi.dto.experiment.ExperimentConfig;
import com.wuxiaozhi.service.ExperimentConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/experiments")
public class ExperimentController {

    private final ExperimentConfigService experimentConfigService;

    public ExperimentController(ExperimentConfigService experimentConfigService) {
        this.experimentConfigService = experimentConfigService;
    }

    @GetMapping
    public List<ExperimentConfig> list() {
        return experimentConfigService.listAll();
    }

    @GetMapping("/{code}")
    public ExperimentConfig get(@PathVariable String code) {
        return experimentConfigService.getByCode(code);
    }
}
