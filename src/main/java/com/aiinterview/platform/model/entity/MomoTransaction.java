package com.aiinterview.platform.model.entity;

import com.aiinterview.platform.model.enums.MomoStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "momo_transactions", indexes = {
        @Index(name = "idx_momo_order_id", columnList = "order_id", unique = true),
        @Index(name = "idx_momo_user", columnList = "user_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MomoTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_id", nullable = false, unique = true, length = 64)
    private String orderId;

    @Column(nullable = false)
    private Long amount;

    @Column(name = "order_info", length = 255)
    private String orderInfo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MomoStatus status = MomoStatus.PENDING;

    @Column(name = "trans_id", length = 64)
    private String transId;

    @Column(name = "pay_type", length = 32)
    private String payType;

    @Column(name = "result_code", length = 16)
    private String resultCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) status = MomoStatus.PENDING;
        if (amount == null) amount = 0L;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
