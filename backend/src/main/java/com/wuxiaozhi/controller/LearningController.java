package com.wuxiaozhi.controller;

import com.wuxiaozhi.security.AuthSupport;
import com.wuxiaozhi.service.LearningService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
public class LearningController {
    private final LearningService service;
    public LearningController(LearningService service){this.service=service;}
    @GetMapping("/api/student/learning")
    public Map<String,Object> student(@RequestParam(required=false) String className,Authentication auth){
        return service.student(AuthSupport.currentUserId(auth),className);
    }
    @GetMapping("/api/teacher/learning")
    public Map<String,Object> teacher(@RequestParam(required=false) String className,@RequestParam(required=false) String experimentCode,Authentication auth){return service.teacher(AuthSupport.currentUserId(auth),className,experimentCode);}
    @GetMapping("/api/teacher/learning/students/{id}")
    public LearningService.Summary detail(@PathVariable Long id,@RequestParam String className,@RequestParam(required=false) String experimentCode,Authentication auth){return service.teacherStudent(AuthSupport.currentUserId(auth),id,className,experimentCode);}
    public record ReflectionRequest(@NotBlank @Size(max=2000) String content,@NotBlank @Size(max=64) String feedbackRevision){}
    @PutMapping("/api/student/learning/{code}/reflection")
    public void reflect(@PathVariable String code,@Valid @RequestBody ReflectionRequest request,Authentication auth){service.reflect(AuthSupport.currentUserId(auth),code,request.content(),request.feedbackRevision());}
}
