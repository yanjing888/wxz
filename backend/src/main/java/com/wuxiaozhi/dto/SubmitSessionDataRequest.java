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
}
