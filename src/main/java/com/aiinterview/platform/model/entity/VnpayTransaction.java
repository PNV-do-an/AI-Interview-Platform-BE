package com.aiinterview.platform.model.entity;

import com.aiinterview.platform.model.enums.VnpayStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "vnpay_transactions", indexes = {
        @Index(name = "idx_vnpay_txn_ref", columnList = "txn_ref", unique = true),
        @Index(name = "idx_vnpay_user", columnList = "user_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VnpayTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "txn_ref", nullable = false, unique = true, length = 64)
    private String txnRef;

    @Column(nullable = false)
    private Long amount;

    @Column(name = "order_info", length = 255)
    private String orderInfo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private VnpayStatus status = VnpayStatus.PENDING;

    @Column(name = "vnp_transaction_no", length = 64)
    private String vnpTransactionNo;

    @Column(name = "bank_code", length = 32)
    private String bankCode;

    @Column(name = "pay_date", length = 32)
    private String payDate;

    @Column(name = "response_code", length = 8)
    private String responseCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) status = VnpayStatus.PENDING;
        if (amount == null) amount = 0L;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}