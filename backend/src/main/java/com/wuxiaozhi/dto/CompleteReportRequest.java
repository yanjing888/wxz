package com.wuxiaozhi.dto;

import lombok.Data;

import java.util.Map;

@Data
public class CompleteReportRequest {
    private Long sessionId;
    private Map<String, String> sections;
}
