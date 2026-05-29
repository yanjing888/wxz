package com.wuxiaozhi.repository;

import com.wuxiaozhi.entity.CorrectionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CorrectionLogRepository extends JpaRepository<CorrectionLog, Long> {
    List<CorrectionLog> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
}
