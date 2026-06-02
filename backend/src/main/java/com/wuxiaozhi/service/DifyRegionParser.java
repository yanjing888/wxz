package com.wuxiaozhi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiaozhi.dto.experiment.MarkDto;

import java.util.ArrayList;
import java.util.List;

/**
 * 解析 Dify 工作流中的 regions（topLeftX/Y、bottomRightX/Y，0–1000 归一化坐标）。
 */
public final class DifyRegionParser {

    private static final int MAX = 1000;

    private DifyRegionParser() {
    }

    public static List<MarkDto> fromOutputs(JsonNode outputs, ObjectMapper objectMapper) {
        if (outputs == null || outputs.isNull()) {
            return List.of();
        }
        List<MarkDto> fromRegions = parseRegionsNode(outputs.get("regions"), objectMapper);
        if (!fromRegions.isEmpty()) {
            return fromRegions;
        }
        JsonNode text = outputs.get("text");
        if (text != null && text.isTextual()) {
            return fromAnalysisJson(text.asText(), objectMapper);
        }
        return List.of();
    }

    public static List<MarkDto> fromAnalysisJson(String text, ObjectMapper objectMapper) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String trimmed = text.trim();
        try {
            if (trimmed.startsWith("{")) {
                return parseRegionsNode(objectMapper.readTree(trimmed).get("regions"), objectMapper);
            }
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return parseRegionsNode(objectMapper.readTree(trimmed.substring(start, end + 1)).get("regions"), objectMapper);
            }
        } catch (Exception ignored) {
        }
        return List.of();
    }

    public static List<MarkDto> parseRegionsNode(JsonNode regionsNode, ObjectMapper objectMapper) {
        if (regionsNode == null || regionsNode.isNull()) {
            return List.of();
        }
        JsonNode array = regionsNode;
        if (regionsNode.isTextual()) {
            try {
                array = objectMapper.readTree(regionsNode.asText());
            } catch (Exception e) {
                return List.of();
            }
        }
        if (!array.isArray()) {
            return List.of();
        }
        List<MarkDto> marks = new ArrayList<>();
        int n = 1;
        for (JsonNode item : array) {
            MarkDto mark = toMark(item, n++);
            if (mark != null) {
                marks.add(mark);
            }
        }
        return marks;
    }

    private static MarkDto toMark(JsonNode item, int n) {
        if (item == null || !item.isObject()) {
            return null;
        }
        int topLeftX = intVal(item, "topLeftX", "top_left_x", "x1", "x");
        int topLeftY = intVal(item, "topLeftY", "top_left_y", "y1", "y");
        int bottomRightX = intVal(item, "bottomRightX", "bottom_right_x", "x2");
        int bottomRightY = intVal(item, "bottomRightY", "bottom_right_y", "y2");
        int w = intVal(item, "w", "width");
        int h = intVal(item, "h", "height");

        if (bottomRightX > topLeftX && bottomRightY > topLeftY) {
            return clampMark(topLeftX, topLeftY, bottomRightX - topLeftX, bottomRightY - topLeftY, n);
        }
        if (w > 0 && h > 0) {
            return clampMark(topLeftX, topLeftY, w, h, n);
        }
        return null;
    }

    private static int intVal(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode v = node.get(field);
            if (v != null && !v.isNull()) {
                if (v.isNumber()) {
                    return v.intValue();
                }
                if (v.isTextual()) {
                    try {
                        return Integer.parseInt(v.asText().trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return -1;
    }

    private static MarkDto clampMark(int x, int y, int w, int h, int n) {
        if (x < 0 || y < 0 || w <= 0 || h <= 0) {
            return null;
        }
        MarkDto mark = new MarkDto();
        mark.setX(Math.min(x, MAX));
        mark.setY(Math.min(y, MAX));
        mark.setW(Math.max(1, Math.min(w, MAX - mark.getX())));
        mark.setH(Math.max(1, Math.min(h, MAX - mark.getY())));
        mark.setN(n);
        return mark;
    }
}
