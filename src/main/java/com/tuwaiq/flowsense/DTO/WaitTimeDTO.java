package com.tuwaiq.flowsense.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// Computed live, never stored, so it needs its own DTO.
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WaitTimeDTO {
    private Integer pavilionId;
    private String name;
    private String zone;
    private String gate;
    private Integer mapX;
    private Integer mapY;
    private String status;

    private Double waitMinutes;          // W = L / mu
    private Double measuredWaitMinutes;  // average JOIN -> ENTRY of recent app visitors (null if none)
    private Double peopleInLine;         // L, estimated full line (app + walk-ins)
    private Integer appWaiting;          // visitors holding a virtual place (WAITING + CALLED)
    private Double throughputPerMinute;  // mu, from ENTRY scans
    private Double appShare;             // share of entrants who came through FlowSense

    private String level;                // GREEN | AMBER | RED
    private Double forecast30Minutes;
    private String trend;                // UP | DOWN | FLAT
    private List<Double> history;        // last 60 minutes of wait snapshots
    private String alert;                // operations recommendation, null when fine
    private String recommendedAction;    // OPEN_OVERFLOW | CLOSE_OVERFLOW | null: what "Apply" (or Autopilot) will do
    private String overflowLane;         // OPEN | CLOSED
    private Integer designThroughput;
}
