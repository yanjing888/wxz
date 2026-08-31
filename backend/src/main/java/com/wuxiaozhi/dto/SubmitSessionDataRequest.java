package com.wuxiaozhi.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class SubmitSessionDataRequest {
    @NotNull
    private Integer stepId;
    @NotNull
    private Map<String, Object> values;
    /** false = only check/correct; true = saved as report-ready experiment data. */
    private Boolean officialData;
    /** Defaults to true; official saves may set false to avoid duplicate AI messages. */
    private Boolean runCorrection;
}
