package com.wuxiaozhi.dto;

import lombok.Data;

@Data
public class MechanicalScaleRecognizeRequest {
    private String imageUrl;
    private String fieldKey;
    private String fieldLabel;
    /** vertical | horizontal */
    private String scaleAxis;
    /** down | up | right | left */
    private String scaleDirection;
    private Double scaleUnitPerTick;
    private Double scaleZeroValue;
}
