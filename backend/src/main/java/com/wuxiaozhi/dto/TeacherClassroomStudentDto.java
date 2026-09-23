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
    private boolean reportCompleted;
    /** high | medium | normal */
    private String priority;
    private String priorityReason;
    private LocalDateTime startTime;
    /** 学生是否在监控页开启了摄像头（近期有心跳） */
    private boolean cameraActive;

    /** 最近一次巡检等级 L0-L3 / NA */
    private String latestEnvLevel;

    /** 本会话巡检记录条数 */
    private int envLogCount;

    /** 是否开启自动安全巡检 */
    private boolean envCheckEnabled;
}
