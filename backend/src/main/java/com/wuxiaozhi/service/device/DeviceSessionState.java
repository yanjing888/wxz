package com.wuxiaozhi.service.device;

import com.wuxiaozhi.dto.device.TelemetrySampleDto;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

@Getter
@Setter
public class DeviceSessionState {
    private Long sessionId;
    private int stepId;
    private String deviceType;
    private String deviceName;
    private String state = "idle";
    private int samplingHz = 10;
    private boolean connected;

    private Map<String, Object> specimen = new LinkedHashMap<>();
    private Map<String, Object> snapshot = new LinkedHashMap<>();
    private final List<TelemetrySampleDto> curve = new ArrayList<>();

    private final AtomicBoolean acquiring = new AtomicBoolean(false);
    private Future<?> streamTask;

    public void resetForStep(int stepId, String deviceType, String deviceName, int hz) {
        this.stepId = stepId;
        this.deviceType = deviceType;
        this.deviceName = deviceName;
        this.samplingHz = hz;
        this.state = "ready";
        this.connected = true;
        this.snapshot.clear();
        this.curve.clear();
        this.acquiring.set(false);
        cancelStream();
    }

    public void cancelStream() {
        if (streamTask != null) {
            streamTask.cancel(true);
            streamTask = null;
        }
        acquiring.set(false);
    }
}
