package com.wuxiaozhi.dto;

import jakarta.validation.constraints.*;

/** 最小设备配置，不要求终端配对、课次或学生信息。 */
public record SimpleWorkstationRequest(
    @NotBlank @Size(max=32) String code,
    @NotBlank @Size(max=80) String name,
    @Size(max=1024) String address,
    boolean enabled
) {}
