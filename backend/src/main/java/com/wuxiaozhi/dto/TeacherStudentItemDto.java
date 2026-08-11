package com.wuxiaozhi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeacherStudentItemDto {
    private Long userId;
    private String username;
    private String displayName;
    private String studentClass;
    private List<String> assignedExperimentCodes = new ArrayList<>();
    private List<String> assignedExperimentNames = new ArrayList<>();
}
