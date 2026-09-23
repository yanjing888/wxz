package com.wuxiaozhi.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class ExperimentProgressDto {
    private String experimentCode;
    private String experimentName;
    private String currentStep;
    private Long activeSessionId;
    private Long finishedSessionId;
    private boolean dataCollectionEnabled;
    private boolean dataSubmitted;
    /** 进门就绪：预习自测通过或已标记预习完成 */
    private boolean preLabCompleted;
    /** 虚拟仿真预习已完成 */
    private boolean simulationCompleted;
    /** 该实验是否配置为必须完成仿真后才能进实验台 */
    private boolean simulationRequired;
    /** 仿真页面 URL（相对前端 public 根路径） */
    private String simulationUrl;
    /** 已点击「结束实验」，实验台仅可查看历史对话 */
    private boolean labCompleted;
    private boolean reportCompleted;
    private boolean recapCompleted;
    private Map<String, String> reportSections = new LinkedHashMap<>();
    private List<ExperimentStepProgressDto> steps = new ArrayList<>();
}
