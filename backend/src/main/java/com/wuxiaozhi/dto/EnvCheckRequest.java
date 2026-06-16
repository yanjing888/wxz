package com.wuxiaozhi.dto;

import lombok.Data;

@Data
public class EnvCheckRequest {
    /** 本次巡检抽帧画面 URL（/uploads/...） */
    private String snapshotUrl;
}
