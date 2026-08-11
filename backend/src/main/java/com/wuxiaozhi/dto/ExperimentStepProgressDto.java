package com.wuxiaozhi.dto;

import lombok.Data;

@Data
public class ExperimentStepProgressDto {
    private String key;
    private String label;
    /** pending | active | done */
    private String status;
}
