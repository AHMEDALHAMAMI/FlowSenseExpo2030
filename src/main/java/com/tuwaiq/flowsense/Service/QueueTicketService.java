package com.tuwaiq.flowsense.Service;

import com.tuwaiq.flowsense.Api.ApiException;
import com.tuwaiq.flowsense.DTO.NudgeDTO;
import com.tuwaiq.flowsense.DTO.TicketStatusDTO;
import com.tuwaiq.flowsense.DTO.WaitTimeDTO;
import com.tuwaiq.flowsense.Model.Pavilion;
import com.tuwaiq.flowsense.Model.QueueTicket;
import com.tuwaiq.flowsense.Model.ScanEvent;
import com.tuwaiq.flowsense.Repository.PavilionRepository;
import com.tuwaiq.flowsense.Repository.QueueTicketRepository;
import com.tuwaiq.flowsense.Repository.ScanEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class QueueTicketService {

    public static final List<String> ACTIVE = List.of("WAITING", "CALLED");

    private final QueueTicketRepository queueTicketRepository;
    private final ScanEventRepository scanEventRepository;
    private final PavilionRepository pavilionRepository;
    private final WaitTimeService waitTimeService;
    private final NudgeService nudgeService;

    @Value("${flowsense.call-ahead-minutes:5}")
    private int callAheadMinutes;

    @Value("${flowsense.return-window-minutes:10}")
    private int returnWindowMinutes;

    public List<QueueTicket> getActiveTickets() {
        return queueTicketRepository.findAllByStatusIn(ACTIVE);
    }

    // ---------- JOIN: visitor scans the QR sign at the queue ----------
    public QueueTicket join(Integer pavilionId, String ticketCode, String lane) {
        return joinInternal(pavilionId, hash(ticketCode), lane, null);
    }

    private QueueTicket joinInternal(Integer pavilionId, String hash, String lane, Integer nudgedFrom) {
        Pavilion pavilion = openPavilion(pavilionId);
        if (!lane.matches("^(STANDARD|ACCESSIBLE)$")) {
            throw new ApiException("Lane must be STANDARD or ACCESSIBLE");
        }
        QueueTicket active = queueTicketRepository.findFirstByTicketHashAndStatusIn(hash, ACTIVE);
        if (active != null) {
            if (active.getPavilionId().equals(pavilionId)) {
                throw new ApiException("You already hold a place at " + pavilion.getName());
            }
            Pavilion other = pavilionRepository.findPavilionById(active.getPavilionId());
            throw new ApiException("You already hold a place at " + (other == null ? "another pavilion" : other.getName())
                    + ". Leave that queue first.");
        }
        LocalDateTime now = LocalDateTime.now();
        QueueTicket ticket = new QueueTicket(null, hash, pavilionId, "WAITING", lane, now, null, null, nudgedFrom);
        queueTicketRepository.save(ticket);
        scanEventRepository.save(new ScanEvent(null, hash, pavilionId, "JOIN", true, now));
        waitTimeService.refresh();
        return ticket;
    }

    // Visitor accepts a Flow Nudge: leaves the busy queue and joins the suggested one
    public QueueTicket switchPavilion(String ticketCode, Integer targetPavilionId) {
        QueueTicket current = queueTicketRepository.findFirstByTicketHashAndStatusIn(hash(ticketCode), ACTIVE);
        if (current == null) {
            throw new ApiException("You are not holding a place in any queue");
        }
        return switchTicket(current, targetPavilionId);
    }

    // Moves a held place to the suggested pavilion, only if the suggestion is still valid (cap respected)
    public QueueTicket switchTicket(QueueTicket current, Integer targetPavilionId) {
        NudgeDTO nudge = nudgeService.findNudge(current.getPavilionId());
        if (nudge == null || !nudge.getPavilionId().equals(targetPavilionId)) {
            throw new ApiException("This suggestion is no longer available. Your current place is kept.");
        }
        Integer from = current.getPavilionId();
        current.setStatus("CANCELLED");
        queueTicketRepository.save(current);
        return joinInternal(targetPavilionId, current.getTicketHash(), current.getLane(), from);
    }

    // Used by the simulator: an arriving visitor accepts a nudge before joining
    public QueueTicket joinFromNudge(Integer targetPavilionId, String ticketCode, String lane, Integer fromPavilionId) {
        return joinInternal(targetPavilionId, hash(ticketCode), lane, fromPavilionId);
    }

    public void leave(String ticketCode) {
        QueueTicket current = queueTicketRepository.findFirstByTicketHashAndStatusIn(hash(ticketCode), ACTIVE);
        if (current == null) {
            throw new ApiException("You are not holding a place in any queue");
        }
        current.setStatus("CANCELLED");
        queueTicketRepository.save(current);
        waitTimeService.refresh();
    }

    // ---------- ENTRY: staff scan at the pavilion door ----------
    public String enter(Integer pavilionId, String ticketCode) {
        Pavilion pavilion = getPavilion(pavilionId);
        String hash = hash(ticketCode);
        QueueTicket ticket = queueTicketRepository.findFirstByTicketHashAndPavilionIdAndStatusIn(hash, pavilionId, ACTIVE);
        if (ticket == null) {
            scanEventRepository.save(new ScanEvent(null, hash, pavilionId, "ENTRY", false, LocalDateTime.now()));
            return "No FlowSense place at " + pavilion.getName() + " for this ticket. Entry recorded as walk-in.";
        }
        double waited = enterTicket(ticket);
        return "Welcome to " + pavilion.getName() + ". Total wait " + Math.round(waited) + " min.";
    }

    // Staff tap a name on the "called" list instead of scanning (e.g. phone screen cracked)
    public String enterById(Integer pavilionId, Integer ticketId) {
        Pavilion pavilion = getPavilion(pavilionId);
        QueueTicket ticket = queueTicketRepository.findQueueTicketById(ticketId);
        if (ticket == null || !ticket.getPavilionId().equals(pavilionId) || !ACTIVE.contains(ticket.getStatus())) {
            throw new ApiException("This visitor is not holding a place at " + pavilion.getName());
        }
        double waited = enterTicket(ticket);
        return "Welcome to " + pavilion.getName() + ". Total wait " + Math.round(waited) + " min.";
    }

    public double enterTicket(QueueTicket ticket) {
        LocalDateTime now = LocalDateTime.now();
        ticket.setStatus("ENTERED");
        ticket.setEnteredAt(now);
        queueTicketRepository.save(ticket);
        scanEventRepository.save(new ScanEvent(null, ticket.getTicketHash(), ticket.getPavilionId(), "ENTRY", true, now));
        return WaitTimeService.minutesBetween(ticket.getJoinedAt(), now);
    }

    public void walkIn(Integer pavilionId) {
        getPavilion(pavilionId);
        scanEventRepository.save(new ScanEvent(null, null, pavilionId, "ENTRY", false, LocalDateTime.now()));
    }

    // ---------- Staff tools ----------
    public List<QueueTicket> getCalled(Integer pavilionId) {
        getPavilion(pavilionId);
        return queueTicketRepository.findAllByPavilionIdAndStatusOrderByJoinedAtAsc(pavilionId, "CALLED");
    }

    public Integer countWaiting(Integer pavilionId) {
        return queueTicketRepository.findAllByPavilionIdAndStatusOrderByJoinedAtAsc(pavilionId, "WAITING").size();
    }

    // Door is free: staff pull the next virtual visitor forward
    public void callNext(Integer pavilionId) {
        getPavilion(pavilionId);
        List<QueueTicket> waiting = queueTicketRepository.findAllByPavilionIdAndStatusOrderByJoinedAtAsc(pavilionId, "WAITING");
        if (waiting.isEmpty()) {
            throw new ApiException("No visitors waiting in the virtual queue");
        }
        call(waiting.get(0), LocalDateTime.now());
    }

    // ---------- Visitor status ----------
    public TicketStatusDTO getStatus(String ticketCode) {
        QueueTicket t = queueTicketRepository.findFirstByTicketHashOrderByIdDesc(hash(ticketCode));
        if (t == null) {
            throw new ApiException("No queue place found for this ticket");
        }
        Pavilion p = getPavilion(t.getPavilionId());
        WaitTimeDTO w = waitTimeService.getWaitTime(p.getId());

        Integer position = null, peopleAhead = null;
        Double eta = null, waited = null;
        NudgeDTO nudge = null;
        LocalDateTime deadline = null;

        if ("WAITING".equals(t.getStatus())) {
            int waitingAhead = queueTicketRepository.countByPavilionIdAndStatusInAndJoinedAtBefore(
                    p.getId(), List.of("WAITING"), t.getJoinedAt());
            int called = queueTicketRepository.findAllByPavilionIdAndStatusOrderByJoinedAtAsc(p.getId(), "CALLED").size();
            position = waitingAhead + 1;
            peopleAhead = (int) Math.round((waitingAhead + called) / w.getAppShare());
            eta = WaitTimeService.round(peopleAhead / w.getThroughputPerMinute());
            if (eta > 10) {
                nudge = nudgeService.findNudge(p.getId());
            }
        } else if ("CALLED".equals(t.getStatus())) {
            position = 0;
            peopleAhead = 0;
            eta = 0.0;
            deadline = t.getCalledAt().plusMinutes(windowFor(t));
        } else if ("ENTERED".equals(t.getStatus())) {
            waited = WaitTimeService.round(WaitTimeService.minutesBetween(t.getJoinedAt(), t.getEnteredAt()));
        }

        return new TicketStatusDTO(t.getId(), shortCode(t.getTicketHash()), p.getId(), p.getName(), p.getGate(),
                t.getStatus(), t.getLane(), t.getJoinedAt(), t.getCalledAt(), deadline, t.getEnteredAt(),
                position, peopleAhead, eta, waited, nudge);
    }

    // ---------- Scheduler: call visitors back, expire no-shows ----------
    // Every 10 s: a visitor is called when the people ahead of them will clear the door
    // within the call-ahead time (5 min; 10 min for the accessible lane).
    @Scheduled(fixedRate = 10000, initialDelay = 8000)
    public void callAndExpire() {
        LocalDateTime now = LocalDateTime.now();
        for (Pavilion p : pavilionRepository.findAllByOrderByIdAsc()) {
            if (!"OPEN".equals(p.getStatus())) continue;
            WaitTimeDTO w = waitTimeService.getWaitTime(p.getId());
            int called = queueTicketRepository.findAllByPavilionIdAndStatusOrderByJoinedAtAsc(p.getId(), "CALLED").size();
            List<QueueTicket> waiting = queueTicketRepository.findAllByPavilionIdAndStatusOrderByJoinedAtAsc(p.getId(), "WAITING");
            for (int i = 0; i < waiting.size(); i++) {
                QueueTicket t = waiting.get(i);
                double minutesUntilDoor = (called + i) / w.getAppShare() / w.getThroughputPerMinute();
                int ahead = "ACCESSIBLE".equals(t.getLane()) ? callAheadMinutes * 2 : callAheadMinutes;
                if (minutesUntilDoor <= ahead) {
                    call(t, now);
                } else if (minutesUntilDoor > callAheadMinutes * 2) {
                    break;
                }
            }
        }
        List<QueueTicket> expired = new ArrayList<>();
        for (QueueTicket t : queueTicketRepository.findAllByStatusAndCalledAtBefore("CALLED", now.minusMinutes(returnWindowMinutes))) {
            if (t.getCalledAt().plusMinutes(windowFor(t)).isBefore(now)) {
                t.setStatus("EXPIRED");
                expired.add(t);
            }
        }
        queueTicketRepository.saveAll(expired);
        waitTimeService.refresh();
    }

    private void call(QueueTicket t, LocalDateTime now) {
        t.setStatus("CALLED");
        t.setCalledAt(now);
        queueTicketRepository.save(t);
    }

    private int windowFor(QueueTicket t) {
        return "ACCESSIBLE".equals(t.getLane()) ? returnWindowMinutes * 2 : returnWindowMinutes;
    }

    // ---------- helpers ----------
    private Pavilion getPavilion(Integer id) {
        Pavilion p = pavilionRepository.findPavilionById(id);
        if (p == null) {
            throw new ApiException("Pavilion not found");
        }
        return p;
    }

    private Pavilion openPavilion(Integer id) {
        Pavilion p = getPavilion(id);
        if (!"OPEN".equals(p.getStatus())) {
            throw new ApiException(p.getName() + " is not accepting virtual queue joins right now");
        }
        return p;
    }

    // Privacy by design: the Expo ticket number is never stored, only its SHA-256 hash
    public static String hash(String ticketCode) {
        if (ticketCode == null || ticketCode.trim().length() < 4) {
            throw new ApiException("Ticket code is missing or too short");
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(ticketCode.trim().toUpperCase().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new ApiException("Could not read ticket");
        }
    }

    public static String shortCode(String hash) {
        return hash.substring(0, 6).toUpperCase();
    }
}
