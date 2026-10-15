package com.tuwaiq.flowsense.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NudgeDTO {
    private Integer pavilionId;
    private String name;
    private String gate;
    private Double waitMinutes;
    private Integer walkMinutes;
    private Integer minutesSaved;
}
