package com.aiinterview.platform.model.entity;

import com.aiinterview.platform.model.enums.ModerationAction;
import com.aiinterview.platform.model.enums.ModerationActorRole;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One event in a job's moderation story. Separate from {@link AdminAuditLog} because the
 * actor can be the employer (SUBMITTED/RESUBMITTED) — the admin audit log forbids that by
 * design (001 R10: admin_id NOT NULL, no placeholder actors).
 */
@Entity
@Table(name = "job_moderation_records",
        indexes = @Index(name = "idx_jmr_job_created", columnList = "job_id, created_at"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobModerationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private JobDescription job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModerationActorRole actorRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModerationAction action;

    /** Rejection reason when action = REJECTED; null otherwise. */
    @Column(columnDefinition = "TEXT")
    private String note;

    /** Seconds between the job's submit and this decision; null for submit events and legacy rows. */
    private Long processingSeconds;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
