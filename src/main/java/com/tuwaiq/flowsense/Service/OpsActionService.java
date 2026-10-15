package com.tuwaiq.flowsense.Service;

import com.tuwaiq.flowsense.Model.OpsAction;
import com.tuwaiq.flowsense.Repository.OpsActionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OpsActionService {

    private final OpsActionRepository opsActionRepository;

    public void log(String actor, String action, Integer pavilionId, String detail) {
        String d = detail != null && detail.length() > 250 ? detail.substring(0, 250) : detail;
        opsActionRepository.save(new OpsAction(null, actor, action, pavilionId, d, LocalDateTime.now()));
    }

    public List<OpsAction> getRecent() {
        return opsActionRepository.findTop40ByOrderByIdDesc();
    }

    public void deleteAll() {
        opsActionRepository.deleteAll();
    }
}
