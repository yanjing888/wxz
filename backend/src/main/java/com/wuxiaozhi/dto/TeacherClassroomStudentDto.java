package com.wuxiaozhi.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class TeacherClassroomStudentDto {
    private Long userId;
    private String studentName;
    private String studentClass;
    private String experimentCode;
    private String experimentName;
    private Long sessionId;
    private String status;
    private int activeStep;
    private String stepTitle;
    private int helpCount;
    private int errorPointCount;
    private long minutesOnSession;
    private boolean preLabCompleted;
    private boolean dataIssue;
    private String lastDataValidation;
    private List<String> recentCorrectionTypes = new ArrayList<>();
    /** high | medium | normal */
    private String priority;
    private String priorityReason;
    private LocalDateTime startTime;
}
