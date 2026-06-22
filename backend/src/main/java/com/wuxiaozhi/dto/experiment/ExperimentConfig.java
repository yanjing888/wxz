package com.wuxiaozhi.dto.experiment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExperimentConfig {
    private String code;
    private String name;
    private String category;
    private List<String> menuLabels;
    private List<String> reportKnowledge;
    private List<String> reportPath;
    private String reportGuidePath;
    private Map<String, StepConfig> steps;
    private DataCollectionConfig dataCollection;
    /** Dify 侧配置：知识库 ID 等 */
    private ExperimentDifyConfig dify;
}
