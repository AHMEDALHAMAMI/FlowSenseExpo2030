package com.tuwaiq.flowsense.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Visitors who accepted a Flow Nudge from one pavilion to another (drawn as moving lines on the map)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FlowDTO {
    private Integer fromPavilionId;
    private Integer toPavilionId;
    private Integer visitorsLast15Min;
    private Integer visitorsLast5Min;
}
