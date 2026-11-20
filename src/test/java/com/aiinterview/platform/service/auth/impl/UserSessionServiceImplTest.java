package com.aiinterview.platform.service.auth.impl;

import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.model.dto.response.UserSessionResponse;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.entity.UserSession;
import com.aiinterview.platform.model.repository.UserSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Vòng đời "thiết bị đăng nhập" — bảng user_session là thứ duy nhất khiến refresh token bị thu hồi
 * được ngay thay vì phải chờ access token hết hạn 24h.
 * <p>
 * Ba luật đáng khoá lại:
 * <ul>
 *   <li>token đời cũ không có {@code sid} vẫn phải sống ({@code isLive(null) == true}), nếu không sẽ
 *       đăng xuất oan toàn bộ người dùng cũ ngay khi deploy;</li>
 *   <li>{@code touch} chỉ ghi khi lần cuối nhìn thấy đã cũ hơn 5 phút, để GET thường không sinh write;</li>
 *   <li>{@code revoke} phải giới hạn theo userId, nếu không người dùng A thu hồi được phiên của B.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class UserSessionServiceImplTest {

    private static final String SESSION_ID = "11111111-2222-3333-4444-555555555555";
    private static final Long USER_ID = 20L;
    private static final String CHROME_WINDOWS =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    @Mock
    private UserSessionRepository userSessionRepository;
    @Mock
    private HttpServletRequest httpRequest;
    @InjectMocks
    private UserSessionServiceImpl service;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(USER_ID).email("candidate@example.com").build();
    }

    @Test
    @DisplayName("start: tao sid ngau nhien, luu nhan thiet bi, IP that va han dung bang TTL refresh token")
    void start_persistsSessionWithDeviceLabelIpAndExpiry() {
        when(httpRequest.getHeader("User-Agent")).thenReturn(CHROME_WINDOWS);
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10, 10.0.0.1");

        String sessionId = service.start(user, httpRequest);

        ArgumentCaptor<UserSession> saved = ArgumentCaptor.forClass(UserSession.class);
        verify(userSessionRepository).save(saved.capture());
        UserSession session = saved.getValue();

        assertThat(sessionId).hasSize(36).matches("[0-9a-f-]{36}");
        assertThat(session.getSessionId()).isEqualTo(sessionId);
        assertThat(session.getUserId()).isEqualTo(USER_ID);
        assertThat(session.getDeviceLabel()).isEqualTo("Chrome trên Windows");
        assertThat(session.getIpAddress()).isEqualTo("203.0.113.10");
        assertThat(session.getUserAgent()).isEqualTo(CHROME_WINDOWS);
        assertThat(session.getLastSeenAt()).isNotNull();
        assertThat(session.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(6));
    }

    @Test
    @DisplayName("start: khong co request (job/CLI) thi IP va user agent de null, nhan la 'khong xac dinh'")
    void start_withoutRequest_leavesIpAndUserAgentNull() {
        String sessionId = service.start(user, null);

        ArgumentCaptor<UserSession> saved = ArgumentCaptor.forClass(UserSession.class);
        verify(userSessionRepository).save(saved.capture());
        UserSession session = saved.getValue();

        assertThat(session.getIpAddress()).isNull();
        assertThat(session.getUserAgent()).isNull();
        assertThat(session.getDeviceLabel()).isEqualTo("Thiết bị không xác định");
        assertThat(session.getSessionId()).isEqualTo(sessionId);
    }

    @Test
    @DisplayName("start: user agent dai hon 512 ky tu bi cat, vi cot chi cho 512")
    void start_truncatesLongUserAgent() {
        String longAgent = "Chrome/124 " + "x".repeat(600);
        when(httpRequest.getHeader("User-Agent")).thenReturn(longAgent);

        service.start(user, httpRequest);

        ArgumentCaptor<UserSession> saved = ArgumentCaptor.forClass(UserSession.class);
        verify(userSessionRepository).save(saved.capture());
        assertThat(saved.getValue().getUserAgent()).hasSize(512);
    }

    @Test
    @DisplayName("isLive: token cu khong co sid van duoc coi la song (khong da nguoi dung cu ra")
    void isLive_withoutSessionId_treatsLegacyTokenAsLive() {
        assertThat(service.isLive(null)).isTrue();
        verifyNoInteractions(userSessionRepository);
    }

    @Test
    @DisplayName("isLive: sid khong ton tai trong bang thi coi nhu da bi thu hoi")
    void isLive_unknownSessionId_isNotLive() {
        when(userSessionRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());

        assertThat(service.isLive(SESSION_ID)).isFalse();
    }

    @Test
    @DisplayName("isLive: phien da bi thu hoi (logout / admin khoa) thi chet")
    void isLive_revokedSession_isNotLive() {
        UserSession session = liveSession();
        session.setRevokedAt(LocalDateTime.now().minusMinutes(1));
        when(userSessionRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(session));

        assertThat(service.isLive(SESSION_ID)).isFalse();
    }

    @Test
    @DisplayName("isLive: phien da het han theo TTL refresh token thi chet")
    void isLive_expiredSession_isNotLive() {
        UserSession session = liveSession();
        session.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(userSessionRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(session));

        assertThat(service.isLive(SESSION_ID)).isFalse();
    }

    @Test
    @DisplayName("isLive: phien binh thuong van song")
    void isLive_activeSession_isLive() {
        when(userSessionRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(liveSession()));

        assertThat(service.isLive(SESSION_ID)).isTrue();
    }

    @Test
    @DisplayName("touch: khong co sid thi khong ghi database")
    void touch_withoutSessionId_doesNothing() {
        service.touch(null);

        verifyNoInteractions(userSessionRepository);
    }

    @Test
    @DisplayName("touch: chi cap nhat khi last_seen cu hon 5 phut (nhuong nguong cho request thuong)")
    void touch_passesThrottleWindow() {
        service.touch(SESSION_ID);

        ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> threshold = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(userSessionRepository).touch(eq(SESSION_ID), now.capture(), threshold.capture());

        assertThat(threshold.getValue()).isEqualTo(now.getValue().minusMinutes(5));
    }

    @Test
    @DisplayName("listForUser: danh dau dung phien dang dung de goi request nay")
    void listForUser_marksCurrentSession() {
        UserSession current = liveSession();
        UserSession other = liveSession();
        other.setId(2L);
        other.setSessionId("other-session");
        other.setDeviceLabel("Firefox trên Linux");
        when(userSessionRepository
                .findByUserIdAndRevokedAtIsNullAndExpiresAtGreaterThanOrderByLastSeenAtDesc(
                        eq(USER_ID), any(LocalDateTime.class)))
                .thenReturn(List.of(current, other));

        List<UserSessionResponse> sessions = service.listForUser(USER_ID, SESSION_ID);

        assertThat(sessions).hasSize(2);
        assertThat(sessions.get(0).isCurrent()).isTrue();
        assertThat(sessions.get(0).getDeviceLabel()).isEqualTo("Chrome trên Windows");
        assertThat(sessions.get(1).isCurrent()).isFalse();
    }

    @Test
    @DisplayName("revoke: phien cua tai khoan khac thi khong duoc dong vao, bao khong tim thay")
    void revoke_sessionOfAnotherUser_isRejected() {
        when(userSessionRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(USER_ID, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy phiên đăng nhập này cho tài khoản của bạn");
        verify(userSessionRepository, never()).save(any(UserSession.class));
    }

    @Test
    @DisplayName("revoke: phien cua chinh minh thi dat moc thu hoi va luu lai")
    void revoke_ownSession_stampsRevokedAt() {
        UserSession session = liveSession();
        when(userSessionRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(session));

        service.revoke(USER_ID, 5L);

        assertThat(session.getRevokedAt()).isNotNull();
        verify(userSessionRepository).save(session);
    }

    @Test
    @DisplayName("revokeAll: thu hoi tat ca phien cua mot tai khoan bang mot cau update")
    void revokeAll_revokesEverySessionOfUser() {
        service.revokeAll(USER_ID);

        verify(userSessionRepository).revokeAllForUser(eq(USER_ID), any(LocalDateTime.class));
    }

    private UserSession liveSession() {
        return UserSession.builder()
                .id(1L)
                .sessionId(SESSION_ID)
                .userId(USER_ID)
                .deviceLabel("Chrome trên Windows")
                .ipAddress("203.0.113.10")
                .lastSeenAt(LocalDateTime.now().minusMinutes(1))
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
    }
}
