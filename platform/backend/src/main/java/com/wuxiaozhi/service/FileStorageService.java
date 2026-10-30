package com.wuxiaozhi.service;

import com.wuxiaozhi.config.AppProperties;
import com.wuxiaozhi.entity.StoredUpload;
import com.wuxiaozhi.repository.StoredUploadRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.LinkOption;
import java.util.List;
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

    private static final Set<String> ALLOWED_DOC_EXT = Set.of(
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".csv", ".txt", ".md", ".json");

    private static final long MAX_ATTACHMENT_BYTES = 20L * 1024 * 1024;

    private final Path uploadRoot;
    private final StoredUploadRepository uploads;

    public FileStorageService(AppProperties appProperties, StoredUploadRepository uploads) {
        this.uploads = uploads;
        // Only used to import files written by older versions. New uploads never
        // create this directory or write image bytes to the application disk.
        this.uploadRoot = Paths.get(appProperties.getUpload().getDir()).toAbsolutePath().normalize();
    }

    /** 资料库附件：在图片之外额外放行常见的数据/文档格式 */
    public String storeAttachment(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的文件");
        }
        byte[] bytes = file.getBytes();
        if (bytes.length == 0) {
            throw new IllegalArgumentException("文件内容为空");
        }
        if (bytes.length > MAX_ATTACHMENT_BYTES) {
            throw new IllegalArgumentException("文件超过 20MB 限制");
        }

        String ext = extensionFromMagic(bytes);
        if (ext.isEmpty()) {
            ext = attachmentExtensionFromFilename(file.getOriginalFilename());
        }
        if (ext.isEmpty()) {
            throw new IllegalArgumentException("暂不支持该文件格式，请上传图片、PDF、Word、Excel、CSV 或文本文件");
        }

        String filename = UUID.randomUUID() + ext;
        return persist(filename, bytes);
    }

    private static String attachmentExtensionFromFilename(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "";
        }
        String ext = originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        if (".jpeg".equals(ext)) {
            return ".jpg";
        }
        if (ALLOWED_EXT.contains(ext) || ALLOWED_DOC_EXT.contains(ext)) {
            return ext;
        }
        return "";
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
        return persist(filename, bytes);
    }

    public String storeTeachingMedia(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty() || file.getSize() > MAX_ATTACHMENT_BYTES)
            throw new IllegalArgumentException("请选择不超过20MB的图片或视频");
        byte[] bytes = file.getBytes();
        String ext = extensionFromMagic(bytes);
        if (!ext.isEmpty() && ALLOWED_EXT.contains(ext)) return persist(UUID.randomUUID() + ext, bytes);
        boolean mp4 = bytes.length > 12 && new String(bytes, 4, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("ftyp");
        boolean webm = bytes.length > 4 && bytes[0] == 0x1a && bytes[1] == 0x45 && (bytes[2] & 255) == 0xdf && (bytes[3] & 255) == 0xa3;
        if (!mp4 && !webm) throw new IllegalArgumentException("仅支持图片、MP4 或 WebM 视频");
        return persist(UUID.randomUUID() + (mp4 ? ".mp4" : ".webm"), bytes);
    }

    public String storeImageBytes(byte[] bytes, String preferredExt) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("图片文件为空");
        }
        String ext = extensionFromMagic(bytes);
        if (ext.isEmpty()) {
            String normalized = preferredExt == null ? "" : preferredExt.toLowerCase(Locale.ROOT).trim();
            if (!normalized.startsWith(".")) {
                normalized = "." + normalized;
            }
            ext = ALLOWED_EXT.contains(normalized) ? normalized : ".jpg";
        }
        String filename = UUID.randomUUID() + ext;
        return persist(filename, bytes);
    }

    private String persist(String filename, byte[] bytes) {
        if (bytes.length > MAX_ATTACHMENT_BYTES) {
            throw new IllegalArgumentException("文件超过 20MB 限制");
        }
        StoredUpload upload = new StoredUpload();
        upload.setFilename(filename);
        upload.setData(bytes);
        upload.setSizeBytes(bytes.length);
        upload.setContentType(contentType(filename, bytes));
        uploads.saveAndFlush(upload);
        return "/uploads/" + filename;
    }

    public StoredUpload read(String urlPath) throws IOException {
        String filename = filename(urlPath);
        return uploads.findById(filename)
                .orElseThrow(() -> new FileNotFoundException("上传文件不存在"));
    }

    public long size(String urlPath) throws IOException {
        return uploads.findSizeByFilename(filename(urlPath))
                .orElseThrow(() -> new FileNotFoundException("上传文件不存在"));
    }

    private String filename(String urlPath) {
        if (urlPath == null || !urlPath.startsWith("/uploads/")) {
            throw new IllegalArgumentException("Invalid upload path");
        }
        String cleanPath = urlPath;
        int queryIdx = cleanPath.indexOf('?');
        if (queryIdx >= 0) {
            cleanPath = cleanPath.substring(0, queryIdx);
        }
        String filename = cleanPath.substring("/uploads/".length());
        if (!filename.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,254}")) {
            throw new IllegalArgumentException("Invalid upload path");
        }
        return filename;
    }

    /** Idempotent import, preserving names and every historical URL. Originals
     * are retained so an operator can verify the database backup before cleanup. */
    public int importLegacyUploads() throws IOException {
        if (!Files.isDirectory(uploadRoot, LinkOption.NOFOLLOW_LINKS)) return 0;
        List<Path> paths;
        try (var entries = Files.list(uploadRoot)) {
            paths = entries.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).toList();
        }
        int imported = 0;
        for (Path path : paths) {
            String filename = path.getFileName().toString();
            if (!filename.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,254}") ||
                    attachmentExtensionFromFilename(filename).isEmpty()) continue;
            if (uploads.existsById(filename)) continue;
            if (Files.size(path) > MAX_ATTACHMENT_BYTES)
                throw new IOException("旧上传文件超过 20MB，未完成数据库导入：" + filename);
            persist(filename, Files.readAllBytes(path));
            imported++;
        }
        return imported;
    }

    private static String contentType(String filename, byte[] bytes) {
        if (filename.endsWith(".mp4")) return "video/mp4";
        if (filename.endsWith(".webm")) return "video/webm";
        String ext = extensionFromMagic(bytes);
        if (ext.isEmpty()) ext = attachmentExtensionFromFilename(filename);
        return switch (ext) {
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".png" -> "image/png";
            case ".gif" -> "image/gif";
            case ".webp" -> "image/webp";
            case ".bmp" -> "image/bmp";
            case ".pdf" -> "application/pdf";
            case ".doc" -> "application/msword";
            case ".docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case ".xls" -> "application/vnd.ms-excel";
            case ".xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case ".csv" -> "text/csv";
            case ".txt", ".md" -> "text/plain";
            case ".json" -> "application/json";
            default -> "application/octet-stream";
        };
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
