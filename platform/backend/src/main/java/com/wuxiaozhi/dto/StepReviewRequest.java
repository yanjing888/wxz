package com.wuxiaozhi.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class StepReviewRequest {
    @NotNull private Boolean passed;
    @NotNull @DecimalMin("0") private Double score;
    @Size(max = 2000) private String comment;
    private java.util.List<String> checkedCriteria;
    /** Revision number prevents a stale teacher screen from overwriting a later review. */
    private Long version;
}
