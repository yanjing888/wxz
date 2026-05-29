package com.wuxiaozhi.dto;

import com.wuxiaozhi.dto.experiment.MarkDto;
import lombok.Data;

import java.util.List;

@Data
public class AssistResponse {
    private String type;
    private String feedback;
    private String errorType;
    private String detail;
    private List<MarkDto> marks;
    private boolean fromDify;
}
