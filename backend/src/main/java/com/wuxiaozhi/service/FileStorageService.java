package com.wuxiaozhi.service;

import com.wuxiaozhi.config.AppProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_EXT = Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp");
    private static final Map<String, String> CONTENT_TYPE_EXT = Map.of(
            "image/jpeg", ".jpg",
            "image/jpg", ".jpg",
            "image/png", ".png",
            "image/gif", ".gif",
            "image/webp", ".webp",
            "image/bmp", ".bmp"
    );

    private final Path uploadRoot;

    public FileStorageService(AppProperties appProperties) throws IOException {
        this.uploadRoot = Paths.get(appProperties.getUpload().getDir()).toAbsolutePath().normalize();
        Files.createDirectories(uploadRoot);
    }

    public String store(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择图片文件");
        }
        byte[] bytes = file.getBytes();
        if (bytes.length == 0) {
            throw new IllegalArgumentException("图片文件为空");
        }

        String ext = resolveExtension(file.getOriginalFilename(), file.getContentType(), bytes);
        String filename = UUID.randomUUID() + ext;
        Path target = uploadRoot.resolve(filename);
        Files.write(target, bytes);
        return "/uploads/" + filename;
    }

    public Path resolve(String urlPath) {
        if (urlPath == null || !urlPath.startsWith("/uploads/")) {
            throw new IllegalArgumentException("Invalid upload path");
        }
        String cleanPath = urlPath;
        int queryIdx = cleanPath.indexOf('?');
        if (queryIdx >= 0) {
            cleanPath = cleanPath.substring(0, queryIdx);
        }
        Path resolved = uploadRoot.resolve(cleanPath.substring("/uploads/".length())).normalize();
        if (!resolved.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("Invalid upload path");
        }
        return resolved;
    }

    static String resolveExtension(String originalFilename, String contentType, byte[] bytes) {
        String fromMagic = extensionFromMagic(bytes);
        if (!fromMagic.isEmpty()) {
            return fromMagic;
        }

        String fromType = extensionFromContentType(contentType);
        if (!fromType.isEmpty()) {
            return fromType;
        }

        String fromName = extensionFromFilename(originalFilename);
        if (!fromName.isEmpty()) {
            return fromName;
        }

        return ".jpg";
    }

    private static String extensionFromContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "";
        }
        String normalized = contentType.toLowerCase(Locale.ROOT).split(";")[0].trim();
        String ext = CONTENT_TYPE_EXT.get(normalized);
        return ext != null ? ext : "";
    }

    private static String extensionFromFilename(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "";
        }
        String ext = originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        if (".jpeg".equals(ext)) {
            return ".jpg";
        }
        return ALLOWED_EXT.contains(ext) ? ext : "";
    }

    private static String extensionFromMagic(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return ".jpg";
        }
        if (bytes.length >= 8
                && bytes[0] == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47) {
            return ".png";
        }
        if (bytes.length >= 6
                && bytes[0] == 0x47
                && bytes[1] == 0x49
                && bytes[2] == 0x46) {
            return ".gif";
        }
        if (bytes.length >= 12
                && bytes[0] == 0x52
                && bytes[1] == 0x49
                && bytes[2] == 0x46
                && bytes[3] == 0x46
                && bytes[8] == 0x57
                && bytes[9] == 0x45
                && bytes[10] == 0x42
                && bytes[11] == 0x50) {
            return ".webp";
        }
        if (bytes.length >= 2 && bytes[0] == 0x42 && bytes[1] == 0x4D) {
            return ".bmp";
        }
        return "";
    }
}
