package com.wuxiaozhi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiaozhi.entity.StepAssessment;
import java.util.*;

/** Read-only report appendix, derived from persisted assessment history. */
public final class ProcessReportHistory {
    private static final ObjectMapper JSON = new ObjectMapper();
    private ProcessReportHistory() {}

    public static List<Map<String, Object>> from(Map<String, Object> assessment) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!(assessment.get("steps") instanceof List<?> steps)) return result;
        for (Object value : steps) {
            if (!(value instanceof Map<?, ?> step) || !(step.get("record") instanceof StepAssessment record)) continue;
            boolean hasReview = false;
            try {
                var events = JSON.readTree(record.getHistoryJson() == null ? "[]" : record.getHistoryJson());
                if (events.isArray()) for (var event : events) {
                    String action = event.path("action").asText();
                    if (!Set.of("SUBMITTED", "SUPPLEMENTED", "REVIEWED").contains(action)) continue;
                    hasReview |= "REVIEWED".equals(action);
                    result.add(row(step, event.path("at").asText(), action, event.path("status").asText(),
                            event.path("score").isNumber() ? event.get("score").asDouble() : null,
                            event.path("note").asText("")));
                }
            } catch (Exception ignored) { /* Legacy records can still supply their last saved review. */ }
            if (!hasReview && record.getReviewedAt() != null) {
                result.add(row(step, record.getReviewedAt().toString(), "REVIEWED", record.getStatus(), record.getScore(), record.getComment()));
            }
        }
        result.sort(Comparator.<Map<String, Object>>comparingInt(r -> ((Number) r.get("stepId")).intValue())
                .thenComparing(r -> String.valueOf(r.get("time"))));
        return result;
    }
    private static Map<String, Object> row(Map<?, ?> step, String at, String action, String status, Double score, String note) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("stepId", step.get("stepId")); row.put("stepTitle", step.get("title"));
        row.put("time", at); row.put("action", action);
        row.put("result", "SUBMITTED".equals(action) ? "提交检查" : "SUPPLEMENTED".equals(action) ? "补充材料" : "PASSED".equals(status) ? "检查通过" : "退回整改");
        row.put("score", "REVIEWED".equals(action) ? score : null); row.put("maxScore", step.get("maxScore"));
        row.put("note", note == null ? "" : note);
        return row;
    }
}
