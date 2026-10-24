package com.aiinterview.platform.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** One-use backup code proving the user lost their 2FA device (BCrypt hash stored, never the code). */
@Entity
@Table(name = "recovery_codes", indexes = {
        @Index(name = "idx_recovery_user_used", columnList = "user_id,used_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /** null = still usable. */
    @Column(name = "used_at")
    private LocalDateTime usedAt;
}
