package com.wuxiaozhi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ImportStudentRow {
    @NotBlank
    @Size(max = 64)
    private String username;

    @NotBlank
    @Size(max = 64)
    private String displayName;

    @Size(max = 128)
    private String password;

    @Size(max = 64)
    private String studentClass;
}
