package com.wuxiaozhi.dto;
import jakarta.validation.constraints.*;
import java.util.List;
public record AdminUserRequest(
    @NotBlank @Size(max=64) String username,
    @NotBlank @Size(max=64) String displayName,
    @NotNull @Pattern(regexp="ADMIN|TEACHER|STUDENT") String role,
    @Size(max=64) String studentClass,
    @Size(max=20) List<@NotBlank @Size(max=64) String> classes,
    boolean enabled,
    @Size(max=128) String password
) {}
