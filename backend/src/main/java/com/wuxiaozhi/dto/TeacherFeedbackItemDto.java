package com.wuxiaozhi.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TeacherFeedbackItemDto {
    private Long id;
    private Long messageId;
    private Long sessionId;
    private Long userId;
    private String studentName;
    private String studentClass;
    private String experimentCode;
    private String experimentName;
    private int stepId;
    private String rating;
    private String userQuestion;
    private String aiReply;
    private boolean processed;
    private LocalDateTime createdAt;
}
