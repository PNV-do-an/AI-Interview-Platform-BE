package com.aiinterview.platform.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_audit_log", indexes = {
        @Index(name = "idx_audit_created_at", columnList = "created_at"),
        @Index(name = "idx_audit_target_user_id", columnList = "target_user_id"),
        @Index(name = "idx_audit_admin_id", columnList = "admin_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long adminId;

    @Column(nullable = false)
    private String adminEmail;

    /** The user that was affected by the action (nullable for non-user actions) */
    @Column
    private Long targetUserId;

    /** Action identifier, one of {@link com.aiinterview.platform.model.enums.AuditAction} */
    @Column(nullable = false, length = 100)
    private String action;

    /** Human-readable detail describing what changed */
    @Column(columnDefinition = "TEXT")
    private String detail;

    /** false = the attempt was rejected by a business rule and nothing was changed */
    @Column(name = "succeeded", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 1")
    @Builder.Default
    private boolean succeeded = true;

    /** Why the attempt was rejected; null for successful actions */
    @Column(length = 500)
    private String reason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
