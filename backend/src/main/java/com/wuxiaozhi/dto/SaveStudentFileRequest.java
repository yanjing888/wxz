package com.wuxiaozhi.dto;

import lombok.Data;

/** 登记一个已上传到 /uploads 的文件，或由前端生成后回传的图表 */
@Data
public class SaveStudentFileRequest {
    private String experimentCode;
    private String category;
    private String stage;
    private String fileName;
    private String url;
    private Long sessionId;
    private String note;
}
