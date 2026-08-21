package com.wuxiaozhi.dto.experiment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataFieldConfig {
    private String key;
    private String label;
    /** number | text */
    private String type = "number";
    private String unit;
    private boolean required = true;
    private Double min;
    private Double max;
    private String placeholder;
    /** Optional formula such as abs(reading_right_mm - reading_left_mm). */
    private String computed;
    private boolean readOnly = false;
}
