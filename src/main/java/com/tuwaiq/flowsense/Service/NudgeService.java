package com.tuwaiq.flowsense.Service;

import com.tuwaiq.flowsense.DTO.FlowDTO;
import com.tuwaiq.flowsense.DTO.NudgeDTO;
import com.tuwaiq.flowsense.DTO.WaitTimeDTO;
import com.tuwaiq.flowsense.Model.QueueTicket;
import com.tuwaiq.flowsense.Repository.QueueTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/*
 * Flow Nudges: suggest a nearby pavilion with a much shorter wait.
 *
 * The trap with showing wait times is herding: everyone runs to the "empty" pavilion
 * and it becomes the next bottleneck. So every target has a cap: in any 5-minute window
 * it accepts at most enough switches to close half the wait gap.
 *
 * Manual mode: offers appear only for long waits (20+ min) and the visitor has to open the app to see them.
 * Autopilot mode: offers start earlier (12+ min) and are pushed to visitors. The cap never changes.
 */
@Service
@RequiredArgsConstructor
public class NudgeService {

    private final WaitTimeService waitTimeService;
    private final QueueTicketRepository queueTicketRepository;

    private static final double NEARBY_DISTANCE = 40;     // map units (0-100 site map)

    // Set by AutopilotService when the supervisor switches modes
    private volatile boolean autopilot = false;

    public void setAutopilot(boolean autopilot) {
        this.autopilot = autopilot;
    }

    public boolean isAutopilot() {
        return autopilot;
    }

    public double minSourceWait() {
        return autopilot ? 12 : 20;
    }

    public NudgeDTO findNudge(Integer sourcePavilionId) {
        double maxTargetRatio = autopilot ? 0.65 : 0.55;
        int minMinutesSaved = autopilot ? 5 : 8;

        WaitTimeDTO source = waitTimeService.getWaitTime(sourcePavilionId);
        if (source.getWaitMinutes() < minSourceWait()) {
            return null;
        }

        List<WaitTimeDTO> candidates = new ArrayList<>();
        for (WaitTimeDTO t : waitTimeService.getAllWaitTimes()) {
            if (t.getPavilionId().equals(sourcePavilionId) || !"OPEN".equals(t.getStatus())) continue;
            if (distance(source, t) > NEARBY_DISTANCE) continue;
            if (t.getWaitMinutes() > source.getWaitMinutes() * maxTargetRatio) continue;
            candidates.add(t);
        }
        candidates.sort(Comparator.comparing(WaitTimeDTO::getWaitMinutes));

        LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
        for (WaitTimeDTO t : candidates) {
            int walk = walkMinutes(source, t);
            int saved = (int) Math.round(source.getWaitMinutes() - (t.getWaitMinutes() + walk));
            if (saved < minMinutesSaved) continue;

            // Anti-herding cap: same in both modes
            double cap = (source.getWaitMinutes() - t.getWaitMinutes()) / 2.0 * t.getThroughputPerMinute();
            int recentSwitches = queueTicketRepository
                    .countByPavilionIdAndNudgedFromPavilionIdIsNotNullAndJoinedAtAfter(t.getPavilionId(), fiveMinutesAgo);
            if (recentSwitches >= cap) continue;

            return new NudgeDTO(t.getPavilionId(), t.getName(), t.getGate(), t.getWaitMinutes(), walk, saved);
        }
        return null;
    }

    // Who moved where in the last 15 minutes: drawn as moving lines on the operations map
    public List<FlowDTO> getFlows() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime fiveAgo = now.minusMinutes(5);
        Map<String, int[]> counts = new LinkedHashMap<>();
        for (QueueTicket t : queueTicketRepository.findAllByNudgedFromPavilionIdIsNotNullAndJoinedAtAfter(now.minusMinutes(15))) {
            String key = t.getNudgedFromPavilionId() + ">" + t.getPavilionId();
            int[] c = counts.computeIfAbsent(key, k -> new int[2]);
            c[0]++;
            if (t.getJoinedAt().isAfter(fiveAgo)) c[1]++;
        }
        List<FlowDTO> flows = new ArrayList<>();
        counts.forEach((key, c) -> {
            String[] ids = key.split(">");
            flows.add(new FlowDTO(Integer.valueOf(ids[0]), Integer.valueOf(ids[1]), c[0], c[1]));
        });
        return flows;
    }

    private double distance(WaitTimeDTO a, WaitTimeDTO b) {
        return Math.hypot(a.getMapX() - b.getMapX(), a.getMapY() - b.getMapY());
    }

    // The whole site map (100 units) is roughly a 15-minute walk
    private int walkMinutes(WaitTimeDTO a, WaitTimeDTO b) {
        return (int) Math.max(1, Math.round(distance(a, b) * 0.15));
    }
}
