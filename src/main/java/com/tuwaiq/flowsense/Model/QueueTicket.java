package com.tuwaiq.flowsense.Model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// One visitor's virtual place in one pavilion's queue.
// Privacy: only a SHA-256 hash of the Expo ticket is stored, never the ticket itself.
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(indexes = {
        @Index(columnList = "pavilion_id,status"),
        @Index(columnList = "ticket_hash,status")
})
public class QueueTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotEmpty(message = "Ticket hash is required")
    @Column(columnDefinition = "varchar(64) not null")
    private String ticketHash;

    @NotNull(message = "Pavilion id is required")
    private Integer pavilionId;

    @NotEmpty(message = "Status is required")
    @Pattern(regexp = "^(WAITING|CALLED|ENTERED|EXPIRED|CANCELLED)$",
            message = "Status must be WAITING, CALLED, ENTERED, EXPIRED or CANCELLED")
    @Column(columnDefinition = "varchar(10) not null")
    private String status;

    @NotEmpty(message = "Lane is required")
    @Pattern(regexp = "^(STANDARD|ACCESSIBLE)$", message = "Lane must be STANDARD or ACCESSIBLE")
    @Column(columnDefinition = "varchar(10) not null")
    private String lane;

    private LocalDateTime joinedAt;
    private LocalDateTime calledAt;
    private LocalDateTime enteredAt;

    // Set when the visitor accepted a Flow Nudge and switched here from a busier pavilion
    private Integer nudgedFromPavilionId;
}
