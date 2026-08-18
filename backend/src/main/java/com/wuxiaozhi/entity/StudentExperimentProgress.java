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

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
