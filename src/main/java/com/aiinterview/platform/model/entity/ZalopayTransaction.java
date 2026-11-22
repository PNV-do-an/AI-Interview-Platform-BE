package com.aiinterview.platform.model.entity;

import com.aiinterview.platform.model.enums.ZalopayStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "zalopay_transactions", indexes = {
        @Index(name = "idx_zalopay_app_trans_id", columnList = "app_trans_id", unique = true),
        @Index(name = "idx_zalopay_user", columnList = "user_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZalopayTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "app_trans_id", nullable = false, unique = true, length = 64)
    private String appTransId;

    @Column(nullable = false)
    private Long amount;

    @Column(name = "order_info", length = 255)
    private String orderInfo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ZalopayStatus status = ZalopayStatus.PENDING;

    @Column(name = "zp_trans_id", length = 64)
    private String zpTransId;

    @Column(name = "channel", length = 16)
    private String channel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) status = ZalopayStatus.PENDING;
        if (amount == null) amount = 0L;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
