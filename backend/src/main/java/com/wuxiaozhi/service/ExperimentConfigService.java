package com.wuxiaozhi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiaozhi.dto.experiment.ExperimentConfig;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
public class ExperimentConfigService {

    private final ObjectMapper objectMapper;
    private final Map<String, ExperimentConfig> byCode = new LinkedHashMap<>();

    public ExperimentConfigService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void loadAll() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:experiments/*.json");
        for (Resource resource : resources) {
            ExperimentConfig cfg = objectMapper.readValue(resource.getInputStream(), ExperimentConfig.class);
            if (cfg.getCode() == null || cfg.getCode().isBlank()) {
                throw new IllegalStateException("Experiment config missing code: " + resource.getFilename());
            }
            byCode.put(cfg.getCode(), cfg);
        }
    }

    public List<ExperimentConfig> listAll() {
        return new ArrayList<>(byCode.values());
    }

    public ExperimentConfig getByCode(String code) {
        ExperimentConfig cfg = byCode.get(code);
        if (cfg == null) {
            throw new NoSuchElementException("实验不存在: " + code);
        }
        return cfg;
    }
}
