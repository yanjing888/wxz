package com.wuxiaozhi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** 入座时保存身份和设备快照，历史记录不借用当前摄像头/当前学生。 */
@Entity @Table(name="workstation_uses",indexes={@Index(columnList="stationId,endedAt"),@Index(columnList="userId,endedAt")})
@Getter @Setter
public class WorkstationUse {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long stationId;
    @Column(nullable=false) private Long lessonId;
    @Column(nullable=false) private Long userId;
    @Column(nullable=false) private Long sessionId;
    @Column(length=64) private String studentName;
    @Column(length=64) private String className;
    @Column(length=64) private String cameraCode;
    private LocalDateTime joinedAt=LocalDateTime.now();
    private LocalDateTime endedAt;
    @Column(length=32) private String endReason;
}
