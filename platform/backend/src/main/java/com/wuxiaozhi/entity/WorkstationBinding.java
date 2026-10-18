package com.wuxiaozhi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** Current seating labels, independent of experiment sessions and camera configuration. */
@Entity
@Table(name="workstation_bindings", uniqueConstraints=@UniqueConstraint(columnNames={"room_id","user_id"}), indexes=@Index(columnList="station_id"))
@Getter @Setter
public class WorkstationBinding {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="room_id",nullable=false) private Long roomId;
    @Column(name="station_id",nullable=false) private Long stationId;
    @Column(name="user_id",nullable=false) private Long userId;
    @Column(nullable=false,length=64) private String className;
    @Column(nullable=false) private Long teacherId;
    @Column(nullable=false) private LocalDateTime updatedAt=LocalDateTime.now();
}
