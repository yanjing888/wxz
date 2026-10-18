package com.wuxiaozhi.repository;
import com.wuxiaozhi.entity.LabLesson;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface LabLessonRepository extends JpaRepository<LabLesson,Long> {
    Optional<LabLesson> findFirstByRoomIdAndStatusOrderByStartedAtDesc(Long roomId,String status);
}
