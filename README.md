# FlowSense

**Know the wait. Leave the line. Spread the crowd.**
Expo 2030 Riyadh Challenge · Track 1 Smart Visitor Experience · Challenge 1.6
Built by Ahmed Alhamami · Tuwaiq Academy

Visitors scan a QR sign to hold their place in a pavilion queue, explore freely, and get called back about 5 minutes before their turn. The same scans tell the Expo how long every queue is. No cameras, no sensors, no app install.

---

## Run it

**Requirements:** Java 17+, Maven (or IntelliJ), MySQL 8.

1. Open the folder in IntelliJ (File → Open → `flowsense/pom.xml` → Open as Project). Let Maven download dependencies.
2. Check the MySQL username and password in `src/main/resources/application.properties` (default `root` / `root`). The `flowsense` database is created automatically.
3. Run `FlowSenseApplication`.
4. Open:
   - http://localhost:8080 → **Visitor** web app (open on a phone, see below)
   - http://localhost:8080/gate.html → **Gate** screen for pavilion staff
   - http://localhost:8080/ops.html → **Operations** dashboard
   - http://localhost:8080/signs.html → printable **QR signs** for each pavilion

Thirteen sample pavilions in five districts are created on first start, and the demo traffic simulator starts automatically.

**No MySQL on the day?** Run with the backup in-memory database:
`mvn spring-boot:run -Dspring-boot.run.profiles=demo` (or in IntelliJ: Run configuration → Active profiles → `demo`). Data is lost on restart.

### Use a real phone (for the live demo)
1. Laptop and phone on the same Wi-Fi (or the phone's hotspot with the laptop connected to it, which is safer than venue Wi-Fi).
2. Find the laptop's IP: Windows `ipconfig`, Mac `ipconfig getifaddr en0`. Example: `192.168.1.20`.
3. On the laptop open `http://localhost:8080/signs.html`, type `http://192.168.1.20:8080` in the box, press **Update signs**.
4. Scan a sign with the phone's normal camera. The visitor page opens on that pavilion. Tap **Hold my place**.
5. If Windows Firewall asks, allow Java on private networks.

The gate camera scanner works on the laptop (localhost). Phones over plain `http://` cannot use the camera in a browser, so staff on a phone use the **Admit** buttons or type the code.

### Offline QR libraries (recommended before the demo)
The pages load two small libraries from a CDN. To work with no internet, download these two files into `src/main/resources/static/js/vendor/`:
- https://cdnjs.cloudflare.com/ajax/libs/qrcodejs/1.0.0/qrcode.min.js → `qrcode.min.js`
- https://unpkg.com/html5-qrcode@2.3.8/html5-qrcode.min.js → `html5-qrcode.min.js`

The pages try the local copy first, then the CDN. Without either, the visitor pass shows the ticket code as text instead of a QR.

---

## How it works

| Moment | Scan | What the system learns |
|---|---|---|
| Visitor scans the queue sign | **JOIN** | Someone is in line (virtually) |
| Staff scan the pass at the door | **ENTRY** | Door throughput, and the real wait for that visitor |
| Walk-in without FlowSense enters | **ENTRY** (viaApp = false) | What share of visitors use the app |

**Little's Law** turns those scans into minutes (`WaitTimeService`):

```
mu    = ENTRY scans in last 10 min / 10                (door rate, per minute)
share = app entries / all entries, last 60 min         (smoothed toward 40%)
L     = visitors holding a place / share               (estimated full line)
W     = L / mu                                         (wait in minutes)
```

**Calling back** (`QueueTicketService.callAndExpire`, every 10 s): a visitor is called when the people ahead of them will clear the door within 5 minutes (10 for the accessible lane). They then have 10 minutes (20 accessible) to arrive, or the place is released.

**Flow Nudges** (`NudgeService`): if a pavilion's wait is 20+ minutes, FlowSense offers a nearby pavilion whose wait is at most 55% of it and saves at least 8 minutes. Each target accepts only a capped number of switches per 5 minutes, so the "empty" pavilion never becomes the next bottleneck.

**Forecast**: a damped linear trend over the last 15 minutes of wait snapshots, projected 30 minutes ahead. Operations gets a recommendation before the line gets long.

**Overflow lane**: an extra door lane staffed from a quieter pavilion. While open, the pavilion admits 30% more visitors per minute.

### Manual vs Autopilot (Operations screen)

| | Manual (default) | Autopilot |
|---|---|---|
| Recommendations | Shown with an **Apply** button. Nothing happens until the supervisor presses it. | Applied automatically every 15 s. The Apply buttons still work. |
| Overflow lanes | Supervisor opens/closes | Opened when a wait is over 40 min or forecast to pass 35 min; closed when the line clears (kept open at least 5 min) |
| Flow Nudges | Offered when wait ≥ 20 min; visitor sees it only if they open the app | Offered from 12 min and **pushed** to visitors at the back of long queues |
| Anti-crowding cap | Same | Same |

**Guardrails** (`AutopilotService`): never closes or pauses a pavilion, never cancels a visitor's place. Any supervisor action on a pavilion locks Autopilot out of it for 10 minutes. Every decision goes to the **decision log** (`OpsAction` table) with its reason. After a restart FlowSense always starts in Manual.

### Site map
Each pavilion has its own architecture: Najdi mud-brick (Saudi Pavilion), monumental gate (Heritage Gate), mashrabiya lattice cube (Arts of the World), glass tower, geodesic dome, mobility hall, ocean shell with reflecting pool, planted terraces, water drum, white shells, stacked volumes, shade-umbrella plaza with flags, and an elevated ring. A heat glow and a growing queue of people at each door show where crowds are building, with no need to read numbers.

### Site layout
13 pavilions in five districts (Technology, Planet, Humanity, Culture, Collaboration) around Expo Plaza, with shaded corridors, gates, metro, food, first aid and water stations. **This is an illustrative layout for the demo, not the official master plan.** On start-up, existing pavilions are moved into this layout automatically.

**Privacy**: the Expo ticket is never stored. Only its SHA-256 hash and timestamps. No GPS, no cameras.

### The simulator is honest
`SimulationService` does not invent wait times. It creates visitors who scan exactly like real ones (JOIN through the real service, ENTRY at the door) and keeps a hidden line of walk-ins that FlowSense cannot see. Every number on the dashboard is computed from scans, the same way it would be at the Expo.

---

## Project structure

```
com.tuwaiq.flowsense
├── Advice       ControllerAdvice
├── Api          ApiException, ApiResponse (message only)
├── Controller   PavilionController, QueueTicketController, SimulationController
├── DTO          WaitTimeDTO, TicketStatusDTO, NudgeDTO, SummaryDTO (all computed, never stored)
├── Model        Pavilion, QueueTicket, ScanEvent, WaitSnapshot
├── Repository   one per model
└── Service      PavilionService, WaitTimeService, QueueTicketService, NudgeService, SimulationService
```

## API

All responses for add/update/delete/actions are HTTP 200 with `{"message": "..."}`. Errors are HTTP 400 with `{"message": "..."}`.

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/v1/api/pavilion/get` | All pavilions |
| POST | `/v1/api/pavilion/add` | Add pavilion (JSON body) |
| PUT | `/v1/api/pavilion/update/{id}` | Update pavilion |
| PUT | `/v1/api/pavilion/status/{id}/{OPEN\|PAUSED\|CLOSED}` | Pause / resume |
| PUT | `/v1/api/pavilion/overflow/{id}/{OPEN\|CLOSED}` | Open / close overflow lane |
| GET | `/v1/api/pavilion/flows/get` | Nudge movements, last 15 min |
| GET | `/v1/api/autopilot/status` | Mode, decisions today, locks |
| PUT | `/v1/api/autopilot/enable` · `disable` | Launch Autopilot / return to manual |
| GET | `/v1/api/autopilot/actions/get` | Decision log (newest 40) |
| DELETE | `/v1/api/pavilion/delete/{id}` | Delete (blocked while visitors hold places) |
| GET | `/v1/api/pavilion/wait-time/get` | Live wait for every pavilion |
| GET | `/v1/api/pavilion/wait-time/get/{id}` | Live wait for one pavilion |
| GET | `/v1/api/pavilion/summary/get` | Dashboard KPIs |
| POST | `/v1/api/queue-ticket/join/{pavilionId}/{ticketCode}/{STANDARD\|ACCESSIBLE}` | JOIN scan |
| GET | `/v1/api/queue-ticket/status/{ticketCode}` | Position, ETA, nudge |
| PUT | `/v1/api/queue-ticket/switch/{ticketCode}/{targetPavilionId}` | Accept a nudge |
| PUT | `/v1/api/queue-ticket/leave/{ticketCode}` | Leave queue |
| PUT | `/v1/api/queue-ticket/enter/{pavilionId}/{ticketCode}` | ENTRY scan |
| PUT | `/v1/api/queue-ticket/enter-by-id/{pavilionId}/{ticketId}` | Admit from the called list |
| POST | `/v1/api/queue-ticket/walk-in/{pavilionId}` | Count a walk-in |
| GET | `/v1/api/queue-ticket/called/{pavilionId}` | Visitors called right now |
| GET | `/v1/api/queue-ticket/waiting-count/{pavilionId}` | Size of the virtual queue |
| PUT | `/v1/api/queue-ticket/call-next/{pavilionId}` | Staff call the next visitor |
| GET | `/v1/api/queue-ticket/get` | All active tickets |
| GET/POST | `/v1/api/simulation/status` · `start` · `stop` · `surge/{pavilionId}` | Demo traffic |
| DELETE | `/v1/api/simulation/reset` | Delete all tickets, scans and history |

Example pavilion body:
```json
{ "name": "Kingdom Pavilion", "zone": "Culture", "gate": "S", "mapX": 50, "mapY": 50, "designThroughput": 12, "status": "OPEN" }
```
`zone` must be Technology, Planet, Humanity, Culture or Collaboration.

---

## Demo script (inside the 5-minute pitch)

When the traffic starts (or after **Reset all data**) every pavilion fills with a realistic crowd within about 20 seconds, so the map is never empty. A **surge** sends a crowd worth about 40 minutes of queue to one pavilion within ~20 seconds: you see people gather at its door, the glow turn amber then red, and redirect lines start moving to quieter pavilions. Start the app ~10 minutes early if you want the history charts filled.

1. Show **Operations** on the projector: live waits, the site map, the nudge flows (moving teal lines).
2. A judge scans a **QR sign** with their phone and taps **Hold my place**. Pick a green pavilion so they get called quickly.
3. Point at the phone: position, ETA, the "faster option nearby" card when it appears.
4. On **Operations**, choose AI & Humanity and press **Trigger surge** (a show just ended). Watch the wait climb, the alert appear, and nudge lines start.
5. On the **Gate** screen, press **Call next** if needed. The judge's phone buzzes and shows the pass.
6. Scan the pass with the laptop camera (or press **Admit**). The phone shows "Checked in" and the total wait.
7. Press **Launch Autopilot**. Trigger another surge and let the judges watch FlowSense open an overflow lane and redirect visitors on its own, with every decision explained in the decision log.
8. Close on the KPI row: hours out of line today, nudges accepted.

**Backup:** record a 60-second screen video of this flow the day before, in case the Wi-Fi fails.
