package com.wuxiaozhi.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiRecapSessionItemDto {
    private Long sessionId;
    private String experimentCode;
    private String experimentName;
    private LocalDateTime endTime;
    private Integer helpCount;
    private Integer errorPointCount;
}
