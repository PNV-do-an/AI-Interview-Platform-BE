package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.PasswordResetAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface PasswordResetAttemptRepository extends JpaRepository<PasswordResetAttempt, Long> {
    PasswordResetAttempt findFirstByAccountOrderByIdAsc(String account);
    long countByAccountAndCreatedAtAfter(String account, LocalDateTime since);
    long countByAddressAndCreatedAtAfter(String address, LocalDateTime since);
}
