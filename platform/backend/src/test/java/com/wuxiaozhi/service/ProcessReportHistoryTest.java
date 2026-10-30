package com.wuxiaozhi.service;

import com.wuxiaozhi.entity.StepAssessment;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProcessReportHistoryTest {
    Map<String, Object> step(int id, StepAssessment record) {
        return Map.of("stepId", id, "title", "步骤名称" + id, "maxScore", 20, "record", record);
    }
    @Test void retainsReturnsAndResubmissionsInOrderAndExportsBothReports() throws Exception {
        var record = new StepAssessment();
        record.setHistoryJson("""
          [{"action":"REVIEWED","at":"2026-10-29T10:30:00","status":"PASSED","score":18,"note":"复查通过"},
           {"action":"SUBMITTED","at":"2026-10-29T10:00:00","status":"PENDING","note":"初次提交"},
           {"action":"REVIEWED","at":"2026-10-29T10:10:00","status":"REVISION","score":8,"note":"需要重新调焦"},
           {"action":"SUPPLEMENTED","at":"2026-10-29T10:20:00","status":"PENDING","note":"已调整"}]
          """);
        record.setReviewedAt(LocalDateTime.now()); record.setComment("不应重复的当前记录");
        var rows = ProcessReportHistory.from(Map.of("steps", List.of(step(2, record))));
        assertEquals(4, rows.size());
        assertEquals("提交检查", rows.get(0).get("result"));
        assertEquals("退回整改", rows.get(1).get("result"));
        assertNull(rows.get(2).get("score"));
        assertEquals("检查通过", rows.get(3).get("result"));
        var data = new HashMap<String, Object>(); data.put("processCheckRecords", rows); data.put("experimentName", "牛顿环实验");
        var service = new ReportService();
        for (byte[] bytes : List.of(service.generateDocx(data), service.generateStudentReportDocx(data, List.of(Map.of("label", "实验目的", "content", "学生原有正文"))))) {
            try (var doc = new XWPFDocument(new ByteArrayInputStream(bytes)); var extractor = new XWPFWordExtractor(doc)) {
                String text = extractor.getText();
                assertTrue(text.contains("附录 过程检查记录汇总"));
                assertTrue(text.contains("需要重新调焦")); assertTrue(text.contains("复查通过"));
                assertFalse(text.contains("不应重复的当前记录"));
                assertTrue(text.indexOf("需要重新调焦") < text.indexOf("复查通过"));
                assertTrue(doc.getTables().stream().anyMatch(t -> t.getRow(0).isRepeatHeader()));
            }
        }
    }
    @Test void legacyReviewIsIncludedWithoutFabricatingHistory() {
        var record = new StepAssessment(); record.setStatus("PASSED"); record.setScore(20.0);
        record.setReviewedAt(LocalDateTime.of(2026, 10, 29, 12, 0)); record.setComment("已检查");
        var rows = ProcessReportHistory.from(Map.of("steps", List.of(step(1, record))));
        assertEquals(1, rows.size()); assertEquals("已检查", rows.get(0).get("note"));
        assertTrue(ProcessReportHistory.from(Map.of("steps", List.of(step(1, new StepAssessment())))).isEmpty());
    }
}
