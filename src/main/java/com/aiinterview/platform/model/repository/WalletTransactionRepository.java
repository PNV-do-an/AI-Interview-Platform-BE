package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.WalletTransaction;
import com.aiinterview.platform.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
    List<WalletTransaction> findByUserOrderByCreatedAtDesc(User user);
    boolean existsByIdempotencyKey(String idempotencyKey);
    java.util.Optional<WalletTransaction> findByIdempotencyKey(String idempotencyKey);
}
