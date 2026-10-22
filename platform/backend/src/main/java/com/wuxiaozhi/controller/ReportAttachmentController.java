package com.wuxiaozhi.controller;

import com.wuxiaozhi.dto.ExperimentProgressDto;
import com.wuxiaozhi.repository.LabSessionRepository;
import com.wuxiaozhi.security.AuthSupport;
import com.wuxiaozhi.service.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;

@RestController
public class ReportAttachmentController {
    private final ReportAttachmentService attachments;
    private final StudentExperimentService students;
    private final TeacherService teachers;
    private final LabSessionRepository sessions;
    public ReportAttachmentController(ReportAttachmentService attachments, StudentExperimentService students, TeacherService teachers, LabSessionRepository sessions) {
        this.attachments = attachments; this.students = students; this.teachers = teachers; this.sessions = sessions;
    }
    @PostMapping(value = "/api/student/experiments/{code}/report-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ExperimentProgressDto upload(@PathVariable String code, @RequestParam Long sessionId,
                                        @RequestPart("file") MultipartFile file, Authentication auth) throws IOException {
        Long userId = AuthSupport.currentUserId(auth);
        attachments.submit(userId, code, sessionId, file);
        return students.getProgress(userId, code);
    }
    @GetMapping("/api/student/experiments/{code}/report-file")
    public ResponseEntity<byte[]> studentFile(@PathVariable String code, @RequestParam Long sessionId, Authentication auth) throws IOException {
        var session = sessions.findByIdAndUserId(sessionId, AuthSupport.currentUserId(auth))
                .filter(s -> code.equals(s.getExperimentCode())).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "报告不存在"));
        return attachments.response(session);
    }
    @GetMapping("/api/teacher/reports/{sessionId}/file")
    public ResponseEntity<byte[]> teacherFile(@PathVariable Long sessionId, Authentication auth) throws IOException {
        return attachments.response(teachers.getSessionForTeacher(teachers.requireTeacher(AuthSupport.currentUserId(auth)), sessionId));
    }
}
