package com.wuxiaozhi.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StartSessionRequest {
    @NotBlank
    private String experimentCode;

    @NotBlank
    private String studentName;

    private String studentClass;
}
