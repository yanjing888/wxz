package com.wuxiaozhi.dto;

import lombok.Data;

@Data
public class EnvCheckResponse {
    private String level;
    private String summary;
    private String suggestion;
    private String snapshotUrl;
    private boolean fromDify;
}
