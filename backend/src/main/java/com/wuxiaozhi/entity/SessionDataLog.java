package com.wuxiaozhi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "session_data_logs")
@Getter
@Setter
public class SessionDataLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sessionId;

    @Column(nullable = false)
    private int stepId;

    @Column(length = 128)
    private String stepTitle;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String valuesJson;

    @Column(columnDefinition = "TEXT")
    private String validationJson;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
