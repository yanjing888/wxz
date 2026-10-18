package com.wuxiaozhi.dto.device;

import lombok.Data;

import java.util.Map;

@Data
public class DeviceStatusDto {
    private boolean connected;
    /** idle | ready | acquiring | completed */
    private String state;
    private String deviceName;
    private String deviceType;
    private int samplingHz;
    private int stepId;
    private Map<String, Object> snapshot;
}
