package com.wuxiaozhi.dto.experiment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StepConfig {
    private String title;
    private String desc;
    private String guidePath;
    /** data：表单采集纠错；vision：拍照纠错 */
    private String correctionMode;
    /** device：仪器自动采集；缺省为手填 */
    private String dataSource;
    /** dimension_measure | universal_tester | post_measure | reading_microscope | newton_analyzer */
    private String deviceType;
    private List<DataFieldConfig> dataFields;
    private TutorialConfig tut;
    private AssistMock assistMock;
}
