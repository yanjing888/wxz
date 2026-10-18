package com.wuxiaozhi.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiToolDefinition {
    private String code;
    private String name;
    private String description;
    /** chat | pre-lab | quiz | data-lab | report-review | recap */
    private String pageType = "chat";
    private String group = "拓展学习";
    private String workflowKey;
    private String category = "general";
    /** 是否对学生端展示/开放，默认 true */
    private boolean studentVisible = true;
    private List<String> suggestions = new ArrayList<>();
}
