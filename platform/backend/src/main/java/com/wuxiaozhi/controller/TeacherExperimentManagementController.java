package com.wuxiaozhi.controller;

import com.wuxiaozhi.dto.experiment.ExperimentConfig;
import com.wuxiaozhi.security.AuthSupport;
import com.wuxiaozhi.service.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController @RequestMapping("/api/teacher/experiment-management")
public class TeacherExperimentManagementController {
    private final ExperimentConfigService configs;
    private final FileStorageService files;
    public TeacherExperimentManagementController(ExperimentConfigService configs, FileStorageService files) { this.configs = configs; this.files = files; }
    public record SaveRequest(ExperimentConfig config, Long version) {}
    @GetMapping public Object list(Authentication auth) { AuthSupport.requireTeacher(auth); return configs.listAll(); }
    @PostMapping public Object create(@RequestBody SaveRequest request, Authentication auth) {
        AuthSupport.requireTeacher(auth); return configs.createTeachingConfig(request.config(), AuthSupport.currentUserId(auth));
    }
    @GetMapping("/{code}") public Object detail(@PathVariable String code, Authentication auth) { AuthSupport.requireTeacher(auth); return configs.managementDetail(code); }
    @PutMapping("/{code}") public Object save(@PathVariable String code, @RequestBody SaveRequest request, Authentication auth) {
        AuthSupport.requireTeacher(auth); return configs.saveTeachingConfig(code, request.config(), request.version(), AuthSupport.currentUserId(auth));
    }
    @PostMapping("/media") public Object upload(@RequestParam MultipartFile file, Authentication auth) throws Exception {
        AuthSupport.requireTeacher(auth); return Map.of("url", files.storeTeachingMedia(file));
    }
}
