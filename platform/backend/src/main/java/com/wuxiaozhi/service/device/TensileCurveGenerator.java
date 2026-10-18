package com.wuxiaozhi.service.device;

import com.wuxiaozhi.dto.device.TelemetrySampleDto;

import java.util.ArrayList;
import java.util.List;

/** 低碳钢典型 σ-ε 曲线（工程应力应变） */
public final class TensileCurveGenerator {

    private final double dMm;
    private final double l0Mm;
    private final double areaMm2;
    private static final double E_MPA = 210_000;

    public TensileCurveGenerator(double dMm, double l0Mm) {
        this.dMm = dMm > 0 ? dMm : 10.0;
        this.l0Mm = l0Mm > 0 ? l0Mm : 100.0;
        this.areaMm2 = Math.PI * this.dMm * this.dMm / 4.0;
    }

    public List<TelemetrySampleDto> buildCurve(int pointCount) {
        int n = Math.max(pointCount, 80);
        double maxStrain = 0.26;
        List<TelemetrySampleDto> list = new ArrayList<>(n + 1);
        for (int i = 0; i <= n; i++) {
            double strain = maxStrain * i / n;
            double stress = stressAt(strain);
            double forceKn = stress * areaMm2 / 1000.0;
            double strainPct = strain * 100.0;
            double dispMm = strain * l0Mm;
            double t = i * 0.08;
            list.add(new TelemetrySampleDto(t, round(forceKn, 3), round(strainPct, 4), round(stress, 2), round(dispMm, 3)));
        }
        return list;
    }

    private double stressAt(double strain) {
        if (strain < 0.00115) {
            return E_MPA * strain;
        }
        if (strain < 0.022) {
            return 235;
        }
        if (strain < 0.11) {
            return 235 + (strain - 0.022) * 1950;
        }
        if (strain < 0.19) {
            return 406 - (strain - 0.11) * 950;
        }
        return Math.max(180, 338 - (strain - 0.19) * 2200);
    }

    private static double round(double v, int scale) {
        double p = Math.pow(10, scale);
        return Math.round(v * p) / p;
    }

    public double getAreaMm2() {
        return areaMm2;
    }

    public double getL0Mm() {
        return l0Mm;
    }

    public double getDMm() {
        return dMm;
    }
}
