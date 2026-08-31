package com.wuxiaozhi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "student_experiment_progress",
        uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "experimentCode"}))
@Getter
@Setter
public class StudentExperimentProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 64)
    private String experimentCode;

    @Column(nullable = false)
    private boolean preLabCompleted;

    @Column(nullable = false)
    private boolean reportCompleted;

    @Column(nullable = false)
    private boolean recapCompleted;

    /** 学生提交的实验报告正文（JSON：sectionKey -> html/text） */
    @Column(columnDefinition = "TEXT")
    private String reportSectionsJson;

    private Long reportSessionId;

    /** AI 预评建议分（10 分制） */
    private Double aiReviewScore;

    @Column(columnDefinition = "TEXT")
    private String aiReviewComment;

    @Column(columnDefinition = "TEXT")
    private String aiReviewJson;

    /** 教师确认分（10 分制） */
    private Double teacherScore;

    @Column(columnDefinition = "TEXT")
    private String teacherComment;

    private Boolean gradingCompleted = Boolean.FALSE;

    private LocalDateTime gradingAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
