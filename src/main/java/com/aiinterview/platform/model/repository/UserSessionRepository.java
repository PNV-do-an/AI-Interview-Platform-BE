package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findBySessionId(String sessionId);

    List<UserSession> findByUserIdAndRevokedAtIsNullAndExpiresAtGreaterThanOrderByLastSeenAtDesc(
            Long userId, LocalDateTime now);

    Optional<UserSession> findByIdAndUserId(Long id, Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update UserSession s set s.revokedAt = :now where s.userId = :userId and s.revokedAt is null")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * Chỉ ghi khi lần thấy gần nhất đã cũ hơn ngưỡng, để một request bình thường không nhất
     * thiết phải phát sinh write.
     */
    @Modifying(clearAutomatically = true)
    @Query("update UserSession s set s.lastSeenAt = :now "
            + "where s.sessionId = :sessionId and s.revokedAt is null and s.lastSeenAt < :notSeenAfter")
    int touch(@Param("sessionId") String sessionId,
              @Param("now") LocalDateTime now,
              @Param("notSeenAfter") LocalDateTime notSeenAfter);
}
