package com.wuxiaozhi.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BulkAssignExperimentsRequest {
    @NotEmpty
    private List<Long> userIds = new ArrayList<>();

    private List<String> experimentCodes = new ArrayList<>();

    /** replace=覆盖分配；append=追加实验（默认 append） */
    private String mode = "append";
}
