package com.wuxiaozhi.dto.experiment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TutorialConfig {
    private List<String> steps;
    private List<String> warnings;
    /** 教程示意图 URL 列表（可多图切换） */
    private List<String> images;
    /** 教程演示视频 URL，可选 */
    private String videoUrl;
}
