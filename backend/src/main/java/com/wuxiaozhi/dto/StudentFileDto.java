package com.wuxiaozhi.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StudentFileDto {
    private Long id;
    private String experimentCode;
    private String experimentName;
    private String category;
    private String categoryLabel;
    private String stage;
    private String fileName;
    private String url;
    private String contentType;
    private long sizeBytes;
    private Long sessionId;
    private String note;
    private LocalDateTime createdAt;
    private boolean previewable;
}
