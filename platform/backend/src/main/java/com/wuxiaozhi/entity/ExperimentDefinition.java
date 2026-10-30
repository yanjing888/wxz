package com.wuxiaozhi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name = "experiment_definitions") @Getter @Setter
public class ExperimentDefinition {
    @Id @Column(length = 64) private String code;
    @Version private Long version;
    @Column(nullable = false, columnDefinition = "LONGTEXT") private String contentJson;
    private LocalDateTime updatedAt;
    private Long updatedBy;
}
