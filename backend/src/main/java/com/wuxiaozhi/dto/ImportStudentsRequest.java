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

    /** 导入完成后自动分配到这些实验（默认追加，不覆盖已有分配） */
    private List<String> experimentCodes = new ArrayList<>();

    /** append=追加实验分配；replace=覆盖为该列表 */
    private String assignMode = "append";
}
