package com.wuxiaozhi.dto.device;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TelemetrySampleDto {
    private double t;
    private double forceKn;
    private double strainPct;
    private double stressMpa;
    private double displacementMm;
}
