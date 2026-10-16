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

    /** 虚拟仿真预习已完成（部分实验进实验台前必做） */
    @Column(nullable = false)
    private boolean simulationCompleted;

    @Column(nullable = false)
    private boolean reportCompleted;

    @Column(nullable = false)
    private boolean recapCompleted;

    /** 学生提交的实验报告正文（JSON：sectionKey -> html/text） */
    @Column(columnDefinition = "TEXT")
    private String reportSectionsJson;

    private Long reportSessionId;

    /** AI 预评原始分；对应分值上限记录于 aiReviewScoreMax。 */
    private Double aiReviewScore;

    /** 旧记录默认10分，新Workflow预评使用100分。 */
    @Column(nullable = false, columnDefinition = "double default 10")
    private Double aiReviewScoreMax = 10.0;

    @Column(columnDefinition = "TEXT")
    private String aiReviewComment;

    @Column(columnDefinition = "TEXT")
    private String aiReviewJson;

    /** 教师确认原始分；对应分值上限记录于 teacherScoreMax。 */
    private Double teacherScore;

    @Column(nullable = false, columnDefinition = "double default 10")
    private Double teacherScoreMax = 10.0;

    @Column(columnDefinition = "TEXT")
    private String teacherComment;

    private Boolean gradingCompleted = Boolean.FALSE;

    private LocalDateTime gradingAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Double aiReviewScoreAsPercent() { return percent(aiReviewScore, aiReviewScoreMax); }
    public Double teacherScoreAsPercent() { return percent(teacherScore, teacherScoreMax); }
    private static Double percent(Double score, Double maximum) {
        if (score == null) return null;
        double scale = maximum == null ? 10.0 : maximum;
        if (!Double.isFinite(score) || !Double.isFinite(scale) || scale <= 0) return null;
        return Math.round(score / scale * 1000.0) / 10.0;
    }
}
