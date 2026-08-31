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
    /** Whether this field should show the photo reading helper in the manual entry panel. */
    private boolean photoAssist = true;
    /** Whether this field should use local mechanical scale recognition instead of AI reading. */
    private boolean scaleReading = false;
    /** vertical | horizontal. The scale direction in the guided capture. */
    private String scaleAxis;
    /** down | up | right | left. Direction in which values increase from the zero tick. */
    private String scaleDirection;
    /** Physical value represented by one minor tick. */
    private Double scaleUnitPerTick;
    /** Value at the first visible major zero tick; defaults to 0. */
    private Double scaleZeroValue;
}
