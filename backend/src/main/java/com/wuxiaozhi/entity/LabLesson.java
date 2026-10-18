package com.wuxiaozhi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name="lab_lessons") @Getter @Setter
public class LabLesson {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long roomId;
    @Column(nullable=false) private Long teacherId;
    @Column(length=64) private String experimentCode;
    @Column(nullable=false,length=64) private String className;
    @Column(nullable=false,length=16) private String status="ACTIVE";
    private LocalDateTime startedAt=LocalDateTime.now();
    private LocalDateTime endedAt;
}
