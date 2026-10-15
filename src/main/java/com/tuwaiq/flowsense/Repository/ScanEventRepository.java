package com.tuwaiq.flowsense.Repository;

import com.tuwaiq.flowsense.Model.ScanEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ScanEventRepository extends JpaRepository<ScanEvent, Integer> {

    Integer countByPavilionIdAndTypeAndScannedAtAfter(Integer pavilionId, String type, LocalDateTime time);

    Integer countByPavilionIdAndTypeAndViaAppAndScannedAtAfter(Integer pavilionId, String type, Boolean viaApp, LocalDateTime time);

    Integer countByTypeAndScannedAtAfter(String type, LocalDateTime time);

    List<ScanEvent> findTop12ByPavilionIdOrderByIdDesc(Integer pavilionId);

}
