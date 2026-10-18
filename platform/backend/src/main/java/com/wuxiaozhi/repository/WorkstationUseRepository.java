package com.wuxiaozhi.repository;
import com.wuxiaozhi.entity.WorkstationUse;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface WorkstationUseRepository extends JpaRepository<WorkstationUse,Long> {
    List<WorkstationUse> findByStationIdAndEndedAtIsNull(Long id);
    List<WorkstationUse> findByUserIdAndEndedAtIsNull(Long id);
    List<WorkstationUse> findByLessonIdAndEndedAtIsNull(Long id);
    List<WorkstationUse> findTop100ByStationIdOrderByJoinedAtDesc(Long id);
}
