package com.wuxiaozhi.dto.experiment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AssistMock {
    private String errorType;
    private String detail;
    private String feedback;
    private List<MarkDto> marks;
}
