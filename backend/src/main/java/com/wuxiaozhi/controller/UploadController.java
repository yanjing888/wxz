package com.wuxiaozhi.controller;

import com.wuxiaozhi.service.FileStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/upload")
public class UploadController {

    private final FileStorageService fileStorageService;

    public UploadController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @PostMapping
    public Map<String, String> upload(@RequestParam("file") MultipartFile file) throws Exception {
        String url = fileStorageService.store(file);
        return Map.of("url", url);
    }
}
