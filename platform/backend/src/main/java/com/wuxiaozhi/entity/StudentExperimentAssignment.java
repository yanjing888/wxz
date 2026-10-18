package com.wuxiaozhi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "student_experiment_assignments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "experimentCode"}))
@Getter
@Setter
public class StudentExperimentAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 64)
    private String experimentCode;

    @Column(nullable = false)
    private Long assignedByUserId;

    /** 分配时的教学班快照，避免学生加入其他班级后报告串班。 */
    @Column(length=64)
    private String className;

    @Column(length=4000)
    private String instructions;

    private LocalDateTime reportDueAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime assignedAt = LocalDateTime.now();
}
