package com.wuxiaozhi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiaozhi.dto.experiment.ExperimentConfig;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
        byCode.clear();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        loadResources(resolver.getResources("classpath:experiments/*.json"));
        loadResources(resolver.getResources("classpath:experiments/*/manifest.json"));
    }

    private void loadResources(Resource[] resources) throws IOException {
        for (Resource resource : resources) {
            ExperimentConfig cfg = objectMapper.readValue(resource.getInputStream(), ExperimentConfig.class);
            hydrateExternalContent(cfg, resource);
            register(cfg, resource);
        }
    }

    private void register(ExperimentConfig cfg, Resource resource) {
        if (cfg.getCode() == null || cfg.getCode().isBlank()) {
            throw new IllegalStateException("Experiment config missing code: " + resource.getFilename());
        }
        byCode.put(cfg.getCode(), cfg);
    }

    private void hydrateExternalContent(ExperimentConfig cfg, Resource manifestResource) throws IOException {
        if (cfg.getReportGuidePath() != null && !cfg.getReportGuidePath().isBlank()) {
            MarkdownSections report = readMarkdown(manifestResource.createRelative(cfg.getReportGuidePath()));
            if (cfg.getReportKnowledge() == null || cfg.getReportKnowledge().isEmpty()) {
                cfg.setReportKnowledge(report.list("Knowledge"));
            }
            if (cfg.getReportPath() == null || cfg.getReportPath().isEmpty()) {
                cfg.setReportPath(report.list("Follow-up"));
            }
        }
        if (cfg.getSteps() == null || cfg.getSteps().isEmpty()) {
            return;
        }
        for (var entry : cfg.getSteps().entrySet()) {
            var step = entry.getValue();
            if (step.getGuidePath() == null || step.getGuidePath().isBlank()) {
                continue;
            }
            MarkdownSections guide = readMarkdown(manifestResource.createRelative(step.getGuidePath()));
            if (step.getDesc() == null || step.getDesc().isBlank()) {
                step.setDesc(guide.text("Description"));
            }
            if (step.getTut() == null) {
                var tut = new com.wuxiaozhi.dto.experiment.TutorialConfig();
                tut.setSteps(guide.list("Steps"));
                tut.setWarnings(guide.list("Warnings"));
                step.setTut(tut);
            }
        }
    }

    private MarkdownSections readMarkdown(Resource resource) throws IOException {
        if (!resource.exists()) {
            return new MarkdownSections(Map.of());
        }
        Map<String, List<String>> sections = new LinkedHashMap<>();
        String current = "";
        sections.put(current, new ArrayList<>());
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("## ")) {
                    current = line.substring(3).trim();
                    sections.putIfAbsent(current, new ArrayList<>());
                } else if (!line.startsWith("# ")) {
                    sections.get(current).add(line);
                }
            }
        }
        return new MarkdownSections(sections);
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

    private record MarkdownSections(Map<String, List<String>> sections) {
        String text(String section) {
            List<String> lines = sections.getOrDefault(section, List.of());
            return String.join("\n", lines).trim();
        }

        List<String> list(String section) {
            List<String> items = new ArrayList<>();
            for (String line : sections.getOrDefault(section, List.of())) {
                String trimmed = line.trim();
                if (trimmed.startsWith("- ")) {
                    items.add(trimmed.substring(2).trim());
                }
            }
            return items;
        }
    }
}
