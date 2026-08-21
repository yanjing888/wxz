package com.wuxiaozhi.controller;

import com.wuxiaozhi.service.DifyService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/voice")
public class VoiceController {

    private final DifyService difyService;

    public VoiceController(DifyService difyService) {
        this.difyService = difyService;
    }

    @PostMapping("/transcribe")
    public Map<String, String> transcribe(@RequestParam("file") MultipartFile file,
                                          Authentication authentication) {
        String text = difyService.transcribeAudio(file, String.valueOf(authentication.getPrincipal()));
        return Map.of("text", text);
    }
}
