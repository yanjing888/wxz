package com.wuxiaozhi.controller;

import com.wuxiaozhi.security.AuthSupport;
import com.wuxiaozhi.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/teacher/teaching-context")
public class TeacherTeachingContextController {
    private final TeacherTeachingContextService contexts;
    private final TeacherService teachers;
    public TeacherTeachingContextController(TeacherTeachingContextService contexts, TeacherService teachers) {
        this.contexts = contexts; this.teachers = teachers;
    }
    public record Selection(@Size(max = 64) String className) {}
    @GetMapping
    public Map<String, Object> get(@RequestParam String experimentCode, Authentication auth) {
        return contexts.get(teachers.requireTeacher(AuthSupport.currentUserId(auth)), experimentCode);
    }
    @PutMapping
    public Map<String, Object> select(@RequestParam String experimentCode, @Valid @RequestBody Selection request, Authentication auth) {
        return contexts.select(teachers.requireTeacher(AuthSupport.currentUserId(auth)), experimentCode, request.className());
    }
}
