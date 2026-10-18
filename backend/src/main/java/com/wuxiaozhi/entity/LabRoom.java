package com.wuxiaozhi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 实际实验室。experimentCode 仅保留为旧版本的默认实验映射。 */
@Entity @Table(name="lab_rooms") @Getter @Setter
public class LabRoom {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(unique=true,length=64) private String experimentCode;
    @Column(nullable=false,length=100) private String name;
}
