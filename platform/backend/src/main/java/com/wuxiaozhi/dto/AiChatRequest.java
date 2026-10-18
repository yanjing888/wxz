package com.wuxiaozhi.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiChatRequest {
    @NotBlank
    private String userMessage;
    /** 当前实验上下文，用于知识库检索与提示词补充 */
    private String experimentCode;
    /** 报告助手：当前实验会话，用于注入 data_logs / corrections */
    private Long sessionId;
    /** 报告助手：学生当前草稿各章节 JSON */
    private String reportSectionsJson;
}
