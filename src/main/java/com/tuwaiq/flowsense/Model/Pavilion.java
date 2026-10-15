package com.tuwaiq.flowsense.Model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Pavilion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotEmpty(message = "Pavilion name is required")
    @Size(max = 60, message = "Pavilion name must be at most 60 characters")
    @Column(columnDefinition = "varchar(60) not null unique")
    private String name;

    // The Expo district the pavilion belongs to
    @NotEmpty(message = "Zone is required")
    @Pattern(regexp = "^(Technology|Planet|Humanity|Culture|Collaboration)$",
            message = "Zone must be Technology, Planet, Humanity, Culture or Collaboration")
    @Column(columnDefinition = "varchar(20) not null")
    private String zone;

    @NotEmpty(message = "Gate is required")
    @Size(max = 5, message = "Gate must be at most 5 characters")
    @Column(columnDefinition = "varchar(5) not null")
    private String gate;

    // Position on the site map, 0-100 on each axis (used for "nearby" pavilions)
    @NotNull(message = "mapX is required")
    @Min(value = 0, message = "mapX must be between 0 and 100")
    @Max(value = 100, message = "mapX must be between 0 and 100")
    private Integer mapX;

    @NotNull(message = "mapY is required")
    @Min(value = 0, message = "mapY must be between 0 and 100")
    @Max(value = 100, message = "mapY must be between 0 and 100")
    private Integer mapY;

    // Design capacity: visitors the door can admit per minute.
    // Used as a fallback until enough ENTRY scans exist to measure it.
    @NotNull(message = "Design throughput is required")
    @Min(value = 1, message = "Design throughput must be at least 1 per minute")
    private Integer designThroughput;

    @NotEmpty(message = "Status is required")
    @Pattern(regexp = "^(OPEN|PAUSED|CLOSED)$", message = "Status must be OPEN, PAUSED or CLOSED")
    @Column(columnDefinition = "varchar(10) not null")
    private String status;

    // Extra door lane staffed from quieter pavilions: +30% door capacity while OPEN.
    // Opened by a supervisor (Apply button) or by Autopilot.
    @Pattern(regexp = "^(OPEN|CLOSED)$", message = "Overflow lane must be OPEN or CLOSED")
    @Column(columnDefinition = "varchar(6)")
    private String overflowLane;

    private java.time.LocalDateTime overflowChangedAt;

    public boolean isOverflowOpen() {
        return "OPEN".equals(overflowLane);
    }

    // Capacity the door can handle right now, including the overflow lane
    public double effectiveCapacity() {
        return designThroughput * (isOverflowOpen() ? 1.3 : 1.0);
    }
}
