package com.wuxiaozhi.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CompleteGradeRequest {
    @NotNull(message = "请填写评分")
    @DecimalMin(value = "0.0", message = "评分不能小于0分")
    @DecimalMax(value = "100.0", message = "评分不能超过100分")
    private Double score;

    private String comment;
}
