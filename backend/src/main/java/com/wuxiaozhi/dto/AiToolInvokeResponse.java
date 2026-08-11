package com.wuxiaozhi.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AiToolInvokeResponse {
    private String text;
    private boolean fromDify;
    private Map<String, Object> data = new LinkedHashMap<>();
}
