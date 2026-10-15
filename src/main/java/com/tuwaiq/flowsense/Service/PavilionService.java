package com.tuwaiq.flowsense.Service;

import com.tuwaiq.flowsense.Api.ApiException;
import com.tuwaiq.flowsense.Model.Pavilion;
import com.tuwaiq.flowsense.Repository.PavilionRepository;
import com.tuwaiq.flowsense.Repository.QueueTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PavilionService {

    private final PavilionRepository pavilionRepository;
    private final QueueTicketRepository queueTicketRepository;
    private final OpsActionService opsActionService;
    private final AutopilotService autopilotService;
    private final WaitTimeService waitTimeService;

    public List<Pavilion> getAllPavilions() {
        return pavilionRepository.findAllByOrderByIdAsc();
    }

    public Pavilion getPavilion(Integer id) {
        Pavilion pavilion = pavilionRepository.findPavilionById(id);
        if (pavilion == null) {
            throw new ApiException("Pavilion not found");
        }
        return pavilion;
    }

    public void addPavilion(Pavilion pavilion) {
        if (pavilionRepository.findPavilionByName(pavilion.getName()) != null) {
            throw new ApiException("A pavilion with this name already exists");
        }
        pavilion.setId(null);
        if (pavilion.getOverflowLane() == null) {
            pavilion.setOverflowLane("CLOSED");
        }
        pavilionRepository.save(pavilion);
        waitTimeService.forceRefresh();
    }

    public void updatePavilion(Integer id, Pavilion pavilion) {
        Pavilion old = getPavilion(id);
        Pavilion sameName = pavilionRepository.findPavilionByName(pavilion.getName());
        if (sameName != null && !sameName.getId().equals(id)) {
            throw new ApiException("A pavilion with this name already exists");
        }
        old.setName(pavilion.getName());
        old.setZone(pavilion.getZone());
        old.setGate(pavilion.getGate());
        old.setMapX(pavilion.getMapX());
        old.setMapY(pavilion.getMapY());
        old.setDesignThroughput(pavilion.getDesignThroughput());
        old.setStatus(pavilion.getStatus());
        pavilionRepository.save(old);
        waitTimeService.forceRefresh();
    }

    // Supervisor pauses / resumes a pavilion (e.g. technical issue): no new virtual joins, calling stops.
    public void changeStatus(Integer id, String status) {
        if (!status.matches("^(OPEN|PAUSED|CLOSED)$")) {
            throw new ApiException("Status must be OPEN, PAUSED or CLOSED");
        }
        Pavilion pavilion = getPavilion(id);
        if (status.equals(pavilion.getStatus())) {
            throw new ApiException(pavilion.getName() + " is already " + status);
        }
        pavilion.setStatus(status);
        pavilionRepository.save(pavilion);
        autopilotService.lockForSupervisor(id);
        opsActionService.log("SUPERVISOR", "OPEN".equals(status) ? "RESUME" : "PAUSE", id,
                pavilion.getName() + " set to " + status + " by supervisor.");
        waitTimeService.forceRefresh();
    }

    // Supervisor opens / closes the overflow lane (the "Apply" button on a recommendation)
    public void changeOverflow(Integer id, String lane) {
        if (!lane.matches("^(OPEN|CLOSED)$")) {
            throw new ApiException("Overflow lane must be OPEN or CLOSED");
        }
        Pavilion pavilion = getPavilion(id);
        String current = pavilion.isOverflowOpen() ? "OPEN" : "CLOSED";
        if (lane.equals(current)) {
            throw new ApiException("Overflow lane at " + pavilion.getName() + " is already " + lane);
        }
        pavilion.setOverflowLane(lane);
        pavilion.setOverflowChangedAt(LocalDateTime.now());
        pavilionRepository.save(pavilion);
        autopilotService.lockForSupervisor(id);
        opsActionService.log("SUPERVISOR", "OPEN".equals(lane) ? "OPEN_OVERFLOW" : "CLOSE_OVERFLOW", id,
                pavilion.getName() + ": overflow lane " + ("OPEN".equals(lane) ? "opened" : "closed") + " by supervisor.");
        waitTimeService.forceRefresh();
    }

    public void deletePavilion(Integer id) {
        Pavilion pavilion = getPavilion(id);
        Integer active = queueTicketRepository.countByPavilionIdAndStatusIn(id, QueueTicketService.ACTIVE);
        if (active > 0) {
            throw new ApiException("Cannot delete: " + active + " visitors are holding a place in this pavilion");
        }
        pavilionRepository.delete(pavilion);
        waitTimeService.forceRefresh();
    }
}
