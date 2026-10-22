package com.wuxiaozhi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** Progress belongs to an experiment, so opening another conversation cannot reset it. */
@Entity
@Table(name = "step_assessments", uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "experimentCode", "stepId"}))
@Getter @Setter
public class StepAssessment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version private Long version;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false, length = 64) private String experimentCode;
    @Column(nullable = false) private int stepId;
    @Column(nullable = false) private Long sessionId;
    @Column(nullable = false, length = 24) private String status = "PENDING";
    private Double score;
    @Column(length = 2000) private String comment;
    @Column(length = 2000) private String studentNote;
    private Long teacherId;
    private String teacherName;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    @Column(columnDefinition = "LONGTEXT") private String evidenceJson;
    @Column(columnDefinition = "LONGTEXT") private String historyJson = "[]";
    @Column(columnDefinition = "TEXT") private String checkedCriteriaJson;
}
