package com.wuxiaozhi.repository;

import com.wuxiaozhi.entity.StudentExperimentProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StudentExperimentProgressRepository extends JpaRepository<StudentExperimentProgress, Long> {
    Optional<StudentExperimentProgress> findByUserIdAndExperimentCode(Long userId, String experimentCode);

    List<StudentExperimentProgress> findByUserIdAndExperimentCodeIn(Long userId, Collection<String> experimentCodes);
}
