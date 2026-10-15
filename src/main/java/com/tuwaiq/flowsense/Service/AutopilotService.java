package com.tuwaiq.flowsense.Service;

import com.tuwaiq.flowsense.Api.ApiException;
import com.tuwaiq.flowsense.DTO.AutopilotDTO;
import com.tuwaiq.flowsense.DTO.NudgeDTO;
import com.tuwaiq.flowsense.DTO.WaitTimeDTO;
import com.tuwaiq.flowsense.Model.Pavilion;
import com.tuwaiq.flowsense.Model.QueueTicket;
import com.tuwaiq.flowsense.Repository.OpsActionRepository;
import com.tuwaiq.flowsense.Repository.PavilionRepository;
import com.tuwaiq.flowsense.Repository.QueueTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
 * Autopilot: the supervisor hands crowd distribution to FlowSense with one button.
 *
 * It does exactly what the Recommendations panel tells a human supervisor to do, by itself:
 *   1. Opens an overflow lane where a queue is long or forecast to get long, closes it when the line clears.
 *   2. Pushes Flow Nudge offers to visitors at the back of long queues (earlier than in manual mode).
 *
 * Guardrails:
 *   - It never closes or pauses a pavilion and never cancels a visitor's place. Visitors always choose.
 *   - The anti-herding cap on nudges is the same as in manual mode.
 *   - Any supervisor action on a pavilion locks Autopilot out of that pavilion for 10 minutes. The human always wins.
 *   - An overflow lane stays open at least 3 minutes before Autopilot may close it (no flapping).
 *   - Every decision is written to the decision log with its reason.
 *   - After a restart FlowSense always starts in MANUAL mode.
 */
@Service
@RequiredArgsConstructor
public class AutopilotService {

    private final PavilionRepository pavilionRepository;
    private final QueueTicketRepository queueTicketRepository;
    private final OpsActionRepository opsActionRepository;
    private final OpsActionService opsActionService;
    private final WaitTimeService waitTimeService;
    private final NudgeService nudgeService;

    private static final int SUPERVISOR_LOCK_MINUTES = 10;
    private static final int MIN_OVERFLOW_MINUTES = 3;
    private static final int OFFER_EVERY_MINUTES = 2;

    private volatile boolean enabled = false;
    private volatile LocalDateTime since = LocalDateTime.now();
    private final Map<Integer, LocalDateTime> supervisorLockUntil = new ConcurrentHashMap<>();
    private final Map<Integer, LocalDateTime> lastOfferAt = new ConcurrentHashMap<>();

    public void enable() {
        if (enabled) {
            throw new ApiException("Autopilot is already running");
        }
        enabled = true;
        since = LocalDateTime.now();
        nudgeService.setAutopilot(true);
        opsActionService.log("SUPERVISOR", "MODE_AUTOPILOT", null, "Supervisor launched Autopilot. FlowSense now distributes the crowd automatically.");
        run();
    }

    public void disable() {
        if (!enabled) {
            throw new ApiException("Already in manual mode");
        }
        enabled = false;
        since = LocalDateTime.now();
        nudgeService.setAutopilot(false);
        opsActionService.log("SUPERVISOR", "MODE_MANUAL", null, "Supervisor returned to manual control. Overflow lanes stay as they are.");
    }

    public boolean isEnabled() {
        return enabled;
    }

    // Called whenever a person changes a pavilion: Autopilot steps back from it for 10 minutes
    public void lockForSupervisor(Integer pavilionId) {
        supervisorLockUntil.put(pavilionId, LocalDateTime.now().plusMinutes(SUPERVISOR_LOCK_MINUTES));
    }

    private boolean isLocked(Integer pavilionId) {
        LocalDateTime until = supervisorLockUntil.get(pavilionId);
        return until != null && until.isAfter(LocalDateTime.now());
    }

    public AutopilotDTO getStatus() {
        List<Integer> locks = new ArrayList<>();
        supervisorLockUntil.forEach((id, until) -> { if (until.isAfter(LocalDateTime.now())) locks.add(id); });
        int lanes = 0;
        for (Pavilion p : pavilionRepository.findAll()) {
            if (p.isOverflowOpen()) lanes++;
        }
        return new AutopilotDTO(enabled ? "AUTOPILOT" : "MANUAL", since,
                opsActionRepository.countByActorAndCreatedAtAfter("AUTOPILOT", LocalDate.now().atStartOfDay()),
                lanes, locks);
    }

    public void reset() {
        supervisorLockUntil.clear();
        lastOfferAt.clear();
    }

    @Scheduled(fixedRate = 5000, initialDelay = 10000)
    public void run() {
        if (!enabled) return;
        LocalDateTime now = LocalDateTime.now();
        waitTimeService.refresh();
        boolean changed = false;

        for (WaitTimeDTO d : waitTimeService.getAllWaitTimes()) {
            if (!"OPEN".equals(d.getStatus()) || isLocked(d.getPavilionId())) continue;
            Pavilion p = pavilionRepository.findPavilionById(d.getPavilionId());
            if (p == null) continue;
            String numbers = "wait " + Math.round(d.getWaitMinutes()) + " min, forecast " + Math.round(d.getForecast30Minutes()) + " min";

            // 1. Overflow lanes: follow the same recommendation a supervisor would see
            if ("OPEN_OVERFLOW".equals(d.getRecommendedAction()) && !p.isOverflowOpen()) {
                setOverflow(p, true, now);
                opsActionService.log("AUTOPILOT", "OPEN_OVERFLOW", p.getId(),
                        p.getName() + ": " + numbers + ". Opened overflow lane (+30% door capacity).");
                changed = true;
            } else if ("CLOSE_OVERFLOW".equals(d.getRecommendedAction()) && p.isOverflowOpen()
                    && (p.getOverflowChangedAt() == null || p.getOverflowChangedAt().isBefore(now.minusMinutes(MIN_OVERFLOW_MINUTES)))) {
                setOverflow(p, false, now);
                opsActionService.log("AUTOPILOT", "CLOSE_OVERFLOW", p.getId(),
                        p.getName() + ": " + numbers + ". Line cleared, overflow lane closed and staff returned.");
                changed = true;
            }

            // 2. Push switch offers to visitors at the back of long queues
            if (d.getWaitMinutes() >= nudgeService.minSourceWait()) {
                LocalDateTime last = lastOfferAt.get(p.getId());
                if (last != null && last.isAfter(now.minusMinutes(OFFER_EVERY_MINUTES))) continue;
                NudgeDTO nudge = nudgeService.findNudge(p.getId());
                if (nudge == null) continue;
                int offered = visitorsWaitingLongerThan(d, 10);
                if (offered == 0) continue;
                lastOfferAt.put(p.getId(), now);
                opsActionService.log("AUTOPILOT", "PUSH_OFFERS", p.getId(),
                        "Offered " + offered + " visitors at " + p.getName() + " a switch to " + nudge.getName()
                                + " (" + Math.round(d.getWaitMinutes()) + " → " + Math.round(nudge.getWaitMinutes())
                                + " min, saves ~" + nudge.getMinutesSaved() + " min).");
            }
        }
        if (changed) waitTimeService.forceRefresh();
    }

    private void setOverflow(Pavilion p, boolean open, LocalDateTime now) {
        p.setOverflowLane(open ? "OPEN" : "CLOSED");
        p.setOverflowChangedAt(now);
        pavilionRepository.save(p);
    }

    // Visitors far enough back that switching is worth it for them
    private int visitorsWaitingLongerThan(WaitTimeDTO d, double minutes) {
        List<QueueTicket> waiting = queueTicketRepository.findAllByPavilionIdAndStatusOrderByJoinedAtAsc(d.getPavilionId(), "WAITING");
        int count = 0;
        for (int i = 0; i < waiting.size(); i++) {
            if (i / d.getAppShare() / d.getThroughputPerMinute() > minutes) count++;
        }
        return count;
    }
}
