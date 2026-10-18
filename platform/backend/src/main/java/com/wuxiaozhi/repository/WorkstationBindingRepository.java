package com.wuxiaozhi.repository;
import com.wuxiaozhi.entity.WorkstationBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface WorkstationBindingRepository extends JpaRepository<WorkstationBinding,Long> {
    List<WorkstationBinding> findByRoomId(Long roomId);
    List<WorkstationBinding> findByStationId(Long stationId);
}
