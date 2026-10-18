package com.wuxiaozhi.service;

import com.wuxiaozhi.dto.SaveStudentFileRequest;
import com.wuxiaozhi.dto.StudentFileDto;
import com.wuxiaozhi.entity.StudentFile;
import com.wuxiaozhi.repository.StudentFileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 实验资料库：管理学生在实验全过程中沉淀下来的文件 */
@Service
public class StudentFileService {

    private static final Map<String, String> CATEGORY_LABELS = Map.of(
            "raw_data", "原始数据",
            "photo", "实验照片",
            "chart", "数据图表",
            "prep", "预习材料",
            "report", "实验报告",
            "other", "其他资料"
    );

    private static final Set<String> STAGES = Set.of("prepare", "lab", "data", "report");
    private static final Set<String> PREVIEWABLE_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");

    private final StudentFileRepository fileRepository;
    private final FileStorageService fileStorageService;
    private final ExperimentConfigService experimentConfigService;

    public StudentFileService(StudentFileRepository fileRepository,
                              FileStorageService fileStorageService,
                              ExperimentConfigService experimentConfigService) {
        this.fileRepository = fileRepository;
        this.fileStorageService = fileStorageService;
        this.experimentConfigService = experimentConfigService;
    }

    public List<StudentFileDto> list(Long userId, String experimentCode) {
        List<StudentFile> files = (experimentCode == null || experimentCode.isBlank())
                ? fileRepository.findByUserIdOrderByCreatedAtDesc(userId)
                : fileRepository.findByUserIdAndExperimentCodeOrderByCreatedAtDesc(userId, experimentCode.trim());
        return files.stream().map(this::toDto).toList();
    }

    @Transactional
    public StudentFileDto save(Long userId, SaveStudentFileRequest request) {
        String url = trim(request.getUrl());
        if (!url.startsWith("/uploads/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件地址不合法");
        }
        StudentFile file = new StudentFile();
        file.setUserId(userId);
        file.setUrl(url);
        file.setCategory(normalizeCategory(request.getCategory()));
        file.setStage(normalizeStage(request.getStage()));
        file.setFileName(defaultIfBlank(trim(request.getFileName()), url.substring(url.lastIndexOf('/') + 1)));
        file.setNote(trim(request.getNote()));
        file.setSessionId(request.getSessionId());
        file.setCreatedAt(LocalDateTime.now());
        applyExperiment(file, request.getExperimentCode());
        file.setSizeBytes(readSize(url));
        file.setContentType(extensionOf(url));
        return toDto(fileRepository.save(file));
    }

    @Transactional
    public void delete(Long userId, Long fileId) {
        StudentFile file = fileRepository.findByIdAndUserId(fileId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "资料不存在"));
        fileRepository.delete(file);
        try {
            Files.deleteIfExists(fileStorageService.resolve(file.getUrl()));
        } catch (IOException | IllegalArgumentException ignored) {
            // 磁盘文件缺失不影响记录删除
        }
    }

    @Transactional
    public StudentFileDto rename(Long userId, Long fileId, String fileName, String note) {
        StudentFile file = fileRepository.findByIdAndUserId(fileId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "资料不存在"));
        String name = trim(fileName);
        if (!name.isBlank()) {
            file.setFileName(name);
        }
        if (note != null) {
            file.setNote(trim(note));
        }
        return toDto(fileRepository.save(file));
    }

    private void applyExperiment(StudentFile file, String experimentCode) {
        String code = trim(experimentCode);
        file.setExperimentCode(code);
        if (code.isBlank()) {
            file.setExperimentName("未归类");
            return;
        }
        try {
            file.setExperimentName(experimentConfigService.getByCode(code).getName());
        } catch (RuntimeException e) {
            file.setExperimentName(code);
        }
    }

    private long readSize(String url) {
        try {
            return Files.size(fileStorageService.resolve(url));
        } catch (IOException | IllegalArgumentException e) {
            return 0L;
        }
    }

    private StudentFileDto toDto(StudentFile file) {
        StudentFileDto dto = new StudentFileDto();
        dto.setId(file.getId());
        dto.setExperimentCode(file.getExperimentCode());
        dto.setExperimentName(file.getExperimentName());
        dto.setCategory(file.getCategory());
        dto.setCategoryLabel(CATEGORY_LABELS.getOrDefault(file.getCategory(), "其他资料"));
        dto.setStage(file.getStage());
        dto.setFileName(file.getFileName());
        dto.setUrl(file.getUrl());
        dto.setContentType(file.getContentType());
        dto.setSizeBytes(file.getSizeBytes());
        dto.setSessionId(file.getSessionId());
        dto.setNote(file.getNote());
        dto.setCreatedAt(file.getCreatedAt());
        dto.setPreviewable(PREVIEWABLE_EXT.contains(file.getContentType()));
        return dto;
    }

    private String normalizeCategory(String category) {
        String value = trim(category).toLowerCase(Locale.ROOT);
        return CATEGORY_LABELS.containsKey(value) ? value : "other";
    }

    private String normalizeStage(String stage) {
        String value = trim(stage).toLowerCase(Locale.ROOT);
        return STAGES.contains(value) ? value : "lab";
    }

    private String extensionOf(String url) {
        int idx = url.lastIndexOf('.');
        return idx < 0 ? "" : url.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    private String defaultIfBlank(String value, String fallback) {
        return value.isBlank() ? fallback : value;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
