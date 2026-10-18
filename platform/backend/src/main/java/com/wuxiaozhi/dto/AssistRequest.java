package com.wuxiaozhi.dto;

import lombok.Data;

@Data
public class AssistRequest {
    private String userMessage;
    private String imageUrl;
    /** 前端当前步骤；缺省时回退 session.activeStep */
    private Integer stepId;
}
