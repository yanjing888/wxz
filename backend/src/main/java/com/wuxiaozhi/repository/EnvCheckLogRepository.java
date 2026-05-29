package com.wuxiaozhi.repository;

import com.wuxiaozhi.entity.EnvCheckLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EnvCheckLogRepository extends JpaRepository<EnvCheckLog, Long> {
    List<EnvCheckLog> findBySessionIdOrderByCreatedAtDesc(Long sessionId);
}
