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
}
