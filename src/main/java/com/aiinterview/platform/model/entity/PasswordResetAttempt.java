package com.aiinterview.platform.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

/** Một dòng = một lượt yêu cầu đặt lại mật khẩu, kể cả lượt bị chặn hay email không tồn tại. */
@Entity
@Table(name = "password_reset_attempts", indexes = {
        @Index(name = "idx_reset_account_created", columnList = "account,created_at"),
        @Index(name = "idx_reset_address_created", columnList = "address,created_at")
})
public class PasswordResetAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    protected PasswordResetAttempt() {
    }

    @Getter
    private String account;

    @Getter
    private String address;

    @Getter
    private LocalDateTime createdAt;

    public PasswordResetAttempt(String address, String account) {
        this.address = address;
        this.account = account;
        this.createdAt = LocalDateTime.now();
    }
}
