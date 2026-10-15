package com.tuwaiq.flowsense.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SummaryDTO {
    private Double longestWaitMinutes;
    private String longestWaitPavilion;
    private Integer holdingVirtualPlace;
    private Integer nudgesAcceptedToday;
    private Integer entriesLastHour;
    private Double hoursOutOfLineToday;  // time visitors spent free instead of standing in line
}
