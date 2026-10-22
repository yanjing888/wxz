package com.wuxiaozhi.repository;

import com.wuxiaozhi.entity.TeacherExperimentContext;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TeacherExperimentContextRepository extends JpaRepository<TeacherExperimentContext, Long> {
    Optional<TeacherExperimentContext> findByTeacherIdAndExperimentCode(Long teacherId, String experimentCode);
}
