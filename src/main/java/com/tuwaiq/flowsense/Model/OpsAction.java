package com.tuwaiq.flowsense.Model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Audit trail: every operational decision, by a person or by Autopilot, with the reason.
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(indexes = {@Index(columnList = "created_at")})
public class OpsAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotEmpty(message = "Actor is required")
    @Pattern(regexp = "^(AUTOPILOT|SUPERVISOR|GATE_STAFF)$", message = "Actor must be AUTOPILOT, SUPERVISOR or GATE_STAFF")
    @Column(columnDefinition = "varchar(12) not null")
    private String actor;

    @NotEmpty(message = "Action is required")
    @Pattern(regexp = "^(OPEN_OVERFLOW|CLOSE_OVERFLOW|PUSH_OFFERS|PAUSE|RESUME|CALL_NEXT|MODE_AUTOPILOT|MODE_MANUAL)$",
            message = "Unknown action")
    @Column(columnDefinition = "varchar(20) not null")
    private String action;

    private Integer pavilionId;

    @Column(columnDefinition = "varchar(255)")
    private String detail;

    private LocalDateTime createdAt;
}
