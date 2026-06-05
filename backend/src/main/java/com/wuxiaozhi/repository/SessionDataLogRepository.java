package com.wuxiaozhi.repository;

import com.wuxiaozhi.entity.SessionDataLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SessionDataLogRepository extends JpaRepository<SessionDataLog, Long> {
    List<SessionDataLog> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    Optional<SessionDataLog> findFirstBySessionIdAndStepIdOrderByCreatedAtDesc(Long sessionId, int stepId);
}
