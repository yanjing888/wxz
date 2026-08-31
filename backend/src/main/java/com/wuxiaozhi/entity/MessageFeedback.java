package com.wuxiaozhi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "message_feedbacks", indexes = {
        @Index(name = "idx_feedback_teacher_list", columnList = "studentClass,createdAt"),
        @Index(name = "idx_feedback_message", columnList = "messageId")
})
@Getter
@Setter
public class MessageFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long messageId;

    @Column(nullable = false)
    private Long sessionId;

    @Column(nullable = false)
    private Long userId;

    /** HELPFUL | NOT_HELPFUL */
    @Column(nullable = false, length = 16)
    private String rating;

    @Column(nullable = false, length = 64)
    private String experimentCode;

    @Column(nullable = false, length = 128)
    private String experimentName;

    @Column(nullable = false)
    private int stepId;

    @Column(nullable = false, length = 64)
    private String studentName;

    @Column(length = 64)
    private String studentClass;

    @Column(columnDefinition = "LONGTEXT")
    private String userQuestion;

    @Column(columnDefinition = "LONGTEXT")
    private String aiReply;

    /** 学生提交时的评价：HELPFUL | NOT_HELPFUL */
    @Column(length = 16)
    private String studentRating;

    @Column(nullable = false)
    private boolean processed = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
