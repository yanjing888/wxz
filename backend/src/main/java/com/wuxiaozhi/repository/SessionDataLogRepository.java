package com.wuxiaozhi.repository;

import com.wuxiaozhi.entity.SessionDataLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SessionDataLogRepository extends JpaRepository<SessionDataLog, Long> {
    List<SessionDataLog> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    @Query("""
            select d from SessionDataLog d
            where d.sessionId = :sessionId
              and (d.officialData is null or d.officialData = true)
            order by d.createdAt asc
            """)
    List<SessionDataLog> findOfficialBySessionIdOrderByCreatedAtAsc(@Param("sessionId") Long sessionId);

    Optional<SessionDataLog> findFirstBySessionIdAndStepIdOrderByCreatedAtDesc(Long sessionId, int stepId);
}
