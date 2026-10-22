package com.wuxiaozhi.controller;

import com.wuxiaozhi.dto.*;
import com.wuxiaozhi.entity.*;
import com.wuxiaozhi.repository.LabSessionRepository;
import com.wuxiaozhi.security.AuthSupport;
import com.wuxiaozhi.service.*;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import java.util.*;

@RestController
public class ProcessAssessmentController {
    private final ProcessAssessmentService process;
    private final LabSessionService sessions;
    private final TeacherService teachers;
    private final ClassRosterService roster;
    private final LabSessionRepository repository;
    private final TeacherTeachingContextService teachingContexts;

    public ProcessAssessmentController(ProcessAssessmentService process, LabSessionService sessions,
                                       TeacherService teachers, ClassRosterService roster, LabSessionRepository repository,
                                       TeacherTeachingContextService teachingContexts) {
        this.process = process; this.sessions = sessions; this.teachers = teachers; this.roster = roster; this.repository = repository;
        this.teachingContexts = teachingContexts;
    }
    @GetMapping("/api/sessions/{sessionId}/process")
    public Map<String, Object> student(@PathVariable Long sessionId, Authentication auth) {
        return process.summary(sessions.getSession(sessionId, AuthSupport.currentUserId(auth)));
    }
    @PostMapping(value = "/api/sessions/{sessionId}/steps/{stepId}/complete", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> complete(@PathVariable Long sessionId, @PathVariable int stepId,
                                       @Valid @RequestBody StepCompleteRequest request, Authentication auth) {
        return process.complete(sessions.getSession(sessionId, AuthSupport.currentUserId(auth)), stepId, request.getNote(), List.of(), request.getTable());
    }
    @PostMapping(value = "/api/sessions/{sessionId}/steps/{stepId}/complete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> completeWithImages(@PathVariable Long sessionId, @PathVariable int stepId,
                                                @Valid @RequestPart("submission") StepCompleteRequest request,
                                                @RequestPart(value = "images", required = false) List<MultipartFile> images,
                                                Authentication auth) {
        return process.complete(sessions.getSession(sessionId, AuthSupport.currentUserId(auth)), stepId,
                request.getNote(), images == null ? List.of() : images, request.getTable());
    }
    @GetMapping("/api/teacher/process")
    public List<Map<String, Object>> queue(@RequestParam(required = false) String experimentCode,
                                          @RequestParam(required = false) String className, Authentication auth) {
        User teacher = teachers.requireTeacher(AuthSupport.currentUserId(auth));
        String scope = roster.scope(teacher, className);
        if (experimentCode != null && !experimentCode.isBlank()) {
            String current = (String) teachingContexts.get(teacher, experimentCode).get("className");
            if (current.isBlank()) return List.of();
            if (className != null && !className.isBlank() && !current.equals(className.trim()))
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "本次教学班已变更，请刷新页面");
            scope = roster.scope(teacher, current);
        }
        var students = roster.students(teacher, scope);
        if (students.isEmpty()) return List.of();
        var all = repository.findByUserIdIn(students.stream().map(User::getId).toList());
        all.sort(Comparator.comparing(LabSession::getStartTime).reversed().thenComparing(LabSession::getId, Comparator.reverseOrder()));
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (LabSession session : all) {
            if (experimentCode != null && !experimentCode.isBlank() && !experimentCode.equals(session.getExperimentCode())) continue;
            if (!scope.isBlank() && !scope.equals(teachers.resolvedSessionClass(session))) continue;
            if (!seen.add(session.getUserId() + "|" + session.getExperimentCode())) continue;
            teachers.getSessionForTeacher(teacher, session.getId());
            var summary = process.summary(session);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("session", session); item.put("assessment", summary);
            item.put("username", students.stream().filter(s -> s.getId().equals(session.getUserId())).map(User::getUsername).findFirst().orElse(""));
            result.add(item);
        }
        return result;
    }
    @GetMapping("/api/teacher/sessions/{sessionId}/process")
    public Map<String, Object> detail(@PathVariable Long sessionId, Authentication auth) {
        var teacher = teachers.requireTeacher(AuthSupport.currentUserId(auth));
        var session = teachers.getSessionForTeacher(teacher, sessionId);
        return Map.of("session", session, "assessment", process.summary(session));
    }
    @PostMapping("/api/teacher/sessions/{sessionId}/steps/{stepId}/review")
    public Map<String, Object> review(@PathVariable Long sessionId, @PathVariable int stepId,
                                      @Valid @RequestBody StepReviewRequest request, Authentication auth) {
        User teacher = teachers.requireTeacher(AuthSupport.currentUserId(auth));
        return process.review(teachers.getSessionForTeacher(teacher, sessionId), stepId, teacher, request);
    }
}
