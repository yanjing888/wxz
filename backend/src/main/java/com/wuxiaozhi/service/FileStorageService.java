package com.wuxiaozhi.service;

import com.wuxiaozhi.config.AppProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path uploadRoot;

    public FileStorageService(AppProperties appProperties) throws IOException {
        this.uploadRoot = Paths.get(appProperties.getUpload().getDir()).toAbsolutePath().normalize();
        Files.createDirectories(uploadRoot);
    }

    public String store(MultipartFile file) throws IOException {
        String ext = "";
        String original = file.getOriginalFilename();
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.'));
        }
        String filename = UUID.randomUUID() + ext;
        Path target = uploadRoot.resolve(filename);
        Files.copy(file.getInputStream(), target);
        return "/uploads/" + filename;
    }

    public Path resolve(String urlPath) {
        if (urlPath == null || !urlPath.startsWith("/uploads/")) {
            throw new IllegalArgumentException("Invalid upload path");
        }
        return uploadRoot.resolve(urlPath.substring("/uploads/".length())).normalize();
    }
}
