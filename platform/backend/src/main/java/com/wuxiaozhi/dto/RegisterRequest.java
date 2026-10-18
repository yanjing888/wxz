package com.wuxiaozhi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank
    @Size(min = 3, max = 64)
    private String username;

    @NotBlank
    private String password;

    @NotBlank
    @Size(max = 64)
    private String displayName;

    @Size(max = 64)
    private String studentClass;
}
