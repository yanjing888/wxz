package com.wuxiaozhi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "teacher_experiment_contexts", uniqueConstraints = @UniqueConstraint(columnNames = {"teacherId", "experimentCode"}))
@Getter @Setter
public class TeacherExperimentContext {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long teacherId;
    @Column(nullable = false, length = 64) private String experimentCode;
    @Column(length = 64) private String className;
}
