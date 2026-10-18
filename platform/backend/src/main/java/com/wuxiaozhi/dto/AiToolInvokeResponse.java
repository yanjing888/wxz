package com.wuxiaozhi.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AiToolInvokeResponse {
    private String text;
    private boolean fromDify;
    private Map<String, Object> data = new LinkedHashMap<>();
    /** 建议分；教师报告预评为百分制，具体满分由maxScore提供。 */
    private Double score;
    private Double maxScore;
    private String comment;
    private String gradeBand;
    private Boolean needsTeacherConfirm;
}
