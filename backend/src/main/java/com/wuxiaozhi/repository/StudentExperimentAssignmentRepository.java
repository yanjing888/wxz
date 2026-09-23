package com.wuxiaozhi.repository;

import com.wuxiaozhi.entity.StudentExperimentAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface StudentExperimentAssignmentRepository extends JpaRepository<StudentExperimentAssignment, Long> {
    List<StudentExperimentAssignment> findByUserIdOrderByExperimentCodeAsc(Long userId);

    List<StudentExperimentAssignment> findByUserIdIn(Collection<Long> userIds);

    List<StudentExperimentAssignment> findByExperimentCode(String experimentCode);

    boolean existsByUserIdAndExperimentCode(Long userId, String experimentCode);

    void deleteByUserId(Long userId);

    void deleteByUserIdAndExperimentCode(Long userId, String experimentCode);
}
