package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.PayosTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface PayosTransactionRepository extends JpaRepository<PayosTransaction, Long> {
    Optional<PayosTransaction> findByOrderCode(Long orderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PayosTransaction p where p.orderCode = :orderCode")
    Optional<PayosTransaction> findByOrderCodeForUpdate(@Param("orderCode") Long orderCode);

    List<PayosTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);
}
