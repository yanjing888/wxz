package com.wuxiaozhi.controller;

import com.wuxiaozhi.dto.ExperimentProgressDto;
import com.wuxiaozhi.dto.StudentProfileDto;
import com.wuxiaozhi.security.AuthSupport;
import com.wuxiaozhi.service.StudentExperimentService;
import com.wuxiaozhi.service.StudentProfileService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/student/experiments")
public class StudentExperimentController {

    private final StudentExperimentService studentExperimentService;
    private final StudentProfileService studentProfileService;

    public StudentExperimentController(StudentExperimentService studentExperimentService,
                                       StudentProfileService studentProfileService) {
        this.studentExperimentService = studentExperimentService;
        this.studentProfileService = studentProfileService;
    }

    @GetMapping("/profile-summary")
    public StudentProfileDto profileSummary(Authentication authentication) {
        return studentProfileService.summary(AuthSupport.currentUserId(authentication));
    }

    @GetMapping("/progress")
    public List<ExperimentProgressDto> listProgress(Authentication authentication) {
        return studentExperimentService.listAssignedProgress(AuthSupport.currentUserId(authentication));
    }

    @GetMapping("/{experimentCode}/progress")
    public ExperimentProgressDto progress(@PathVariable String experimentCode, Authentication authentication) {
        return studentExperimentService.getProgress(AuthSupport.currentUserId(authentication), experimentCode);
    }

    @PostMapping("/{experimentCode}/progress/pre-lab")
    public ExperimentProgressDto completePreLab(@PathVariable String experimentCode, Authentication authentication) {
        return studentExperimentService.completePreLab(AuthSupport.currentUserId(authentication), experimentCode);
    }

    @PostMapping("/{experimentCode}/progress/recap")
    public ExperimentProgressDto completeRecap(@PathVariable String experimentCode, Authentication authentication) {
        return studentExperimentService.completeRecap(AuthSupport.currentUserId(authentication), experimentCode);
    }
}
