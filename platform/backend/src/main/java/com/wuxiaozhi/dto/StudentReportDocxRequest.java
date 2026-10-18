package com.wuxiaozhi.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class StudentReportDocxRequest {
    /** 每项含 label 与 content，顺序即报告章节顺序 */
    private List<Map<String, String>> sections = new ArrayList<>();
}
