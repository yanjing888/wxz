package com.wuxiaozhi.dto.experiment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StepConfig {
    private String title;
    private String desc;
    private TutorialConfig tut;
    private AssistMock assistMock;
}
