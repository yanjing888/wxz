package com.wuxiaozhi.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
@Entity @Table(name="admin_audit_logs") @Getter @Setter
public class AdminAuditLog {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private Long actorId;
    @Column(length=32) private String action;
    private Long targetUserId;
    @Column(length=200) private String summary;
    private LocalDateTime createdAt=LocalDateTime.now();
}
