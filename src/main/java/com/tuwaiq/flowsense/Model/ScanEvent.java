package com.tuwaiq.flowsense.Model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Raw sensing data. JOIN = visitor scanned the queue sign. ENTRY = staff scanned at the door.
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(indexes = {@Index(columnList = "pavilion_id,type,scanned_at")})
public class ScanEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // Null for walk-in visitors who never used FlowSense
    @Column(columnDefinition = "varchar(64)")
    private String ticketHash;

    @NotNull(message = "Pavilion id is required")
    private Integer pavilionId;

    @NotEmpty(message = "Type is required")
    @Pattern(regexp = "^(JOIN|ENTRY)$", message = "Type must be JOIN or ENTRY")
    @Column(columnDefinition = "varchar(5) not null")
    private String type;

    // True when the entering visitor came through the virtual queue
    @NotNull(message = "viaApp is required")
    private Boolean viaApp;

    private LocalDateTime scannedAt;
}
