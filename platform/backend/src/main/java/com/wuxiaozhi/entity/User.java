package com.wuxiaozhi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 64)
    private String displayName;

    @Column(length = 64)
    private String studentClass;

    /** STUDENT | TEACHER | ADMIN */
    @Column(nullable = false, length = 16)
    private String role = UserRole.STUDENT;

    @Column(nullable=false,columnDefinition="boolean default true")
    private boolean enabled=true;

    @Column(nullable=false,columnDefinition="integer default 0")
    private int authVersion=0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
