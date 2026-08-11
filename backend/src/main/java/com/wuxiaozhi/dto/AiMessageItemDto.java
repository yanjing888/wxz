package com.wuxiaozhi.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiMessageItemDto {
    private Long id;
    private String role;
    private String text;
    private LocalDateTime createdAt;
}
