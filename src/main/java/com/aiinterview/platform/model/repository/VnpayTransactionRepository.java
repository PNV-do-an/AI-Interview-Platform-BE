package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.VnpayTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface VnpayTransactionRepository extends JpaRepository<VnpayTransaction, Long> {
    Optional<VnpayTransaction> findByTxnRef(String txnRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VnpayTransaction v where v.txnRef = :txnRef")
    Optional<VnpayTransaction> findByTxnRefForUpdate(@Param("txnRef") String txnRef);

    List<VnpayTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);
}