package com.tuwaiq.flowsense.Service;

import com.tuwaiq.flowsense.Api.ApiException;
import com.tuwaiq.flowsense.DTO.SummaryDTO;
import com.tuwaiq.flowsense.DTO.WaitTimeDTO;
import com.tuwaiq.flowsense.Model.Pavilion;
import com.tuwaiq.flowsense.Model.QueueTicket;
import com.tuwaiq.flowsense.Model.WaitSnapshot;
import com.tuwaiq.flowsense.Repository.PavilionRepository;
import com.tuwaiq.flowsense.Repository.QueueTicketRepository;
import com.tuwaiq.flowsense.Repository.ScanEventRepository;
import com.tuwaiq.flowsense.Repository.WaitSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/*
 * The core of FlowSense: turns QR scans into wait times using Little's Law.
 *
 *   mu (throughput)  = ENTRY scans per minute at the door (last 10 min)
 *   share            = fraction of entrants who came through FlowSense (last 60 min)
 *   L  (people)      = visitors holding a virtual place / share
 *   W  (wait)        = L / mu
 *
 * Results are cached for 2 seconds so the API, the call scheduler and the simulator
 * all read the same numbers without hammering the database.
 */
@Service
@RequiredArgsConstructor
public class WaitTimeService {

    private final PavilionRepository pavilionRepository;
    private final QueueTicketRepository queueTicketRepository;
    private final ScanEventRepository scanEventRepository;
    private final WaitSnapshotRepository waitSnapshotRepository;

    @Value("${flowsense.default-app-share:0.4}")
    private double defaultAppShare;

    @Value("${flowsense.call-ahead-minutes:5}")
    private int callAheadMinutes;

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private List<WaitTimeDTO> cache = new ArrayList<>();
    private long cacheTime = 0;
    private boolean dirty = false;

    // Recomputed at most every second, or within 300 ms after new scans arrive.
    // During a surge hundreds of scans arrive per minute; this keeps the database load bounded.
    public synchronized List<WaitTimeDTO> getAllWaitTimes() {
        long age = System.currentTimeMillis() - cacheTime;
        if (age > 1000 || (dirty && age > 300)) {
            cache = computeAll();
            cacheTime = System.currentTimeMillis();
            dirty = false;
        }
        return cache;
    }

    // New scans arrived: numbers will update within 300 ms
    public synchronized void refresh() {
        dirty = true;
    }

    // Something structural changed (pavilion added, paused, overflow lane): recompute on next read
    public synchronized void forceRefresh() {
        cacheTime = 0;
    }

    public WaitTimeDTO getWaitTime(Integer pavilionId) {
        for (WaitTimeDTO dto : getAllWaitTimes()) {
            if (dto.getPavilionId().equals(pavilionId)) {
                return dto;
            }
        }
        forceRefresh();
        for (WaitTimeDTO dto : getAllWaitTimes()) {
            if (dto.getPavilionId().equals(pavilionId)) {
                return dto;
            }
        }
        throw new ApiException("Pavilion not found");
    }

    private List<WaitTimeDTO> computeAll() {
        LocalDateTime now = LocalDateTime.now();
        List<WaitTimeDTO> list = new ArrayList<>();
        for (Pavilion p : pavilionRepository.findAllByOrderByIdAsc()) {
            list.add(compute(p, now));
        }
        addAlerts(list, now);
        return list;
    }

    private WaitTimeDTO compute(Pavilion p, LocalDateTime now) {
        double mu = throughput(p, now);
        double share = appShare(p, now);
        int appWaiting = queueTicketRepository.countByPavilionIdAndStatusIn(p.getId(), QueueTicketService.ACTIVE);
        Double measured = measuredWait(p.getId(), now);

        double peopleInLine = appWaiting / share;
        double wait;
        if (appWaiting > 0) {
            wait = peopleInLine / mu;
        } else {
            // Nobody holding a virtual place right now: fall back to what recent visitors actually waited
            wait = measured != null ? measured : 0.0;
            peopleInLine = wait * mu;
        }

        List<Double> history = new ArrayList<>();
        for (WaitSnapshot s : waitSnapshotRepository
                .findAllByPavilionIdAndRecordedAtAfterOrderByRecordedAtAsc(p.getId(), now.minusMinutes(60))) {
            history.add(s.getWaitMinutes());
        }
        double forecast = forecast(history, wait);
        String trend = forecast - wait > 3 ? "UP" : (forecast - wait < -3 ? "DOWN" : "FLAT");

        return new WaitTimeDTO(p.getId(), p.getName(), p.getZone(), p.getGate(), p.getMapX(), p.getMapY(),
                p.getStatus(), round(wait), measured == null ? null : round(measured), round(peopleInLine),
                appWaiting, round(mu), round(share), level(wait), round(forecast), trend, history, null,
                null, p.isOverflowOpen() ? "OPEN" : "CLOSED", p.getDesignThroughput());
    }

    // mu: measured from ENTRY scans, blended with design capacity while data is thin.
    // When the line is short the door is idle, so measured entries understate capacity:
    // never go below 60% of design throughput.
    public double throughput(Pavilion p, LocalDateTime now) {
        int entries = scanEventRepository.countByPavilionIdAndTypeAndScannedAtAfter(p.getId(), "ENTRY", now.minusMinutes(10));
        double measured = entries / 10.0;
        double weight = Math.min(1.0, entries / 10.0);
        double design = p.effectiveCapacity();
        double mu = weight * measured + (1 - weight) * design;
        mu = Math.max(mu, design * 0.6);
        if ("PAUSED".equals(p.getStatus())) {
            mu = Math.max(0.1, mu * 0.1);
        }
        return mu;
    }

    // share: what fraction of entrants used FlowSense. Smoothed toward the default
    // with 20 "virtual" observations so a handful of scans can't swing it wildly.
    public double appShare(Pavilion p, LocalDateTime now) {
        LocalDateTime from = now.minusMinutes(60);
        int total = scanEventRepository.countByPavilionIdAndTypeAndScannedAtAfter(p.getId(), "ENTRY", from);
        int viaApp = scanEventRepository.countByPavilionIdAndTypeAndViaAppAndScannedAtAfter(p.getId(), "ENTRY", true, from);
        double share = (viaApp + defaultAppShare * 20) / (total + 20.0);
        return Math.max(0.1, Math.min(1.0, share));
    }

    private Double measuredWait(Integer pavilionId, LocalDateTime now) {
        List<QueueTicket> entered = queueTicketRepository
                .findAllByPavilionIdAndStatusAndEnteredAtAfter(pavilionId, "ENTERED", now.minusMinutes(20));
        if (entered.isEmpty()) {
            return null;
        }
        double sum = 0;
        for (QueueTicket t : entered) {
            sum += minutesBetween(t.getJoinedAt(), t.getEnteredAt());
        }
        return sum / entered.size();
    }

    // Damped linear trend over the last 10 minutes of snapshots, projected 30 minutes ahead.
    private double forecast(List<Double> history, double current) {
        int n = Math.min(40, history.size());
        if (n < 4) {
            return current;
        }
        List<Double> d = history.subList(history.size() - n, history.size());
        double meanX = (n - 1) / 2.0, meanY = 0;
        for (double v : d) meanY += v;
        meanY /= n;
        double num = 0, den = 0;
        for (int i = 0; i < n; i++) {
            num += (i - meanX) * (d.get(i) - meanY);
            den += (i - meanX) * (i - meanX);
        }
        double slopePerSnapshot = num / den;          // snapshots are 15 s apart
        double projected = current + slopePerSnapshot * 120 * 0.6;
        return Math.max(0, Math.min(120, projected));
    }

    private void addAlerts(List<WaitTimeDTO> list, LocalDateTime now) {
        WaitTimeDTO quietest = null;
        for (WaitTimeDTO d : list) {
            if ("OPEN".equals(d.getStatus()) && (quietest == null || d.getWaitMinutes() < quietest.getWaitMinutes())) {
                quietest = d;
            }
        }
        String later = now.plusMinutes(30).format(HH_MM);
        String staffFrom = quietest == null ? "a quieter pavilion" : quietest.getName();
        // One rule set for both modes: a supervisor sees these with an Apply button,
        // Autopilot applies exactly the same recommendedAction by itself.
        for (WaitTimeDTO d : list) {
            boolean overflow = "OPEN".equals(d.getOverflowLane());
            boolean isQuietest = quietest != null && quietest.getPavilionId().equals(d.getPavilionId());
            if ("PAUSED".equals(d.getStatus())) {
                d.setAlert("Paused by staff. Visitors holding a place keep it.");
            } else if (d.getWaitMinutes() > 40 && !overflow) {
                d.setAlert("Wait over 40 min. Open the overflow lane (+30% door capacity) with 2 staff from " + staffFrom + ".");
                d.setRecommendedAction("OPEN_OVERFLOW");
            } else if (d.getWaitMinutes() > 40) {
                d.setAlert("Overflow lane open and Flow Nudges redirecting arrivals. Keep monitoring.");
            } else if (d.getForecast30Minutes() > 35 && d.getWaitMinutes() > 15 && !overflow && !isQuietest) {
                d.setAlert("Trending to " + Math.round(d.getForecast30Minutes()) + " min by " + later
                        + ". Open the overflow lane now with 2 staff from " + staffFrom + ".");
                d.setRecommendedAction("OPEN_OVERFLOW");
            } else if (overflow && d.getWaitMinutes() < 15 && d.getForecast30Minutes() < 20) {
                d.setAlert("Line has cleared. Close the overflow lane and return the staff.");
                d.setRecommendedAction("CLOSE_OVERFLOW");
            }
        }
    }

    public SummaryDTO getSummary() {
        LocalDateTime now = LocalDateTime.now();
        List<WaitTimeDTO> all = getAllWaitTimes();
        WaitTimeDTO longest = null;
        for (WaitTimeDTO d : all) {
            if (longest == null || d.getWaitMinutes() > longest.getWaitMinutes()) longest = d;
        }
        LocalDateTime today = LocalDate.now().atStartOfDay();
        double minutesFree = 0;
        for (QueueTicket t : queueTicketRepository.findAllByStatusAndEnteredAtAfter("ENTERED", today)) {
            minutesFree += Math.max(0, minutesBetween(t.getJoinedAt(), t.getEnteredAt()) - callAheadMinutes);
        }
        return new SummaryDTO(
                longest == null ? 0.0 : longest.getWaitMinutes(),
                longest == null ? "-" : longest.getName(),
                queueTicketRepository.countByStatusIn(QueueTicketService.ACTIVE),
                queueTicketRepository.countByNudgedFromPavilionIdIsNotNullAndJoinedAtAfter(today),
                scanEventRepository.countByTypeAndScannedAtAfter("ENTRY", now.minusMinutes(60)),
                round(minutesFree / 60.0));
    }

    @Scheduled(fixedRate = 15000, initialDelay = 5000)
    public void recordSnapshots() {
        refresh();
        LocalDateTime now = LocalDateTime.now();
        for (WaitTimeDTO d : getAllWaitTimes()) {
            waitSnapshotRepository.save(new WaitSnapshot(null, d.getPavilionId(), d.getWaitMinutes(), now));
        }
    }

    public static String level(double wait) {
        return wait < 15 ? "GREEN" : (wait < 35 ? "AMBER" : "RED");
    }

    public static double minutesBetween(LocalDateTime a, LocalDateTime b) {
        return Duration.between(a, b).toSeconds() / 60.0;
    }

    public static double round(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
