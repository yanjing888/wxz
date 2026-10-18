package com.wuxiaozhi.dto.experiment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SimulationConfig {
    /** 静态仿真页面路径，如 /newton-rings-simulation/index.html */
    private String url;
    /** 是否必须完成仿真后才能进入真实实验台 */
    private Boolean required = false;
    private String title;
}
