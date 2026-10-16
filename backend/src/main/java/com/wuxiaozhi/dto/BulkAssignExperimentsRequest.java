package com.wuxiaozhi.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BulkAssignExperimentsRequest {
    private String className;
    private String instructions;
    private java.time.LocalDateTime reportDueAt;
    @NotEmpty
    private List<Long> userIds = new ArrayList<>();

    private List<String> experimentCodes = new ArrayList<>();

    /** replace=覆盖分配；append=追加实验（默认 append） */
    private String mode = "append";
}
