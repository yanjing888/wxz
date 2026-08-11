package com.wuxiaozhi.controller;

import com.wuxiaozhi.dto.*;
import com.wuxiaozhi.entity.User;
import com.wuxiaozhi.security.AuthSupport;
import com.wuxiaozhi.service.TeacherService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/teacher")
public class TeacherController {

    private final TeacherService teacherService;

    public TeacherController(TeacherService teacherService) {
        this.teacherService = teacherService;
    }

    @GetMapping("/overview")
    public TeacherOverviewDto overview(Authentication authentication) {
        User teacher = teacherService.requireTeacher(AuthSupport.currentUserId(authentication));
        return teacherService.overview(teacher);
    }

    @GetMapping("/reports")
    public List<TeacherReportItemDto> reports(@RequestParam(required = false) String experimentCode,
                                              Authentication authentication) {
        User teacher = teacherService.requireTeacher(AuthSupport.currentUserId(authentication));
        return teacherService.listReports(teacher, experimentCode);
    }

    @GetMapping("/reports/{sessionId}")
    public Map<String, Object> report(@PathVariable Long sessionId, Authentication authentication) {
        User teacher = teacherService.requireTeacher(AuthSupport.currentUserId(authentication));
        return teacherService.getReport(teacher, sessionId);
    }

    @GetMapping("/reports/{sessionId}/docx")
    public ResponseEntity<byte[]> reportDocx(@PathVariable Long sessionId, Authentication authentication) throws Exception {
        User teacher = teacherService.requireTeacher(AuthSupport.currentUserId(authentication));
        Map<String, Object> data = teacherService.getReport(teacher, sessionId);
        byte[] bytes = teacherService.getReportDocx(teacher, sessionId);
        String filename = URLEncoder.encode("实验总结报告-" + data.get("experimentName") + ".docx", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(bytes);
    }

    @GetMapping("/feedback")
    public List<TeacherFeedbackItemDto> feedback(@RequestParam(required = false) String rating,
                                                @RequestParam(required = false) Boolean processed,
                                                @RequestParam(required = false) String experimentCode,
                                                Authentication authentication) {
        User teacher = teacherService.requireTeacher(AuthSupport.currentUserId(authentication));
        return teacherService.listFeedback(teacher, rating, processed, experimentCode);
    }

    @PatchMapping("/feedback/{feedbackId}/processed")
    public TeacherFeedbackItemDto markProcessed(@PathVariable Long feedbackId, Authentication authentication) {
        User teacher = teacherService.requireTeacher(AuthSupport.currentUserId(authentication));
        return teacherService.markFeedbackProcessed(teacher, feedbackId);
    }

    @GetMapping("/students")
    public List<TeacherStudentItemDto> students(Authentication authentication) {
        User teacher = teacherService.requireTeacher(AuthSupport.currentUserId(authentication));
        return teacherService.listStudents(teacher);
    }

    @PostMapping("/students/import")
    public ImportStudentsResult importStudents(@Valid @RequestBody ImportStudentsRequest request,
                                               Authentication authentication) {
        User teacher = teacherService.requireTeacher(AuthSupport.currentUserId(authentication));
        return teacherService.importStudents(teacher, request);
    }

    @PutMapping("/students/{userId}/experiments")
    public TeacherStudentItemDto assignExperiments(@PathVariable Long userId,
                                                   @RequestBody AssignExperimentsRequest request,
                                                   Authentication authentication) {
        User teacher = teacherService.requireTeacher(AuthSupport.currentUserId(authentication));
        return teacherService.assignExperiments(teacher, userId, request);
    }

    @PostMapping("/students/assignments/bulk")
    public Map<String, Object> bulkAssignExperiments(@Valid @RequestBody BulkAssignExperimentsRequest request,
                                                     Authentication authentication) {
        User teacher = teacherService.requireTeacher(AuthSupport.currentUserId(authentication));
        int count = teacherService.bulkAssignExperiments(teacher, request);
        return Map.of("updated", count);
    }
}
