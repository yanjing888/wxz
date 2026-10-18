package com.wuxiaozhi.repository;
import com.wuxiaozhi.entity.LabWorkstation;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface LabWorkstationRepository extends JpaRepository<LabWorkstation,Long> {
    List<LabWorkstation> findByRoomIdOrderByCodeAsc(Long roomId);
    Optional<LabWorkstation> findByPairHash(String hash);
    Optional<LabWorkstation> findByGroupCodeHash(String hash);
    Optional<LabWorkstation> findByTerminalHash(String hash);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from LabWorkstation s where s.id=:id")
    Optional<LabWorkstation> lockById(@Param("id") Long id);
}
