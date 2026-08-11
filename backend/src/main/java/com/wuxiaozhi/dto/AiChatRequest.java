package com.wuxiaozhi.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiChatRequest {
    @NotBlank
    private String userMessage;
    /** 当前实验上下文，用于知识库检索与提示词补充 */
    private String experimentCode;
}
