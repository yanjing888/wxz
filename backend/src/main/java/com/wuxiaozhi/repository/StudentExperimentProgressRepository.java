package com.wuxiaozhi.repository;

import com.wuxiaozhi.entity.StudentExperimentProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StudentExperimentProgressRepository extends JpaRepository<StudentExperimentProgress, Long> {
    List<StudentExperimentProgress> findByUserIdIn(Collection<Long> userIds);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from StudentExperimentProgress p where p.userId=:userId and p.experimentCode=:code")
    Optional<StudentExperimentProgress> lockByUserAndCode(@org.springframework.data.repository.query.Param("userId") Long userId,@org.springframework.data.repository.query.Param("code") String code);
    Optional<StudentExperimentProgress> findByUserIdAndExperimentCode(Long userId, String experimentCode);

    List<StudentExperimentProgress> findByUserIdAndExperimentCodeIn(Long userId, Collection<String> experimentCodes);

    List<StudentExperimentProgress> findByExperimentCodeAndUserIdIn(String experimentCode, Collection<Long> userIds);
}
