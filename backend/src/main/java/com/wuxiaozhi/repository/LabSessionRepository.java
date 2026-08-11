package com.wuxiaozhi.repository;

import com.wuxiaozhi.entity.LabSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LabSessionRepository extends JpaRepository<LabSession, Long> {
    List<LabSession> findByUserIdOrderByStartTimeDesc(Long userId);
    List<LabSession> findByUserIdAndExperimentCodeOrderByStartTimeDesc(Long userId, String experimentCode);
    @Query("""
            select s from LabSession s
            where s.userId = :userId
              and exists (
                  select 1 from ChatMessage m
                  where m.sessionId = s.id
              )
            order by s.startTime desc
            """)
    List<LabSession> findConversationSessionsByUserId(@Param("userId") Long userId);

    @Query("""
            select s from LabSession s
            where s.userId = :userId
              and s.experimentCode = :experimentCode
              and exists (
                  select 1 from ChatMessage m
                  where m.sessionId = s.id
              )
            order by s.startTime desc
            """)
    List<LabSession> findConversationSessionsByUserIdAndExperimentCode(@Param("userId") Long userId,
                                                                       @Param("experimentCode") String experimentCode);

    Optional<LabSession> findFirstByUserIdAndExperimentCodeAndStatusOrderByStartTimeDesc(Long userId, String experimentCode, String status);
    Optional<LabSession> findByIdAndUserId(Long id, Long userId);

    List<LabSession> findByStatusOrderByStartTimeDesc(String status);

    List<LabSession> findByStatusAndStudentClassOrderByStartTimeDesc(String status, String studentClass);

    long countByStatus(String status);

    long countByStatusAndStudentClass(String status, String studentClass);
}
