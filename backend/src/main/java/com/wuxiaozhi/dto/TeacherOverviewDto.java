package com.wuxiaozhi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TeacherOverviewDto {
    private long studentCount;
    private long finishedSessionCount;
    private long reportCount;
    private long feedbackCount;
    private long notHelpfulCount;
    private long unprocessedFeedbackCount;
    private String managedClass;
}
