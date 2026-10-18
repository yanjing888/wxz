package com.wuxiaozhi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name="lab_workstations",uniqueConstraints=@UniqueConstraint(columnNames={"roomId","code"}))
@Getter @Setter
public class LabWorkstation {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long roomId;
    @Column(nullable=false,length=32) private String code;
    @Column(nullable=false,length=80) private String name;
    private int capacity=1;
    private boolean enabled=true;
    @Column(length=64,unique=true) private String cameraCode;
    @Column(length=64,unique=true) private String streamHash;
    @Column(length=16) private String cameraType="NETWORK";
    @Column(length=1024) private String streamUrl;
    @Column(length=1024) private String snapshotUrl;
    @Column(length=128) private String cameraUsername;
    @Column(length=256) private String cameraPassword;
    @Column(length=64) private String terminalHash;
    @Column(length=64) private String pairHash;
    private LocalDateTime pairExpiresAt;
    private LocalDateTime pairedAt;
    private LocalDateTime terminalSeenAt;
    @Column(length=64) private String groupCodeHash;
    private LocalDateTime groupCodeExpiresAt;
    private Long groupLessonId;
    @Column(length=64) private String publisherHash;
    private boolean inspectionEnabled=false;
    private LocalDateTime lastInspectionAt;
    @Column(length=200) private String inspectionError;
}
