package com.wuxiaozhi.repository;

import com.wuxiaozhi.entity.StepAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface StepAssessmentRepository extends JpaRepository<StepAssessment, Long> {
    List<StepAssessment> findByUserIdAndExperimentCodeOrderByStepIdAsc(Long userId, String experimentCode);
    Optional<StepAssessment> findByUserIdAndExperimentCodeAndStepId(Long userId, String experimentCode, int stepId);
}
