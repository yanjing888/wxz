package com.wuxiaozhi.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class MechanicalScaleRecognizeResponse {
    private boolean ok;
    private String fieldKey;
    private String fieldLabel;
    private Double value;
    private String unit = "mm";
    private String display = "";
    private double confidence;
    private String debugImageUrl = "";
    private String method = "local-scale-vision";
    private List<String> steps = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private Map<String, Object> quality = new LinkedHashMap<>();
}
