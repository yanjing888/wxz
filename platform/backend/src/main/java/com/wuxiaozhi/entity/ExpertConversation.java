package com.wuxiaozhi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "expert_conversation_links")
@Getter
@Setter
public class ExpertConversation {
    @Id
    @Column(length = 36)
    private String id;
    @Column(nullable = false)
    private Long userId;
    @Column(nullable = false, length = 128)
    private String expertId;
    private String title;
    private String mode;
    private String threadId;
    private LocalDateTime updatedAt = LocalDateTime.now();
}
