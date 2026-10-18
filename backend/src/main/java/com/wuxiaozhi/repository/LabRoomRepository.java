package com.wuxiaozhi.repository;
import com.wuxiaozhi.entity.LabRoom;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface LabRoomRepository extends JpaRepository<LabRoom,Long> {
    Optional<LabRoom> findByExperimentCode(String code);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from LabRoom r where r.id=:id")
    Optional<LabRoom> lockById(@Param("id") Long id);
}
