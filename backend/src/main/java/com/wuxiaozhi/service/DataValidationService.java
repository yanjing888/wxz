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
            case "newton_rings" -> validateNewtonRings(step, values, result);
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

    private void validateNewtonRings(StepConfig step, Map<String, Object> values, DataValidationResult result) {
        String title = step.getTitle() != null ? step.getTitle() : "";
        if (title.contains("暗环直径")) {
            Double left = num(values, "reading_left_mm");
            Double right = num(values, "reading_right_mm");
            Double d = num(values, "diameter_D_mm");
            if (left != null && right != null && d != null) {
                double expected = Math.abs(right - left);
                if (Math.abs(d - expected) > 0.05) {
                    result.getWarnings().add("直径 Dₘ 与左右读数差不一致，请复核计算");
                }
            }
            warnIf(result, num(values, "ring_m"), 1, 50, "暗环序号 m 通常在 1～50 范围内");
        }
        if (title.contains("计算")) {
            Double lambda = num(values, "lambda_nm");
            if (lambda != null && (lambda < 400 || lambda > 700)) {
                result.getWarnings().add("波长 λ 数量级偏离可见光常用范围，请核对公式与单位");
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
