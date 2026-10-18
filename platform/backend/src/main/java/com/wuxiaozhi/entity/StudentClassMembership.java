package com.wuxiaozhi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** 教学班成员关系；不通过修改账号的行政班来移动历史记录。 */
@Entity
@Table(name="student_class_memberships", uniqueConstraints=@UniqueConstraint(columnNames={"userId","className"}))
@Getter @Setter
public class StudentClassMembership {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long userId;
    @Column(nullable=false,length=64) private String className;
    private Long addedByUserId;
    @Column(nullable=false) private LocalDateTime addedAt=LocalDateTime.now();
}
