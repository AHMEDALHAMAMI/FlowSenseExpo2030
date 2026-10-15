package com.tuwaiq.flowsense.Repository;

import com.tuwaiq.flowsense.Model.OpsAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OpsActionRepository extends JpaRepository<OpsAction, Integer> {

    List<OpsAction> findTop40ByOrderByIdDesc();

    Integer countByActorAndCreatedAtAfter(String actor, LocalDateTime time);

    OpsAction findFirstByPavilionIdAndActionOrderByIdDesc(Integer pavilionId, String action);
}
