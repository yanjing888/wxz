package com.wuxiaozhi.service;

import com.wuxiaozhi.entity.StepAssessment;
import com.wuxiaozhi.entity.StoredUpload;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcessRecordExportTest {
    @Test void requiresFinishedSession() {
        assertThrows(ResponseStatusException.class, () -> new ReportService().generateProcessRecordDocx(Map.of("sessionStatus", "ACTIVE"), null));
    }
    @Test void exportsEditableRecordsBeforeReportSubmissionAndIncludesLatestReview() throws Exception {
        var files = mock(FileStorageService.class);
        var picture = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(20, 10, 1), "png", picture);
        var upload = new StoredUpload(); upload.setData(picture.toByteArray());
        when(files.read("/uploads/check.png")).thenReturn(upload);
        var record = new StepAssessment(); record.setStudentNote("调整仪器后提交");
        record.setEvidenceJson("""
          {"images":["/uploads/check.png"],"table":[["测量值","单位"],["12.34","mm"]]}
          """);
        var step = new HashMap<String, Object>(); step.put("stepId", 1); step.put("title", "测量"); step.put("maxScore", 20); step.put("status", "PENDING"); step.put("record", record);
        var assessment = Map.<String,Object>of("steps", List.of(step));
        var report = new HashMap<String, Object>(); report.put("sessionStatus", "FINISHED"); report.put("reportCompleted", false);
        report.put("experimentName", "牛顿环"); report.put("processAssessment", assessment);
        report.put("dataLogEntries", List.of(Map.of("stepId", 1, "stepTitle", "测量", "values", Map.of("diameter", 12.34), "submittedAt", "2026-10-29 12:00:00")));
        report.put("stepSchemas", Map.of("1", Map.of("fields", List.of(Map.of("key", "diameter", "label", "直径（mm）")))));
        for (boolean reviewed : List.of(false, true)) {
            if (reviewed) { record.setStatus("PASSED"); record.setReviewedAt(java.time.LocalDateTime.now()); record.setComment("后续检查通过"); record.setScore(18.0); step.put("status", "PASSED"); }
            report.put("processCheckRecords", ProcessReportHistory.from(assessment));
            byte[] bytes = new ReportService().generateProcessRecordDocx(report, files);
            try (var doc = new XWPFDocument(new ByteArrayInputStream(bytes)); var text = new XWPFWordExtractor(doc)) {
                assertTrue(text.getText().contains("实验过程记录"));
                assertTrue(text.getText().contains("直径（mm）：12.34"));
                assertTrue(text.getText().contains(reviewed ? "后续检查通过" : "待检查"));
                assertFalse(text.getText().contains("知识巩固建议"));
                assertFalse(text.getText().contains("附录 过程检查记录汇总"));
                assertEquals(1, text.getText().split("步骤 1  测量", -1).length - 1);
                assertEquals(1, doc.getAllPictures().size());
                assertTrue(doc.getTables().size() >= 3);
            }
            assertEquals(false, report.get("reportCompleted"));
        }
    }
}
