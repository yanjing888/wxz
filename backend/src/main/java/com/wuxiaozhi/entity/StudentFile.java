package com.wuxiaozhi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 学生在实验全过程中产生或上传的文件（原始数据表、装置照片、图表、报告等） */
@Entity
@Table(name = "student_file", indexes = {
        @Index(name = "idx_student_file_user", columnList = "userId"),
        @Index(name = "idx_student_file_exp", columnList = "userId,experimentCode")
})
@Getter
@Setter
public class StudentFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(length = 64)
    private String experimentCode = "";

    @Column(length = 128)
    private String experimentName = "";

    /** raw_data | photo | chart | prep | report | other */
    @Column(nullable = false, length = 32)
    private String category = "other";

    /** 阶段：prepare | lab | data | report */
    @Column(nullable = false, length = 16)
    private String stage = "lab";

    @Column(nullable = false, length = 255)
    private String fileName = "";

    @Column(nullable = false, length = 512)
    private String url = "";

    @Column(length = 128)
    private String contentType = "";

    @Column(nullable = false)
    private long sizeBytes;

    private Long sessionId;

    @Column(length = 500)
    private String note = "";

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
