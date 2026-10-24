package com.aiinterview.platform.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Một phiên đăng nhập (wave 010). "sid" trong access + refresh token trỏ đúng một dòng ở đây,
 * nên thu hồi phiên cắt được quyền truy cập ngay thay vì đợi access token hết hạn (24h).
 */
@Entity
@Table(name = "user_session", indexes = {
        @Index(name = "idx_session_session_id", columnList = "session_id"),
        @Index(name = "idx_session_user_id", columnList = "user_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** UUID nhét vào claim "sid" của cả access lẫn refresh token. */
    @Column(name = "session_id", nullable = false, unique = true, length = 36)
    private String sessionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Nhãn người đọc được, ví dụ "Chrome trên macOS" — dẫn xuất từ User-Agent, không lưu raw. */
    @Column(name = "device_label", length = 120)
    private String deviceLabel;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    /** Theo đúng TTL refresh token; hết hạn là dòng tự rơi khỏi danh sách, không cần job dọn. */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** null = đang hoạt động. */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        if (lastSeenAt == null) {
            lastSeenAt = now;
        }
    }

    @Transient
    public boolean isLive() {
        return revokedAt == null && expiresAt != null && expiresAt.isAfter(LocalDateTime.now());
    }
}
