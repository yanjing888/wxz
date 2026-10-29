package com.wuxiaozhi.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.wuxiaozhi.security.AuthSupport;
import com.wuxiaozhi.service.SimulationResultService;
import com.wuxiaozhi.service.TeacherService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class SimulationResultController {
    private final SimulationResultService results;
    private final TeacherService teachers;

    @PutMapping("/api/student/experiments/{code}/simulation-result")
    public JsonNode save(@PathVariable String code, @RequestBody JsonNode body, Authentication auth) {
        return results.save(AuthSupport.currentUserId(auth), code, body);
    }
    @GetMapping("/api/teacher/simulation-results")
    public Object list(@RequestParam String experimentCode, @RequestParam(required=false) String className, Authentication auth) {
        return results.list(teachers.requireTeacher(AuthSupport.currentUserId(auth)), experimentCode, className);
    }
    @GetMapping("/api/teacher/simulation-results/{userId}")
    public JsonNode detail(@PathVariable Long userId, @RequestParam String experimentCode, Authentication auth) {
        return results.detail(teachers.requireTeacher(AuthSupport.currentUserId(auth)), experimentCode, userId);
    }
}
