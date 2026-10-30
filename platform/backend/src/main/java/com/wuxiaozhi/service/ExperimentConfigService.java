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
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.wuxiaozhi.repository.ExperimentDefinitionRepository definitions;

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
        Set<String> codes = new LinkedHashSet<>(byCode.keySet());
        if (definitions != null) definitions.findAll().forEach(item -> codes.add(item.getCode()));
        return codes.stream().map(this::getByCode)
                .filter(cfg -> cfg.getEnabled() == null || cfg.getEnabled())
                .sorted(Comparator.comparingInt(cfg -> {
                    int index = DISPLAY_ORDER.indexOf(cfg.getCode());
                    return index >= 0 ? index : DISPLAY_ORDER.size();
                }))
                .toList();
    }

    public ExperimentConfig getByCode(String code) {
        if (definitions != null) {
            var stored = definitions.findById(code);
            if (stored.isPresent()) {
                try { return objectMapper.readValue(stored.get().getContentJson(), ExperimentConfig.class); }
                catch (IOException e) { throw new IllegalStateException("实验配置读取失败", e); }
            }
        }
        ExperimentConfig cfg = byCode.get(code);
        if (cfg == null) throw new NoSuchElementException("实验不存在: " + code);
        return objectMapper.convertValue(cfg, ExperimentConfig.class);
    }

    public Map<String, Object> managementDetail(String code) {
        var stored = definitions.findById(code);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("config", getByCode(code));
        result.put("teachingGuide", readTeachingKnowledge(code));
        result.put("version", stored.map(com.wuxiaozhi.entity.ExperimentDefinition::getVersion).orElse(-1L));
        result.put("updatedAt", stored.map(com.wuxiaozhi.entity.ExperimentDefinition::getUpdatedAt).orElse(null));
        return result;
    }

    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> saveTeachingConfig(String code, ExperimentConfig input, Long version, Long userId) {
        return persistTeachingConfig(code, input, version, userId, false);
    }

    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> createTeachingConfig(ExperimentConfig input, Long userId) {
        return persistTeachingConfig("exp_" + UUID.randomUUID().toString().replace("-", ""), input, -1L, userId, true);
    }

    private Map<String, Object> persistTeachingConfig(String code, ExperimentConfig input, Long version, Long userId, boolean creating) {
        var current = creating ? new ExperimentConfig() : getByCode(code);
        if (creating) {
            current.setCode(code); current.setSteps(new LinkedHashMap<>());
            current.setCommonDataFields(List.of()); current.setReportKnowledge(List.of()); current.setReportPath(List.of());
            current.setDataCollection(new com.wuxiaozhi.dto.experiment.DataCollectionConfig());
        }
        var entity = definitions.findById(code).orElse(null);
        if (!Objects.equals(version, entity == null ? -1L : entity.getVersion()))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "配置已被其他教师修改，请重新打开后编辑");
        require(input != null && input.getName() != null && !input.getName().isBlank() && input.getName().length() <= 128, "请填写实验名称（最多128字）");
        require(input.getSteps() != null && !input.getSteps().isEmpty() && input.getSteps().size() <= 30, "请配置1～30个实验步骤");
        require(input.getSteps().keySet().containsAll(current.getSteps().keySet()), "不可更改已有步骤编号或删除步骤");
        for (int id = 1; id <= input.getSteps().size(); id++) require(input.getSteps().containsKey(String.valueOf(id)), "实验步骤必须连续排列");
        require(input.getDescription() == null || input.getDescription().length() <= 20000, "实验内容过长");
        require(input.getTeachingContent() == null || input.getTeachingContent().length() <= 100000, "实验内容正文最多10万字");
        require(input.getCategory() == null || input.getCategory().length() <= 64, "实验分类过长");
        current.setName(input.getName().trim()); current.setCategory(input.getCategory()); current.setDescription(input.getDescription());
        if (input.getTeachingContent() != null) current.setTeachingContent(input.getTeachingContent());
        for (var entry : input.getSteps().entrySet()) {
            var in = entry.getValue(); var step = current.getSteps().computeIfAbsent(entry.getKey(), key -> {
                var added = new StepConfig(); added.setCorrectionMode("vision"); return added;
            });
            require(in != null && in.getTitle() != null && !in.getTitle().isBlank() && in.getTitle().length() <= 128, "每个步骤都需要名称（最多128字）");
            require(in.getDesc() == null || in.getDesc().length() <= 10000, "步骤说明过长");
            require(!Boolean.TRUE.equals(in.getAssessmentBlocking()) || !Boolean.FALSE.equals(in.getAssessmentRequired()), "关键检查点必须开启教师检查");
            step.setTitle(in.getTitle().trim()); step.setDesc(in.getDesc());
            step.setImageRequired(!Boolean.FALSE.equals(in.getImageRequired())); step.setTableRequired(Boolean.TRUE.equals(in.getTableRequired()));
            step.setAssessmentRequired(in.getAssessmentRequired()); step.setAssessmentBlocking(in.getAssessmentBlocking());
            if (in.getTut() != null) {
                require(in.getTut().getImages() == null || in.getTut().getImages().size() <= 20, "每步最多20张示教图片");
                if (in.getTut().getImages() != null) in.getTut().getImages().forEach(this::validateMediaUrl);
                validateMediaUrl(in.getTut().getVideoUrl());
                validateTextLines(in.getTut().getSteps()); validateTextLines(in.getTut().getWarnings());
            }
            step.setTut(in.getTut());
        }
        current.setMenuLabels(current.getSteps().entrySet().stream().sorted(Comparator.comparingInt(e -> Integer.parseInt(e.getKey()))).map(e -> e.getValue().getTitle()).toList());
        if (entity == null) { entity = new com.wuxiaozhi.entity.ExperimentDefinition(); entity.setCode(code); }
        try { entity.setContentJson(objectMapper.writeValueAsString(current)); }
        catch (IOException e) { throw new IllegalStateException(e); }
        entity.setUpdatedAt(java.time.LocalDateTime.now()); entity.setUpdatedBy(userId);
        definitions.saveAndFlush(entity);
        return managementDetail(code);
    }

    private void validateTextLines(List<String> lines) {
        require(lines == null || (lines.size() <= 100 && lines.stream().allMatch(s -> s != null && s.length() <= 2000)), "教程条目过多或单条超过2000字");
    }
    private void validateMediaUrl(String url) {
        if (url == null || url.isBlank()) return;
        require(url.length() <= 2000 && (url.matches("https?://[^\\s]+") || url.matches("/(?!/)[^\\s]+")), "媒体地址须为站内路径或 http/https 地址");
    }
    private void require(boolean condition, String message) {
        if (!condition) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, message);
    }

    /** 读取 teaching-knowledge.md 全文，供报告助手对照实验目的、步骤与数据处理要求 */
    public String readTeachingKnowledge(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        ExperimentConfig config = getByCode(code);
        if (config.getTeachingContent() != null) return config.getTeachingContent();
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
