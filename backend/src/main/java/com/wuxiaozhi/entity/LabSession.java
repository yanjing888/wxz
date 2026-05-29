package com.wuxiaozhi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "lab_sessions")
@Getter
@Setter
public class LabSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 64)
    private String experimentCode;

    @Column(nullable = false, length = 128)
    private String experimentName;

    @Column(length = 64)
    private String studentClass;

    @Column(nullable = false, length = 64)
    private String studentName;

    @Column(nullable = false)
    private int activeStep = 1;

    private int helpCount = 0;
    private int errorPointCount = 0;
    private int tutViewCount = 0;
    private int labL3Count = 0;

    @Column(nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(nullable = false, updatable = false)
    private LocalDateTime startTime = LocalDateTime.now();

    private LocalDateTime endTime;
}
