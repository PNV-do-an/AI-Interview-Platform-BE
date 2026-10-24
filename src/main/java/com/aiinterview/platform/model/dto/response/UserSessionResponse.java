package com.aiinterview.platform.model.dto.response;

import com.aiinterview.platform.model.entity.UserSession;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Một dòng trong "Thiết bị đăng nhập hoạt động". */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSessionResponse {

    private Long id;
    private String deviceLabel;
    private String ipAddress;
    private LocalDateTime createdAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime expiresAt;
    /** true = đúng phiên đang dùng để gọi request này. */
    private boolean current;

    public static UserSessionResponse from(UserSession session, String currentSessionId) {
        return UserSessionResponse.builder()
                .id(session.getId())
                .deviceLabel(session.getDeviceLabel())
                .ipAddress(session.getIpAddress())
                .createdAt(session.getCreatedAt())
                .lastSeenAt(session.getLastSeenAt())
                .expiresAt(session.getExpiresAt())
                .current(session.getSessionId().equals(currentSessionId))
                .build();
    }
}
