package com.tuwaiq.flowsense.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TicketStatusDTO {
    private Integer ticketId;
    private String shortCode;
    private Integer pavilionId;
    private String pavilionName;
    private String gate;
    private String status;
    private String lane;
    private LocalDateTime joinedAt;
    private LocalDateTime calledAt;
    private LocalDateTime returnDeadline;
    private LocalDateTime enteredAt;
    private Integer position;        // place among FlowSense visitors
    private Integer peopleAhead;     // estimated, including walk-ins
    private Double etaMinutes;
    private Double waitedMinutes;    // only once ENTERED
    private NudgeDTO nudge;          // a faster nearby pavilion, when one exists
}
