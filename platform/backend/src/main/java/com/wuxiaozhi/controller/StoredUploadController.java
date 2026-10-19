package com.wuxiaozhi.controller;

import com.wuxiaozhi.service.FileStorageService;
import java.io.FileNotFoundException;
import java.io.IOException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class StoredUploadController {
    private final FileStorageService files;

    public StoredUploadController(FileStorageService files) { this.files = files; }

    @GetMapping("/uploads/{filename:.+}")
    public ResponseEntity<byte[]> read(@PathVariable String filename) throws IOException {
        try {
            var file = files.read("/uploads/" + filename);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(file.getContentType()))
                    .contentLength(file.getSizeBytes())
                    .header("X-Content-Type-Options", "nosniff")
                    .body(file.getData());
        } catch (FileNotFoundException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在");
        }
    }
}
