package com.wuxiaozhi.repository;

import com.wuxiaozhi.entity.LabSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LabSessionRepository extends JpaRepository<LabSession, Long> {
    List<LabSession> findByUserIdOrderByStartTimeDesc(Long userId);
    List<LabSession> findByUserIdAndExperimentCodeOrderByStartTimeDesc(Long userId, String experimentCode);
    @Query("""
            select s from LabSession s
            where s.userId = :userId
              and s.historyArchived = false
              and (
                  exists (
                      select 1 from ChatMessage m
                      where m.sessionId = s.id
                  )
                  or exists (
                      select 1 from SessionDataLog d
                      where d.sessionId = s.id
                  )
              )
            order by s.startTime desc
            """)
    List<LabSession> findConversationSessionsByUserId(@Param("userId") Long userId);

    @Query("""
            select s from LabSession s
            where s.userId = :userId
              and s.experimentCode = :experimentCode
              and s.historyArchived = false
              and (
                  exists (
                      select 1 from ChatMessage m
                      where m.sessionId = s.id
                  )
                  or exists (
                      select 1 from SessionDataLog d
                      where d.sessionId = s.id
                  )
              )
            order by s.startTime desc
            """)
    List<LabSession> findConversationSessionsByUserIdAndExperimentCode(@Param("userId") Long userId,
                                                                       @Param("experimentCode") String experimentCode);

    Optional<LabSession> findFirstByUserIdAndExperimentCodeAndStatusOrderByStartTimeDesc(Long userId, String experimentCode, String status);
    Optional<LabSession> findFirstByUserIdAndExperimentCodeAndHistoryArchivedFalseAndStatusOrderByStartTimeDesc(
            Long userId, String experimentCode, String status);
    Optional<LabSession> findFirstByUserIdAndExperimentCodeOrderByStartTimeDesc(Long userId, String experimentCode);
    Optional<LabSession> findFirstByUserIdAndExperimentCodeAndHistoryArchivedFalseOrderByStartTimeDesc(
            Long userId, String experimentCode);
    List<LabSession> findByUserIdAndHistoryArchivedFalseOrderByStartTimeDesc(Long userId);
    List<LabSession> findByUserIdAndExperimentCodeAndHistoryArchivedFalseOrderByStartTimeDesc(Long userId, String experimentCode);
    Optional<LabSession> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndExperimentCodeAndStatus(Long userId, String experimentCode, String status);

    List<LabSession> findByExperimentCodeAndUserIdInOrderByStartTimeDesc(String experimentCode, Collection<Long> userIds);

    List<LabSession> findByStatusOrderByStartTimeDesc(String status);

    List<LabSession> findByStatusAndStudentClassOrderByStartTimeDesc(String status, String studentClass);

    long countByStatus(String status);

    long countByStatusAndStudentClass(String status, String studentClass);
}
