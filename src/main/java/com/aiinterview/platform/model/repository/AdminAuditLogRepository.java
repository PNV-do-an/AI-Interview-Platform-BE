package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    /** Latest entry of an action against a user — used to read the pre-delete snapshot. */
    Optional<AdminAuditLog> findFirstByTargetUserIdAndActionOrderByCreatedAtDesc(Long targetUserId, String action);

    /** Any param may be null, meaning "do not filter by it". */
    @Query("""
        SELECT a FROM AdminAuditLog a
        WHERE (:adminId IS NULL OR a.adminId = :adminId)
          AND (:targetUserId IS NULL OR a.targetUserId = :targetUserId)
          AND (:action IS NULL OR a.action = :action)
          AND (:createdFrom IS NULL OR a.createdAt >= :createdFrom)
          AND (:createdTo IS NULL OR a.createdAt <= :createdTo)
        """)
    Page<AdminAuditLog> search(
            @Param("adminId") Long adminId,
            @Param("targetUserId") Long targetUserId,
            @Param("action") String action,
            @Param("createdFrom") LocalDateTime createdFrom,
            @Param("createdTo") LocalDateTime createdTo,
            Pageable pageable
    );
}
