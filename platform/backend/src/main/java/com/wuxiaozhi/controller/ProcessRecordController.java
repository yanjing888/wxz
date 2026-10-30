package com.wuxiaozhi.controller;

import com.wuxiaozhi.security.AuthSupport;
import com.wuxiaozhi.service.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
public class ProcessRecordController {
    private final LabSessionService sessions;
    private final TeacherService teachers;
    private final ReportService reports;
    private final FileStorageService files;
    public ProcessRecordController(LabSessionService sessions, TeacherService teachers, ReportService reports, FileStorageService files) {
        this.sessions = sessions; this.teachers = teachers; this.reports = reports; this.files = files;
    }
    @GetMapping("/api/sessions/{sessionId}/process-record/docx")
    public ResponseEntity<byte[]> student(@PathVariable Long sessionId, Authentication auth) throws Exception {
        return document(sessions.buildReportData(sessionId, AuthSupport.currentUserId(auth)));
    }
    @GetMapping("/api/teacher/reports/{sessionId}/process-record/docx")
    public ResponseEntity<byte[]> teacher(@PathVariable Long sessionId, Authentication auth) throws Exception {
        return document(teachers.getReport(teachers.requireTeacher(AuthSupport.currentUserId(auth)), sessionId));
    }
    private ResponseEntity<byte[]> document(Map<String, Object> data) throws Exception {
        byte[] bytes = reports.generateProcessRecordDocx(data, files);
        String filename = URLEncoder.encode("实验过程记录-" + data.get("experimentName") + "-" + data.get("studentName") + ".docx", StandardCharsets.UTF_8);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")).body(bytes);
    }
}
