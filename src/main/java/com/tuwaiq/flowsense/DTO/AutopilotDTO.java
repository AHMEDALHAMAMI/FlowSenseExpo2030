package com.tuwaiq.flowsense.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AutopilotDTO {
    private String mode;                    // MANUAL | AUTOPILOT
    private LocalDateTime since;
    private Integer actionsToday;           // decisions Autopilot made today
    private Integer overflowLanesOpen;
    private List<Integer> supervisorLocks;  // pavilions Autopilot leaves alone (recent human decision)
}
