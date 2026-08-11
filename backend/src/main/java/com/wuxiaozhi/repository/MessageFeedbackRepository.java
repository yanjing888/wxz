package com.wuxiaozhi.repository;

import com.wuxiaozhi.entity.MessageFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MessageFeedbackRepository extends JpaRepository<MessageFeedback, Long> {
    Optional<MessageFeedback> findByMessageIdAndUserId(Long messageId, Long userId);

    List<MessageFeedback> findByUserIdAndSessionIdOrderByCreatedAtDesc(Long userId, Long sessionId);

    long countByRating(String rating);

    long countByProcessedFalse();

    @Query("""
            select f from MessageFeedback f
            where (:studentClass is null or :studentClass = '' or f.studentClass = :studentClass)
              and (:rating is null or :rating = '' or f.rating = :rating)
              and (:processed is null or f.processed = :processed)
              and (:experimentCode is null or :experimentCode = '' or f.experimentCode = :experimentCode)
            order by f.createdAt desc
            """)
    List<MessageFeedback> searchForTeacher(@Param("studentClass") String studentClass,
                                           @Param("rating") String rating,
                                           @Param("processed") Boolean processed,
                                           @Param("experimentCode") String experimentCode);

    long countByRatingAndStudentClass(String rating, String studentClass);

    long countByProcessedFalseAndStudentClass(String studentClass);

    @Query("""
            select count(f) from MessageFeedback f
            where (:studentClass is null or :studentClass = '' or f.studentClass = :studentClass)
            """)
    long countForTeacherClass(@Param("studentClass") String studentClass);
}
