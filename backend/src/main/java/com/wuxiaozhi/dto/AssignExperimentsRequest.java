package com.wuxiaozhi.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AssignExperimentsRequest {
    private String className;
    private String instructions;
    private java.time.LocalDateTime reportDueAt;
    private List<String> experimentCodes = new ArrayList<>();
}
