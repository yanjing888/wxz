package com.wuxiaozhi.repository;

import com.wuxiaozhi.entity.StudentFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentFileRepository extends JpaRepository<StudentFile, Long> {

    List<StudentFile> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<StudentFile> findByUserIdAndExperimentCodeOrderByCreatedAtDesc(Long userId, String experimentCode);

    Optional<StudentFile> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);
}
