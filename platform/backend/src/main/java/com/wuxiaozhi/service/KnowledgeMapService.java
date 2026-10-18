package com.wuxiaozhi.service;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;

@Service
public class KnowledgeMapService {

    private final Map<String, ExperimentKnowledge> experiments = new LinkedHashMap<>();

    @PostConstruct
    @SuppressWarnings("unchecked")
    public void load() {
        experiments.clear();
        ClassPathResource resource = new ClassPathResource("knowledge-map.yml");
        if (!resource.exists()) {
            return;
        }
        Yaml yaml = new Yaml();
        try (InputStream in = resource.getInputStream()) {
            Object loaded = yaml.load(in);
            if (!(loaded instanceof Map<?, ?> root)) {
                return;
            }
            Object rawExperiments = root.get("experiments");
            if (!(rawExperiments instanceof Map<?, ?> experimentMap)) {
                return;
            }
            experimentMap.forEach((code, raw) -> {
                if (code == null || !(raw instanceof Map<?, ?> body)) {
                    return;
                }
                experiments.put(String.valueOf(code), parseExperiment(body));
            });
        } catch (Exception ignored) {
            experiments.clear();
        }
    }

    public Optional<KnowledgeContext> resolve(String experimentCode, String stepId) {
        if (experimentCode == null || experimentCode.isBlank()) {
            return Optional.empty();
        }
        ExperimentKnowledge exp = experiments.get(experimentCode);
        if (exp == null) {
            return Optional.empty();
        }
        StepKnowledge step = stepId != null ? exp.steps().get(stepId) : null;
        return Optional.of(new KnowledgeContext(
                exp.teachingDataset(),
                exp.rulesDataset(),
                exp.teachingDoc(),
                exp.rulesDoc(),
                step != null ? step.teachingSection() : exp.teachingSection(),
                step != null ? step.rulesSection() : exp.rulesSection(),
                step != null ? step.retrievalTags() : List.of(experimentCode)
        ));
    }

    public void enrichInputs(Map<String, Object> inputs, String experimentCode, String stepId, boolean correctionMode) {
        resolve(experimentCode, stepId).ifPresent(ctx -> {
            putIfText(inputs, "teaching_dataset", ctx.teachingDataset());
            putIfText(inputs, "rules_dataset", ctx.rulesDataset());
            putIfText(inputs, "teaching_doc", ctx.teachingDoc());
            putIfText(inputs, "rules_doc", ctx.rulesDoc());
            putIfText(inputs, "teaching_section", ctx.teachingSection());
            putIfText(inputs, "rules_section", ctx.rulesSection());
            if (!ctx.retrievalTags().isEmpty()) {
                inputs.put("retrieval_tags", String.join(" ", ctx.retrievalTags()));
                inputs.put("retrieval_tags_list", ctx.retrievalTags());
            }
            inputs.put("knowledge_type", correctionMode ? "correction_rule" : "teaching");
        });
    }

    private ExperimentKnowledge parseExperiment(Map<?, ?> body) {
        Map<?, ?> teaching = map(body.get("teaching"));
        Map<?, ?> rules = map(body.get("correctionRules"));
        Map<String, StepKnowledge> steps = new LinkedHashMap<>();
        map(body.get("steps")).forEach((key, value) -> {
            if (key != null && value instanceof Map<?, ?> stepBody) {
                steps.put(String.valueOf(key), parseStep(stepBody));
            }
        });
        return new ExperimentKnowledge(
                text(teaching.get("dataset")),
                text(rules.get("dataset")),
                text(teaching.get("doc")),
                text(rules.get("doc")),
                text(teaching.get("sectionKey")),
                text(rules.get("sectionKey")),
                steps
        );
    }

    private StepKnowledge parseStep(Map<?, ?> body) {
        return new StepKnowledge(
                text(body.get("teachingSection")),
                text(body.get("rulesSection")),
                stringList(body.get("retrievalTags"))
        );
    }

    private void putIfText(Map<String, Object> inputs, String key, String value) {
        if (value != null && !value.isBlank()) {
            inputs.put(key, value);
        }
    }

    private Map<?, ?> map(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private String text(Object value) {
        return value != null ? String.valueOf(value).trim() : "";
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(String::valueOf)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private record ExperimentKnowledge(String teachingDataset,
                                       String rulesDataset,
                                       String teachingDoc,
                                       String rulesDoc,
                                       String teachingSection,
                                       String rulesSection,
                                       Map<String, StepKnowledge> steps) {
    }

    private record StepKnowledge(String teachingSection,
                                 String rulesSection,
                                 List<String> retrievalTags) {
    }

    public record KnowledgeContext(String teachingDataset,
                                   String rulesDataset,
                                   String teachingDoc,
                                   String rulesDoc,
                                   String teachingSection,
                                   String rulesSection,
                                   List<String> retrievalTags) {
    }
}
