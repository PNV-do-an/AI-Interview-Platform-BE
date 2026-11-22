package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.MomoTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface MomoTransactionRepository extends JpaRepository<MomoTransaction, Long> {
    Optional<MomoTransaction> findByOrderId(String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MomoTransaction m where m.orderId = :orderId")
    Optional<MomoTransaction> findByOrderIdForUpdate(@Param("orderId") String orderId);

    List<MomoTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);
}
