package com.wuxiaozhi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiaozhi.dto.experiment.ExperimentConfig;
import com.wuxiaozhi.dto.experiment.StepConfig;
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

    private static final List<String> DISPLAY_ORDER = List.of(
            "newton_rings",
            "air_wedge_thickness",
            "microscope_length_measurement"
    );

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
            if (step.getTut() == null) {
                step.setTut(new com.wuxiaozhi.dto.experiment.TutorialConfig());
            }
            var tut = step.getTut();
            if (tut.getSteps() == null || tut.getSteps().isEmpty()) {
                tut.setSteps(guide.list("Steps"));
            }
            if (tut.getWarnings() == null || tut.getWarnings().isEmpty()) {
                tut.setWarnings(guide.list("Warnings"));
            }
            if (tut.getImages() == null || tut.getImages().isEmpty()) {
                tut.setImages(guide.list("Images"));
            }
            if (tut.getVideoUrl() == null || tut.getVideoUrl().isBlank()) {
                String videoUrl = guide.text("Video");
                if (videoUrl != null && !videoUrl.isBlank()) {
                    tut.setVideoUrl(videoUrl.lines().findFirst().orElse("").trim());
                }
            }
            if (step.getDesc() == null || step.getDesc().isBlank()) {
                step.setDesc(guide.text("Description"));
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
        return byCode.values().stream()
                .filter(cfg -> cfg.getEnabled() == null || cfg.getEnabled())
                .sorted(Comparator.comparingInt(cfg -> {
                    int index = DISPLAY_ORDER.indexOf(cfg.getCode());
                    return index >= 0 ? index : DISPLAY_ORDER.size();
                }))
                .toList();
    }

    public ExperimentConfig getByCode(String code) {
        ExperimentConfig cfg = byCode.get(code);
        if (cfg == null) {
            throw new NoSuchElementException("实验不存在: " + code);
        }
        return cfg;
    }

    /** 读取 teaching-knowledge.md 全文，供报告助手对照实验目的、步骤与数据处理要求 */
    public String readTeachingKnowledge(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        try {
            Resource resource = new PathMatchingResourcePatternResolver()
                    .getResource("classpath:experiments/" + code.trim() + "/teaching-knowledge.md");
            if (!resource.exists()) {
                return "";
            }
            return stripYamlFrontmatter(resource.getContentAsString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            return "";
        }
    }

    /** 实验指导书：教学知识库 + manifest 分步骤操作指引 */
    public String buildExperimentGuide(ExperimentConfig exp) {
        if (exp == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String teaching = readTeachingKnowledge(exp.getCode());
        if (!teaching.isBlank()) {
            sb.append(teaching.trim()).append("\n\n");
        }
        if (exp.getSteps() != null && !exp.getSteps().isEmpty()) {
            sb.append("## 分步骤操作指引（manifest）\n\n");
            exp.getSteps().entrySet().stream()
                    .sorted(Comparator.comparingInt(e -> parseStepNo(e.getKey())))
                    .forEach(entry -> {
                        StepConfig step = entry.getValue();
                        sb.append("### 步骤 ").append(entry.getKey()).append("：")
                                .append(step.getTitle() != null ? step.getTitle() : "").append("\n");
                        if (step.getDesc() != null && !step.getDesc().isBlank()) {
                            sb.append(step.getDesc().trim()).append("\n");
                        }
                        if (step.getTut() != null && step.getTut().getSteps() != null) {
                            for (String item : step.getTut().getSteps()) {
                                if (item != null && !item.isBlank()) {
                                    sb.append("- ").append(item.trim()).append("\n");
                                }
                            }
                        }
                        if (step.getTut() != null && step.getTut().getWarnings() != null && !step.getTut().getWarnings().isEmpty()) {
                            sb.append("常见错误/注意：\n");
                            for (String warning : step.getTut().getWarnings()) {
                                if (warning != null && !warning.isBlank()) {
                                    sb.append("- ").append(warning.trim()).append("\n");
                                }
                            }
                        }
                        if (step.getDataFields() != null && !step.getDataFields().isEmpty()) {
                            sb.append("需记录字段：");
                            step.getDataFields().forEach(field -> {
                                if (field.getLabel() != null && !field.getLabel().isBlank()) {
                                    sb.append(field.getLabel());
                                    if (field.getUnit() != null && !field.getUnit().isBlank()) {
                                        sb.append("(").append(field.getUnit()).append(")");
                                    }
                                    sb.append("、");
                                }
                            });
                            if (sb.charAt(sb.length() - 1) == '、') {
                                sb.setLength(sb.length() - 1);
                            }
                            sb.append("\n");
                        }
                        sb.append("\n");
                    });
        }
        return sb.toString().trim();
    }

    /**
     * 教师预评用：实验目的 + 步骤 + 应测内容，让模型知道这份实验要完成什么。
     */
    public String buildExperimentSpec(ExperimentConfig exp) {
        if (exp == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (exp.getName() != null && !exp.getName().isBlank()) {
            sb.append("# ").append(exp.getName().trim()).append("\n\n");
        }
        String teaching = readTeachingKnowledge(exp.getCode());
        String purpose = extractMarkdownSection(teaching, "实验目的");
        if (!purpose.isBlank()) {
            sb.append("## 实验目的\n").append(purpose.trim()).append("\n\n");
        }
        String principle = extractMarkdownSection(teaching, "实验原理");
        if (!principle.isBlank()) {
            sb.append("## 实验原理（评分参照）\n").append(truncate(principle.trim(), 1800)).append("\n\n");
        }
        sb.append("## 实验步骤\n");
        String teachingSteps = extractMarkdownSection(teaching, "实验步骤");
        if (!teachingSteps.isBlank()) {
            sb.append(teachingSteps.trim()).append("\n\n");
        }
        if (exp.getSteps() != null && !exp.getSteps().isEmpty()) {
            sb.append("### 分步操作要点\n");
            exp.getSteps().entrySet().stream()
                    .sorted(Comparator.comparingInt(e -> parseStepNo(e.getKey())))
                    .forEach(entry -> {
                        StepConfig step = entry.getValue();
                        sb.append(entry.getKey()).append(". ")
                                .append(step.getTitle() != null ? step.getTitle() : "").append("\n");
                        if (step.getDesc() != null && !step.getDesc().isBlank()) {
                            sb.append(step.getDesc().trim()).append("\n");
                        }
                        if (step.getTut() != null && step.getTut().getSteps() != null) {
                            for (String item : step.getTut().getSteps()) {
                                if (item != null && !item.isBlank()) {
                                    sb.append("- ").append(item.trim()).append("\n");
                                }
                            }
                        }
                        if (step.getDataFields() != null && !step.getDataFields().isEmpty()) {
                            sb.append("应记录：");
                            List<String> labels = new ArrayList<>();
                            step.getDataFields().forEach(field -> {
                                if (field.getLabel() != null && !field.getLabel().isBlank()) {
                                    String item = field.getLabel();
                                    if (field.getUnit() != null && !field.getUnit().isBlank()) {
                                        item += "(" + field.getUnit() + ")";
                                    }
                                    labels.add(item);
                                }
                            });
                            sb.append(String.join("、", labels)).append("\n");
                        }
                        sb.append("\n");
                    });
        }
        String dataReq = extractMarkdownSection(teaching, "数据处理");
        if (dataReq.isBlank()) {
            dataReq = extractMarkdownSection(teaching, "数据记录");
        }
        if (!dataReq.isBlank()) {
            sb.append("## 数据处理要求\n").append(truncate(dataReq.trim(), 1200)).append("\n");
        }
        return sb.toString().trim();
    }

    /** 学生报告编辑器「从实验记录填充」用的结构化章节（来自 teaching-knowledge + manifest 分步指引） */
    public Map<String, String> buildReportFillSections(ExperimentConfig exp) {
        Map<String, String> sections = new LinkedHashMap<>();
        if (exp == null) {
            return sections;
        }
        String teaching = readTeachingKnowledge(exp.getCode());
        sections.put("purpose", extractMarkdownSection(teaching, "实验目的"));
        sections.put("principle", extractMarkdownSection(teaching, "实验原理"));
        sections.put("apparatus", extractMarkdownSection(teaching, "仪器与材料"));
        sections.put("procedure", buildReportProcedure(exp, teaching));
        String dataProcessing = extractMarkdownSection(teaching, "数据处理规范");
        if (dataProcessing.isBlank()) {
            dataProcessing = extractMarkdownSection(teaching, "数据处理");
        }
        if (dataProcessing.isBlank()) {
            dataProcessing = extractMarkdownSection(teaching, "数据记录");
        }
        sections.put("dataProcessing", dataProcessing);
        return sections;
    }

    private String buildReportProcedure(ExperimentConfig exp, String teaching) {
        StringBuilder sb = new StringBuilder();
        if (exp.getSteps() != null && !exp.getSteps().isEmpty()) {
            exp.getSteps().entrySet().stream()
                    .sorted(Comparator.comparingInt(e -> parseStepNo(e.getKey())))
                    .forEach(entry -> {
                        StepConfig step = entry.getValue();
                        sb.append("步骤 ").append(entry.getKey()).append("：")
                                .append(step.getTitle() != null ? step.getTitle() : "").append("\n");
                        if (step.getDesc() != null && !step.getDesc().isBlank()) {
                            sb.append("【目标】").append(step.getDesc().trim()).append("\n");
                        }
                        if (step.getTut() != null && step.getTut().getSteps() != null && !step.getTut().getSteps().isEmpty()) {
                            sb.append("【操作指引】\n");
                            int index = 1;
                            for (String item : step.getTut().getSteps()) {
                                if (item != null && !item.isBlank()) {
                                    sb.append("  ").append(index++).append(". ").append(item.trim()).append("\n");
                                }
                            }
                        }
                        if (step.getTut() != null && step.getTut().getWarnings() != null && !step.getTut().getWarnings().isEmpty()) {
                            sb.append("【注意事项】\n");
                            for (String warning : step.getTut().getWarnings()) {
                                if (warning != null && !warning.isBlank()) {
                                    sb.append("  • ").append(warning.trim()).append("\n");
                                }
                            }
                        }
                        if (step.getDataFields() != null && !step.getDataFields().isEmpty()) {
                            sb.append("【需记录数据】");
                            step.getDataFields().forEach(field -> {
                                if (field.getLabel() != null && !field.getLabel().isBlank()) {
                                    sb.append(field.getLabel().trim());
                                    if (field.getUnit() != null && !field.getUnit().isBlank()) {
                                        sb.append("(").append(field.getUnit().trim()).append(")");
                                    }
                                    sb.append("、");
                                }
                            });
                            if (sb.charAt(sb.length() - 1) == '、') {
                                sb.setLength(sb.length() - 1);
                            }
                            sb.append("\n");
                        }
                        sb.append("\n");
                    });
            return sb.toString().trim();
        }
        return extractMarkdownSection(teaching, "实验步骤");
    }

    private static String extractMarkdownSection(String markdown, String headingKeyword) {
        if (markdown == null || markdown.isBlank() || headingKeyword == null) {
            return "";
        }
        java.util.regex.Pattern heading = java.util.regex.Pattern.compile(
                "(?m)^#{1,3}\\s*.*" + java.util.regex.Pattern.quote(headingKeyword) + ".*$");
        java.util.regex.Matcher start = heading.matcher(markdown);
        if (!start.find()) {
            return "";
        }
        int from = start.end();
        java.util.regex.Matcher next = java.util.regex.Pattern.compile("(?m)^#{1,3}\\s+").matcher(markdown);
        if (next.find(from)) {
            return markdown.substring(from, next.start()).trim();
        }
        return markdown.substring(from).trim();
    }

    private static String truncate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxChars).trim() + "\n…";
    }

    private static int parseStepNo(String stepKey) {
        try {
            return Integer.parseInt(stepKey);
        } catch (NumberFormatException e) {
            return 999;
        }
    }

    private static String stripYamlFrontmatter(String raw) {
        if (raw == null || !raw.startsWith("---")) {
            return raw != null ? raw.trim() : "";
        }
        int end = raw.indexOf("---", 3);
        if (end > 0) {
            return raw.substring(end + 3).trim();
        }
        return raw.trim();
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
