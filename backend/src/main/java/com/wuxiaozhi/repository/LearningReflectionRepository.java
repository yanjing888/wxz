package com.wuxiaozhi.repository;
import com.wuxiaozhi.entity.LearningReflection;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface LearningReflectionRepository extends JpaRepository<LearningReflection,Long> {
    Optional<LearningReflection> findByUserIdAndExperimentCode(Long userId,String experimentCode);
    List<LearningReflection> findByUserIdIn(Collection<Long> userIds);
}
