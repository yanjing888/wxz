package com.wuxiaozhi.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiToolItemDto {
    private String code;
    private String name;
    private String description;
    private String pageType;
    private String group;
    private String workflowKey;
    private boolean available;
    private List<String> suggestions = new ArrayList<>();
}
