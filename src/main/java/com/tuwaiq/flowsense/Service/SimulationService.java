package com.tuwaiq.flowsense.Service;

import com.tuwaiq.flowsense.Api.ApiException;
import com.tuwaiq.flowsense.DTO.NudgeDTO;
import com.tuwaiq.flowsense.DTO.WaitTimeDTO;
import com.tuwaiq.flowsense.Model.Pavilion;
import com.tuwaiq.flowsense.Model.QueueTicket;
import com.tuwaiq.flowsense.Repository.PavilionRepository;
import com.tuwaiq.flowsense.Repository.QueueTicketRepository;
import com.tuwaiq.flowsense.Repository.ScanEventRepository;
import com.tuwaiq.flowsense.Repository.WaitSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/*
 * Demo traffic generator. It does NOT fake wait times: it only produces the same
 * scans real visitors would (JOIN at the sign, ENTRY at the door) through the real
 * services. Every number on the dashboard is computed from those scans.
 *
 * Each pavilion has a hidden physical line of walk-ins (visitors without FlowSense)
 * that the system cannot see. FlowSense has to estimate it from the app share.
 *
 * Built for a live pitch:
 *   - Warm-up: on start and after a reset, every pavilion receives a realistic crowd within ~20 seconds,
 *     so the map is never empty while judges watch.
 *   - Surge: a show "ends" next door and a crowd of ~40 minutes' worth arrives within ~25 seconds.
 *
 * Simulated visitors react to Flow Nudges the way people would:
 *   Manual mode    - they only see an offer if they happen to open the app (lower uptake).
 *   Autopilot mode - offers are pushed to their phone (higher uptake).
 * Real visitors (your phone) are never moved or admitted by the simulator.
 */
@Service
@RequiredArgsConstructor
public class SimulationService {

    private final PavilionRepository pavilionRepository;
    private final QueueTicketRepository queueTicketRepository;
    private final ScanEventRepository scanEventRepository;
    private final WaitSnapshotRepository waitSnapshotRepository;
    private final QueueTicketService queueTicketService;
    private final WaitTimeService waitTimeService;
    private final NudgeService nudgeService;
    private final OpsActionService opsActionService;
    private final AutopilotService autopilotService;

    @Value("${flowsense.simulation.auto-start:true}")
    private boolean autoStart;

    private static final double APP_ADOPTION = 0.42;     // visitors who scan the sign
    private static final double TICK_SECONDS = 1;
    private static final int BURST_SECONDS = 22;         // a burst crowd arrives over ~22 seconds
    private static final int SURGE_MINUTES_OF_QUEUE = 42; // surge adds ~42 minutes of queue at that door

    // Illustrative Expo layout: five districts around a central Collaboration plaza.
    // name, district, gate, mapX, mapY, door capacity per minute, popularity (demo arrivals vs capacity)
    private record Seed(String name, String zone, String gate, int x, int y, int capacity, double popularity) {}

    private static final List<Seed> LAYOUT = List.of(
            new Seed("AI & Humanity", "Technology", "B", 28, 22, 10, 0.98),
            new Seed("Future Mobility", "Technology", "A", 16, 31, 8, 0.92),
            new Seed("Space Dome", "Technology", "E", 31, 35, 6, 0.80),
            new Seed("Ocean Lab", "Planet", "C", 70, 19, 7, 0.50),
            new Seed("Green Riyadh", "Planet", "F", 83, 29, 8, 0.50),
            new Seed("Water Futures", "Planet", "G", 70, 34, 7, 0.86),
            new Seed("Health Horizons", "Humanity", "H", 70, 67, 8, 0.75),
            new Seed("Youth Lab", "Humanity", "Y", 83, 77, 6, 0.55),
            new Seed("Saudi Pavilion", "Culture", "K", 29, 67, 14, 1.00),
            new Seed("Heritage Gate", "Culture", "D", 16, 77, 9, 0.62),
            new Seed("Arts of the World", "Culture", "W", 31, 83, 7, 0.55),
            new Seed("Nations Plaza", "Collaboration", "N", 47, 46, 12, 0.80),
            new Seed("Innovation Exchange", "Collaboration", "X", 57, 56, 8, 0.65));

    private static final Map<String, String> OLD_ZONES = Map.of(
            "Opportunity", "Technology", "Mobility", "Technology", "Sustainability", "Planet");

    private volatile boolean running = false;
    private final Map<Integer, Double> hiddenWalkInLine = new ConcurrentHashMap<>();
    private final Map<Integer, LocalDateTime> surgeUntil = new ConcurrentHashMap<>();
    private final Map<Integer, double[]> burst = new ConcurrentHashMap<>();   // pavilionId -> {people left, people per second}
    private final Map<String, Double> popularity = new HashMap<>();
    private final Set<Integer> simulatedTickets = ConcurrentHashMap.newKeySet();
    private final Random random = new Random();

    // Creates the 13 pavilions, or moves existing ones into the five-district layout (safe to run on every start)
    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        for (Pavilion p : pavilionRepository.findAll()) {
            boolean dirty = false;
            if (OLD_ZONES.containsKey(p.getZone())) { p.setZone(OLD_ZONES.get(p.getZone())); dirty = true; }
            if (p.getOverflowLane() == null) { p.setOverflowLane("CLOSED"); dirty = true; }
            if (dirty) pavilionRepository.save(p);
        }
        for (Seed s : LAYOUT) {
            popularity.put(s.name(), s.popularity());
            Pavilion p = pavilionRepository.findPavilionByName(s.name());
            if (p == null) {
                p = new Pavilion(null, s.name(), s.zone(), s.gate(), s.x(), s.y(), s.capacity(), "OPEN", "CLOSED", null);
            } else {
                p.setZone(s.zone());
                p.setGate(s.gate());
                p.setMapX(s.x());
                p.setMapY(s.y());
                p.setDesignThroughput(s.capacity());
            }
            pavilionRepository.save(p);
        }
        waitTimeService.forceRefresh();
        if (autoStart) start();
    }

    public void start() {
        running = true;
        warmUpIfQuiet();
    }

    public void stop() {
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    // A show just ended next door: a crowd worth ~42 minutes of queue arrives within ~22 seconds,
    // then arrivals stay 1.5x higher for 6 minutes.
    public int surge(Integer pavilionId) {
        Pavilion p = pavilionRepository.findPavilionById(pavilionId);
        if (p == null) {
            throw new ApiException("Pavilion not found");
        }
        if (!running) {
            throw new ApiException("Start the demo traffic first");
        }
        if (!"OPEN".equals(p.getStatus())) {
            throw new ApiException(p.getName() + " is " + p.getStatus().toLowerCase() + ". Resume it first.");
        }
        int people = p.getDesignThroughput() * SURGE_MINUTES_OF_QUEUE;
        addBurst(p.getId(), people);
        surgeUntil.put(pavilionId, LocalDateTime.now().plusMinutes(6));
        return people;
    }

    public void reset() {
        queueTicketRepository.deleteAll();
        scanEventRepository.deleteAll();
        waitSnapshotRepository.deleteAll();
        opsActionService.deleteAll();
        for (Pavilion p : pavilionRepository.findAll()) {
            p.setOverflowLane("CLOSED");
            p.setOverflowChangedAt(null);
            pavilionRepository.save(p);
        }
        autopilotService.reset();
        hiddenWalkInLine.clear();
        surgeUntil.clear();
        burst.clear();
        simulatedTickets.clear();
        waitTimeService.forceRefresh();
        if (running) warmUpIfQuiet();
    }

    // Fill every pavilion with a realistic starting crowd (popular pavilions busier) in ~20 seconds
    private void warmUpIfQuiet() {
        if (queueTicketRepository.countByStatusIn(QueueTicketService.ACTIVE) > 30) return;
        for (Pavilion p : pavilionRepository.findAll()) {
            if (!"OPEN".equals(p.getStatus())) continue;
            double pop = popularity.getOrDefault(p.getName(), 0.6);
            double minutes = Math.max(2, (pop - 0.45) * 45);        // ~2 min (quiet) to ~25 min (busiest)
            addBurst(p.getId(), (int) Math.round(p.getDesignThroughput() * minutes));
        }
    }

    private void addBurst(Integer pavilionId, int people) {
        burst.merge(pavilionId, new double[]{people, people / (double) BURST_SECONDS},
                (a, b) -> new double[]{a[0] + b[0], Math.max(a[1], b[1])});
    }

    @Scheduled(fixedRate = 1000, initialDelay = 3000)
    public void tick() {
        if (!running) return;
        double dt = TICK_SECONDS / 60.0;
        LocalDateTime now = LocalDateTime.now();
        double minuteOfDay = now.getHour() * 60 + now.getMinute() + now.getSecond() / 60.0;
        boolean autopilot = nudgeService.isAutopilot();

        for (Pavilion p : pavilionRepository.findAllByOrderByIdAsc()) {
            if ("CLOSED".equals(p.getStatus())) continue;
            WaitTimeDTO w = waitTimeService.getWaitTime(p.getId());
            double line = hiddenWalkInLine.getOrDefault(p.getId(), 0.0);

            // ---- normal arrivals ----
            double rate = p.getDesignThroughput() * popularity.getOrDefault(p.getName(), 0.7)
                    * (1 + 0.3 * Math.sin(minuteOfDay / 9.0 + p.getId() * 1.7));
            LocalDateTime surgeEnd = surgeUntil.get(p.getId());
            if (surgeEnd != null && surgeEnd.isAfter(now)) {
                rate *= 1.5;
            }
            if (w.getWaitMinutes() > 60) rate *= 0.6;  // some people give up on very long lines
            int arrivals = poisson(rate * dt);

            // ---- burst arrivals (warm-up or surge) ----
            double[] b = burst.get(p.getId());
            if (b != null) {
                int n = (int) Math.min(b[0], Math.ceil(b[1] * TICK_SECONDS));
                arrivals += n;
                b[0] -= n;
                if (b[0] <= 0) burst.remove(p.getId());
            }

            for (int i = 0; i < arrivals; i++) {
                if ("OPEN".equals(p.getStatus()) && random.nextDouble() < APP_ADOPTION) {
                    appArrival(p, autopilot);
                } else {
                    line += 1;
                }
            }

            // ---- visitors already holding a place react to switch offers ----
            if ("OPEN".equals(p.getStatus()) && w.getWaitMinutes() >= nudgeService.minSourceWait()
                    && random.nextDouble() < (autopilot ? 0.7 : 0.15)) {
                switchOneHolder(p);
            }

            // ---- door service (overflow lane adds 30%) ----
            if ("PAUSED".equals(p.getStatus())) {
                hiddenWalkInLine.put(p.getId(), line);
                continue;
            }
            int served = poisson(p.effectiveCapacity() * (0.85 + 0.15 * random.nextDouble()) * dt);
            List<QueueTicket> called = new ArrayList<>(queueTicketRepository
                    .findAllByPavilionIdAndStatusOrderByJoinedAtAsc(p.getId(), "CALLED"));
            called.removeIf(t -> !simulatedTickets.contains(t.getId()));   // real visitors walk in themselves
            for (int i = 0; i < served; i++) {
                boolean hasCalled = !called.isEmpty();
                boolean hasWalkIn = line >= 1;
                if (hasCalled && (!hasWalkIn || random.nextDouble() < 0.5)) {
                    QueueTicket t = called.remove(0);
                    queueTicketService.enterTicket(t);
                    simulatedTickets.remove(t.getId());
                } else if (hasWalkIn) {
                    queueTicketService.walkIn(p.getId());
                    line -= 1;
                }
            }
            hiddenWalkInLine.put(p.getId(), line);
        }
        waitTimeService.refresh();
    }

    private void appArrival(Pavilion p, boolean autopilot) {
        String code = "SIM-" + UUID.randomUUID().toString().substring(0, 8);
        String lane = random.nextDouble() < 0.03 ? "ACCESSIBLE" : "STANDARD";
        NudgeDTO nudge = nudgeService.findNudge(p.getId());
        QueueTicket t;
        if (nudge != null && random.nextDouble() < (autopilot ? 0.6 : 0.35)) {
            t = queueTicketService.joinFromNudge(nudge.getPavilionId(), code, lane, p.getId());
        } else {
            t = queueTicketService.join(p.getId(), code, lane);
        }
        simulatedTickets.add(t.getId());
    }

    // The visitor furthest back benefits most from switching
    private void switchOneHolder(Pavilion p) {
        NudgeDTO nudge = nudgeService.findNudge(p.getId());
        if (nudge == null) return;
        for (QueueTicket t : queueTicketRepository.findAllByPavilionIdAndStatusOrderByJoinedAtDesc(p.getId(), "WAITING")) {
            if (!simulatedTickets.contains(t.getId())) continue;
            try {
                QueueTicket moved = queueTicketService.switchTicket(t, nudge.getPavilionId());
                simulatedTickets.remove(t.getId());
                simulatedTickets.add(moved.getId());
            } catch (ApiException ignored) {
                // suggestion expired between check and switch; try next tick
            }
            return;
        }
    }

    private int poisson(double lambda) {
        double l = Math.exp(-lambda), prod = random.nextDouble();
        int k = 0;
        while (prod > l) {
            k++;
            prod *= random.nextDouble();
        }
        return k;
    }
}
