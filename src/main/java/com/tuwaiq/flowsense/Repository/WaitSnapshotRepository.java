package com.tuwaiq.flowsense.Repository;

import com.tuwaiq.flowsense.Model.WaitSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface WaitSnapshotRepository extends JpaRepository<WaitSnapshot, Integer> {

    List<WaitSnapshot> findAllByPavilionIdAndRecordedAtAfterOrderByRecordedAtAsc(Integer pavilionId, LocalDateTime time);

}
