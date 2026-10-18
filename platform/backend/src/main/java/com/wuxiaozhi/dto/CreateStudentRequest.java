package com.wuxiaozhi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CreateStudentRequest {
    @NotBlank
    private String username;

    @NotBlank
    private String displayName;

    @Size(min = 6, max = 128)
    private String password;

    private String studentClass;

    /** 创建后立即分配的实验 code 列表 */
    private List<String> experimentCodes = new ArrayList<>();

    /** true=追加到已有实验；false=覆盖为该列表 */
    private boolean appendExperiments = true;
}
