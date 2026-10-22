package com.wuxiaozhi.controller;

import com.wuxiaozhi.security.AuthSupport;
import com.wuxiaozhi.service.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/teacher/evaluation/exports")
public class TeacherEvaluationExportController {
    private final TeacherService teachers;
    private final TeacherEvaluationExportService exports;
    public TeacherEvaluationExportController(TeacherService teachers, TeacherEvaluationExportService exports) { this.teachers = teachers; this.exports = exports; }
    @GetMapping("/grades")
    public ResponseEntity<byte[]> grades(@RequestParam String experimentCode, Authentication auth) throws Exception {
        var teacher = teachers.requireTeacher(AuthSupport.currentUserId(auth));
        return ResponseEntity.ok().header("Content-Disposition", disposition(experimentCode + "-实验成绩.xlsx"))
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(exports.grades(teacher, experimentCode));
    }
    @GetMapping("/reports")
    public ResponseEntity<StreamingResponseBody> reports(@RequestParam String experimentCode, Authentication auth) throws Exception {
        var teacher = teachers.requireTeacher(AuthSupport.currentUserId(auth));
        var path = exports.reportArchive(teacher, experimentCode);
        return ResponseEntity.ok().header("Content-Disposition", disposition(experimentCode + "-全部实验报告.zip"))
                .contentLength(Files.size(path)).contentType(MediaType.parseMediaType("application/zip"))
                .body(output -> { try (var input = Files.newInputStream(path)) { input.transferTo(output); } finally { Files.deleteIfExists(path); } });
    }
    private String disposition(String filename) { return ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString(); }
}
