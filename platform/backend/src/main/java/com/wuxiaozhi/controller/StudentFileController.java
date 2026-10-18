package com.wuxiaozhi.controller;

import com.wuxiaozhi.dto.SaveStudentFileRequest;
import com.wuxiaozhi.dto.StudentFileDto;
import com.wuxiaozhi.security.AuthSupport;
import com.wuxiaozhi.service.FileStorageService;
import com.wuxiaozhi.service.StudentFileService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student/files")
public class StudentFileController {

    private final StudentFileService studentFileService;
    private final FileStorageService fileStorageService;

    public StudentFileController(StudentFileService studentFileService,
                                 FileStorageService fileStorageService) {
        this.studentFileService = studentFileService;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping
    public List<StudentFileDto> list(@RequestParam(required = false) String experimentCode,
                                     Authentication authentication) {
        return studentFileService.list(AuthSupport.currentUserId(authentication), experimentCode);
    }

    @PostMapping("/upload")
    public StudentFileDto upload(@RequestParam("file") MultipartFile file,
                                 @RequestParam(required = false) String experimentCode,
                                 @RequestParam(required = false) String category,
                                 @RequestParam(required = false) String stage,
                                 @RequestParam(required = false) String note,
                                 @RequestParam(required = false) Long sessionId,
                                 Authentication authentication) throws Exception {
        String url = fileStorageService.storeAttachment(file);
        SaveStudentFileRequest request = new SaveStudentFileRequest();
        request.setUrl(url);
        request.setFileName(file.getOriginalFilename());
        request.setExperimentCode(experimentCode);
        request.setCategory(category);
        request.setStage(stage);
        request.setNote(note);
        request.setSessionId(sessionId);
        return studentFileService.save(AuthSupport.currentUserId(authentication), request);
    }

    @PostMapping
    public StudentFileDto save(@RequestBody SaveStudentFileRequest request, Authentication authentication) {
        return studentFileService.save(AuthSupport.currentUserId(authentication), request);
    }

    @PatchMapping("/{fileId}")
    public StudentFileDto rename(@PathVariable Long fileId,
                                 @RequestBody Map<String, String> body,
                                 Authentication authentication) {
        return studentFileService.rename(AuthSupport.currentUserId(authentication), fileId,
                body.get("fileName"), body.get("note"));
    }

    @DeleteMapping("/{fileId}")
    public Map<String, Object> delete(@PathVariable Long fileId, Authentication authentication) {
        studentFileService.delete(AuthSupport.currentUserId(authentication), fileId);
        return Map.of("success", true);
    }
}
