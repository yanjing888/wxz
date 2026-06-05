package com.wuxiaozhi.dto;

import lombok.Data;

import java.util.Map;

@Data
public class SessionDataSubmitResponse {
    private int stepId;
    private Map<String, Object> values;
    private DataValidationResult validation;
    private AssistResponse assist;
}
