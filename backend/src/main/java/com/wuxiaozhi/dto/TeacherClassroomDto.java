package com.wuxiaozhi.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TeacherClassroomDto {
    private String experimentCode;
    private String experimentName;
    private String managedClass;
    private int activeCount;
    private int highPriorityCount;
    private int notReadyCount;
    private int dataIssueCount;
    private List<TeacherClassroomStudentDto> students = new ArrayList<>();
}
