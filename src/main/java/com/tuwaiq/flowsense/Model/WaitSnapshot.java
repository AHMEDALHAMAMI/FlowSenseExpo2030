package com.tuwaiq.flowsense.Model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Wait time recorded every 15 seconds per pavilion. Feeds the history chart and the 30-minute forecast.
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(indexes = {@Index(columnList = "pavilion_id,recorded_at")})
public class WaitSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer pavilionId;

    private Double waitMinutes;

    private LocalDateTime recordedAt;
}
