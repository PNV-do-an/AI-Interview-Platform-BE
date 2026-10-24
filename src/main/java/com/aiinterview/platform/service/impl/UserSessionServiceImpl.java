package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.model.dto.response.UserSessionResponse;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.entity.UserSession;
import com.aiinterview.platform.model.repository.UserSessionRepository;
import com.aiinterview.platform.security.jwt.JwtUtil;
import com.aiinterview.platform.security.utils.SecurityUtils;
import com.aiinterview.platform.security.utils.UserAgentParser;
import com.aiinterview.platform.service.UserSessionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserSessionServiceImpl implements UserSessionService {

    /** Ghi lại "last seen" chậm hơn ngưỡng này thì bỏ qua, để request thường không phát sinh write. */
    private static final long TOUCH_THROTTLE_MINUTES = 5;

    private final UserSessionRepository userSessionRepository;

    @Override
    @Transactional
    public String start(User user, HttpServletRequest request) {
        String sessionId = UUID.randomUUID().toString();
        String userAgent = request == null ? null : request.getHeader("User-Agent");
        LocalDateTime now = LocalDateTime.now();
        userSessionRepository.save(UserSession.builder()
                .sessionId(sessionId)
                .userId(user.getId())
                .deviceLabel(UserAgentParser.describe(userAgent))
                .ipAddress(request == null ? null : SecurityUtils.getClientIP(request))
                .userAgent(truncate(userAgent, 512))
                .lastSeenAt(now)
                .expiresAt(now.plus(JwtUtil.refreshTokenTtl()))
                .build());
        return sessionId;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isLive(String sessionId) {
        if (sessionId == null) {
            // Token phát hành trước wave 010 không có sid — cho sống nốt tới khi hết hạn 24h.
            return true;
        }
        return userSessionRepository.findBySessionId(sessionId).map(UserSession::isLive).orElse(false);
    }

    @Override
    @Transactional
    public void touch(String sessionId) {
        if (sessionId == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        userSessionRepository.touch(sessionId, now, now.minusMinutes(TOUCH_THROTTLE_MINUTES));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSessionResponse> listForUser(Long userId, String currentSessionId) {
        return userSessionRepository
                .findByUserIdAndRevokedAtIsNullAndExpiresAtGreaterThanOrderByLastSeenAtDesc(
                        userId, LocalDateTime.now())
                .stream()
                .map(session -> UserSessionResponse.from(session, currentSessionId))
                .toList();
    }

    @Override
    @Transactional
    public void revoke(Long userId, Long sessionId) {
        UserSession session = userSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy phiên đăng nhập này cho tài khoản của bạn"));
        session.setRevokedAt(LocalDateTime.now());
        userSessionRepository.save(session);
    }

    @Override
    @Transactional
    public void revokeBySessionId(String sessionId) {
        if (sessionId == null) {
            return;
        }
        userSessionRepository.findBySessionId(sessionId).ifPresent(session -> {
            session.setRevokedAt(LocalDateTime.now());
            userSessionRepository.save(session);
        });
    }

    @Override
    @Transactional
    public void revokeAll(Long userId) {
        userSessionRepository.revokeAllForUser(userId, LocalDateTime.now());
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
