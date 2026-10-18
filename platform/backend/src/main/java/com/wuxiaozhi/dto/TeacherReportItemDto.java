package com.wuxiaozhi.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TeacherReportItemDto {
    private Long sessionId;
    private Long userId;
    private String studentName;
    private String studentClass;
    private String experimentCode;
    private String experimentName;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int helpCount;
    private int errorPointCount;
    private boolean reportCompleted;
    private Double aiReviewScore;
    private Double teacherScore;
    private boolean gradingCompleted;
}
