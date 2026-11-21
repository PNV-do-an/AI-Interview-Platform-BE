package com.aiinterview.platform.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "wallets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    @Builder.Default
    private Long availableBalance = 0L;

    @Column(nullable = false)
    @Builder.Default
    private Long pendingBalance = 0L; // escrow held for this user as beneficiary (mentor)

    @Column(nullable = false)
    @Builder.Default
    private Long escrowBalance = 0L; // funds held from this user (candidate) in escrow

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (availableBalance == null) availableBalance = 0L;
        if (pendingBalance == null) pendingBalance = 0L;
        if (escrowBalance == null) escrowBalance = 0L;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
