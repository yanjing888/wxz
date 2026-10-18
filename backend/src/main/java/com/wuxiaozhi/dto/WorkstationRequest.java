package com.wuxiaozhi.dto;
import jakarta.validation.constraints.*;

public record WorkstationRequest(
    @NotBlank @Size(max=32) String code,
    @NotBlank @Size(max=80) String name,
    @Min(1) @Max(4) int capacity,
    boolean enabled,
    @Size(max=64) String cameraCode,
    @Pattern(regexp="NETWORK|USB") @NotNull String cameraType,
    @Size(max=1024) String streamUrl,
    @Size(max=1024) String snapshotUrl,
    @Size(max=128) String cameraUsername,
    @Size(max=256) String cameraPassword
) {}
