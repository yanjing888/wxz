package com.wuxiaozhi.dto.experiment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExperimentDifyConfig {
    /** Dify 知识库 Dataset UUID，与实验一一对应 */
    private String datasetId;
}
