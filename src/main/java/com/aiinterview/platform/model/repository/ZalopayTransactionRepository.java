package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.ZalopayTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface ZalopayTransactionRepository extends JpaRepository<ZalopayTransaction, Long> {
    Optional<ZalopayTransaction> findByAppTransId(String appTransId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select z from ZalopayTransaction z where z.appTransId = :appTransId")
    Optional<ZalopayTransaction> findByAppTransIdForUpdate(@Param("appTransId") String appTransId);

    List<ZalopayTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);
}
