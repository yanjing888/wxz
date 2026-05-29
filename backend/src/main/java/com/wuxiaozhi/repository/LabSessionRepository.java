package com.wuxiaozhi.repository;

import com.wuxiaozhi.entity.LabSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LabSessionRepository extends JpaRepository<LabSession, Long> {
    List<LabSession> findByUserIdOrderByStartTimeDesc(Long userId);
    Optional<LabSession> findByIdAndUserId(Long id, Long userId);
}
