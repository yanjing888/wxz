package com.wuxiaozhi.service;

import com.wuxiaozhi.dto.DataValidationResult;
import com.wuxiaozhi.dto.experiment.DataFieldConfig;
import com.wuxiaozhi.dto.experiment.ExperimentConfig;
import com.wuxiaozhi.dto.experiment.StepConfig;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DataValidationService {

    public DataValidationResult validate(ExperimentConfig exp, StepConfig step, Map<String, Object> values) {
        DataValidationResult result = new DataValidationResult();
        if (step == null || step.getDataFields() == null || step.getDataFields().isEmpty()) {
            result.setOk(false);
            result.getErrors().add("当前步骤未配置数据采集字段");
            return result;
        }

        for (DataFieldConfig field : step.getDataFields()) {
            validateField(field, values, result);
        }

        applyExperimentRules(exp.getCode(), step, values, result);
        result.setOk(result.getErrors().isEmpty());
        return result;
    }

    private void validateField(DataFieldConfig field, Map<String, Object> values, DataValidationResult result) {
        String key = field.getKey();
        if (key == null || key.isBlank()) {
            return;
        }
        Object raw = values != null ? values.get(key) : null;
        boolean missing = raw == null || String.valueOf(raw).isBlank();

        if (missing) {
            if (field.isRequired()) {
                result.getErrors().add(labelOf(field) + " 为必填项");
            }
            return;
        }

        if ("number".equalsIgnoreCase(field.getType())) {
            double num;
            try {
                num = Double.parseDouble(String.valueOf(raw).trim());
            } catch (NumberFormatException e) {
                result.getErrors().add(labelOf(field) + " 须为有效数字");
                return;
            }
            if (field.getMin() != null && num < field.getMin()) {
                result.getErrors().add(labelOf(field) + " 不应小于 " + field.getMin());
            }
            if (field.getMax() != null && num > field.getMax()) {
                result.getErrors().add(labelOf(field) + " 不应大于 " + field.getMax());
            }
        }
    }

    private void applyExperimentRules(String code, StepConfig step, Map<String, Object> values, DataValidationResult result) {
        if (code == null || values == null) {
            return;
        }
        switch (code) {
            case "tensile_steel" -> validateTensile(step, values, result);
            case "beam_bending" -> validateBeam(step, values, result);
            case "compression_modulus" -> validateCompression(step, values, result);
            default -> { }
        }
    }

    private void validateTensile(StepConfig step, Map<String, Object> values, DataValidationResult result) {
        String title = step.getTitle() != null ? step.getTitle() : "";
        if (title.contains("试样测量")) {
            warnIf(result, num(values, "d_mm"), 3, 20, "直径 d 数量级异常，请核对单位是否为 mm");
            warnIf(result, num(values, "L0_mm"), 20, 200, "标距 L₀ 数量级异常，请核对测量位置");
        }
        if (title.contains("断后")) {
            Double d = num(values, "d_mm");
            Double d1 = num(values, "d1_mm");
            if (d != null && d1 != null && d1 > d) {
                result.getWarnings().add("断后最小直径 d₁ 通常不应大于拉伸前直径 d");
            }
        }
    }

    private void validateBeam(StepConfig step, Map<String, Object> values, DataValidationResult result) {
        String title = step.getTitle() != null ? step.getTitle() : "";
        if (title.contains("跨距")) {
            warnIf(result, num(values, "L_mm"), 100, 800, "跨距 L 数量级异常，请确认单位为 mm");
        }
        if (title.contains("分级加载")) {
            Double p = num(values, "P_N");
            Double delta = num(values, "delta_mm");
            if (p != null && p < 0) {
                result.getErrors().add("荷载 P 不应为负");
            }
            if (delta != null && delta < 0) {
                result.getErrors().add("挠度 δ 不应为负");
            }
        }
    }

    private void validateCompression(StepConfig step, Map<String, Object> values, DataValidationResult result) {
        String title = step.getTitle() != null ? step.getTitle() : "";
        if (title.contains("试样") || title.contains("几何")) {
            Double h = num(values, "H_mm");
            Double d = num(values, "d_mm");
            if (h != null && d != null && h < d) {
                result.getWarnings().add("高度 H 小于直径 d，请确认试样取向与测量位置");
            }
        }
        if (title.contains("弹性模量") || title.contains("计算")) {
            Double e = num(values, "E_GPa");
            if (e != null && (e < 50 || e > 300)) {
                result.getWarnings().add("弹性模量 E 数量级偏离常见金属范围，请复核 ΔF、ΔL 与几何尺寸");
            }
        }
    }

    private void warnIf(DataValidationResult result, Double v, double lo, double hi, String msg) {
        if (v != null && (v < lo || v > hi)) {
            result.getWarnings().add(msg);
        }
    }

    private Double num(Map<String, Object> values, String key) {
        Object raw = values.get(key);
        if (raw == null || String.valueOf(raw).isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String labelOf(DataFieldConfig field) {
        if (field.getLabel() != null && !field.getLabel().isBlank()) {
            return field.getLabel();
        }
        return field.getKey();
    }
}
