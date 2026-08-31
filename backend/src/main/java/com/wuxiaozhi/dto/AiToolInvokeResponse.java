package com.wuxiaozhi.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AiToolInvokeResponse {
    private String text;
    private boolean fromDify;
    private Map<String, Object> data = new LinkedHashMap<>();
    /** 建议分（10 分制，一位小数）；无结构化输出时为空 */
    private Double score;
    private Double maxScore;
    private String comment;
    private String gradeBand;
    private Boolean needsTeacherConfirm;
}
