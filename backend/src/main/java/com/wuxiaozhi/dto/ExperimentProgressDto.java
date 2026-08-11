package com.wuxiaozhi.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ExperimentProgressDto {
    private String experimentCode;
    private String experimentName;
    private String currentStep;
    private Long activeSessionId;
    private Long finishedSessionId;
    private boolean dataCollectionEnabled;
    private boolean dataSubmitted;
    private List<ExperimentStepProgressDto> steps = new ArrayList<>();
}
