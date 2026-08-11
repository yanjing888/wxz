package com.wuxiaozhi.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ImportStudentsRequest {
    /** 未在行内指定密码时使用的默认密码 */
    @Size(min = 6, max = 128)
    private String defaultPassword = "123456";

    /** CSV 文本：username,displayName,password（可选列） */
    private String csv;

    @Valid
    private List<ImportStudentRow> students = new ArrayList<>();
}
