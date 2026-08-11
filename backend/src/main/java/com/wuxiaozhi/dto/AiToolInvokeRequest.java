package com.wuxiaozhi.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AiToolInvokeRequest {
    private String action = "run";
    private Map<String, Object> inputs = new LinkedHashMap<>();
    /** 上传接口返回的图片地址，供读数助手等多模态智能体使用 */
    private String imageUrl;
}
