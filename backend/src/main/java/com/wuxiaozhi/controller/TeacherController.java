package com.wuxiaozhi.controller;

import com.wuxiaozhi.dto.*;
import com.wuxiaozhi.entity.EnvCheckLog;
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

    @PostMapping("/reports/{sessionId}/ai-review")
    public AiToolInvokeResponse reviewReport(@PathVariable Long sessionId, Authentication authentication) {
        User teacher = teacherService.requireTeacher(AuthSupport.currentUserId(authentication));
        return teacherService.reviewReport(teacher, sessionId);
    }

    @PatchMapping("/reports/{sessionId}/grade")
    public Map<String, Object> completeGrade(@PathVariable Long sessionId,
                                             @Valid @RequestBody CompleteGradeRequest request,
                                             Authentication authentication) {
        User teacher = teacherService.requireTeacher(AuthSupport.currentUserId(authentication));
        return teacherService.completeGrade(teacher, sessionId, request);
    }

    @GetMapping("/classroom")
    public TeacherClassroomDto classroom(@RequestParam(required = false) String experimentCode,
                                         Authentication authentication) {
        User teacher = teacherService.requireTeacher(AuthSupport.currentUserId(authentication));
        return teacherService.classroom(teacher, experimentCode);
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
    public TeacherFeedbackItemDto markProcessed(@PathVariable Long feedbackId,
                                                @Valid @RequestBody ReviewFeedbackRequest request,
                                                Authentication authentication) {
        User teacher = teacherService.requireTeacher(AuthSupport.currentUserId(authentication));
        return teacherService.markFeedbackProcessed(teacher, feedbackId, request.getRating());
    }

    @GetMapping("/sessions/{sessionId}/env-logs")
    public List<EnvCheckLog> sessionEnvLogs(@PathVariable Long sessionId, Authentication authentication) {
        User teacher = teacherService.requireTeacher(AuthSupport.currentUserId(authentication));
        return teacherService.getEnvLogsForTeacher(teacher, sessionId);
    }

    @PatchMapping("/sessions/{sessionId}/env-check-enabled")
    public TeacherClassroomStudentDto setEnvCheckEnabled(@PathVariable Long sessionId,
                                                         @RequestBody UpdateEnvCheckEnabledRequest request,
                                                         Authentication authentication) {
        User teacher = teacherService.requireTeacher(AuthSupport.currentUserId(authentication));
        boolean enabled = request != null && request.isEnabled();
        return teacherService.setEnvCheckEnabled(teacher, sessionId, enabled);
    }

    @PostMapping("/sessions/{sessionId}/env-check")
    public EnvCheckResponse triggerEnvCheck(@PathVariable Long sessionId,
                                            @RequestBody(required = false) EnvCheckRequest request,
                                            Authentication authentication) {
        User teacher = teacherService.requireTeacher(AuthSupport.currentUserId(authentication));
        return teacherService.triggerEnvCheckForTeacher(teacher, sessionId, request);
    }

    @GetMapping("/students")
    public List<TeacherStudentItemDto> students(@RequestParam(required = false) String experimentCode,
                                                Authentication authentication) {
        User teacher = teacherService.requireTeacher(AuthSupport.currentUserId(authentication));
        return teacherService.listStudents(teacher, experimentCode);
    }

    @PostMapping("/students")
    public TeacherStudentItemDto createStudent(@Valid @RequestBody CreateStudentRequest request,
                                               Authentication authentication) {
        User teacher = teacherService.requireTeacher(AuthSupport.currentUserId(authentication));
        return teacherService.createStudent(teacher, request);
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

    @DeleteMapping("/students/{userId}/experiments/{experimentCode}")
    public ResponseEntity<Void> unassignExperiment(@PathVariable Long userId,
                                                   @PathVariable String experimentCode,
                                                   Authentication authentication) {
        User teacher = teacherService.requireTeacher(AuthSupport.currentUserId(authentication));
        teacherService.unassignExperiment(teacher, userId, experimentCode);
        return ResponseEntity.noContent().build();
    }
}
