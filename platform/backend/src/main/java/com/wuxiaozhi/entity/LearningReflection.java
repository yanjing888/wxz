package com.wuxiaozhi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name="learning_reflections",uniqueConstraints=@UniqueConstraint(name="uk_learning_reflection_user_experiment",columnNames={"user_id","experiment_code"}))
@Getter @Setter
public class LearningReflection {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="user_id",nullable=false) private Long userId;
    @Column(name="experiment_code",nullable=false,length=64) private String experimentCode;
    @Column(nullable=false,length=2000) private String content;
    @Column(nullable=false,length=64) private String feedbackRevision;
    @Column(nullable=false) private LocalDateTime submittedAt=LocalDateTime.now();
}
