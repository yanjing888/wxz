package com.wuxiaozhi.controller;

import com.wuxiaozhi.dto.experiment.ExperimentConfig;
import com.wuxiaozhi.entity.UserRole;
import com.wuxiaozhi.security.AuthSupport;
import com.wuxiaozhi.service.ExperimentConfigService;
import com.wuxiaozhi.service.StudentExperimentAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/experiments")
public class ExperimentController {

    private final ExperimentConfigService experimentConfigService;
    private final StudentExperimentAccessService accessService;

    public ExperimentController(ExperimentConfigService experimentConfigService,
                                StudentExperimentAccessService accessService) {
        this.experimentConfigService = experimentConfigService;
        this.accessService = accessService;
    }

    @GetMapping
    public List<ExperimentConfig> list(Authentication authentication) {
        List<ExperimentConfig> all = experimentConfigService.listAll();
        if (authentication == null || !AuthSupport.hasRole(authentication, UserRole.STUDENT)) {
            return all;
        }
        Long userId = AuthSupport.currentUserId(authentication);
        Set<String> codes = accessService.assignedCodes(userId);
        return all.stream().filter(e -> codes.contains(e.getCode())).toList();
    }

    @GetMapping("/{code}")
    public ExperimentConfig get(@PathVariable String code, Authentication authentication) {
        ExperimentConfig config = experimentConfigService.getByCode(code);
        if (authentication != null && AuthSupport.hasRole(authentication, UserRole.STUDENT)) {
            Long userId = AuthSupport.currentUserId(authentication);
            if (!accessService.isAssigned(userId, code)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "该实验未分配给您，请联系教师");
            }
        }
        return config;
    }
}
