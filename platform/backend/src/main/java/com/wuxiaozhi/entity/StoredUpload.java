package com.wuxiaozhi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** Binary uploads travel with the MySQL backup; URLs remain /uploads/{filename}. */
@Entity
@Table(name = "stored_uploads")
@Getter
@Setter
public class StoredUpload {
    @Id
    @Column(length = 255)
    private String filename;

    @Column(nullable = false, length = 128)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGBLOB")
    private byte[] data;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
