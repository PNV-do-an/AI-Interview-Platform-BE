package com.aiinterview.platform.model.entity;

import com.aiinterview.platform.model.enums.EscrowStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "escrow_bookings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EscrowBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false)
    private User candidate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentor_id", nullable = false)
    private User mentor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id")
    private InterviewSlot slot;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false)
    private Long mentorAmount;

    @Column(nullable = false)
    private Long commissionAmount;

    /** Commission rate locked at booking time (e.g. 0.10). */
    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal commissionRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private EscrowStatus status = EscrowStatus.HELD;

    @Column(updatable = false)
    private LocalDateTime heldAt;

    private LocalDateTime releasedAt;

    private LocalDateTime complaintDeadline;

    @Column(length = 500)
    private String cancelReason;

    @Column(length = 60)
    private String idempotencyKey;

    @PrePersist
    protected void onCreate() {
        heldAt = LocalDateTime.now();
        complaintDeadline = heldAt.plusHours(48);
        if (status == null) status = EscrowStatus.HELD;
    }
}
