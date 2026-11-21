package com.aiinterview.platform.service.auth.impl;

import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.model.dto.response.UserSessionResponse;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.entity.UserSession;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.UserSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Device/session bookkeeping. The rule that shapes everything else: a token without "sid" is treated
 * as live (issued before sessions existed), while an unknown or revoked sid is dead immediately.
 */
@ExtendWith(MockitoExtension.class)
class UserSessionServiceImplTest {

    private static final String CHROME_WINDOWS =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0.0.0 Safari/537.36";
    private static final String IP = "203.0.113.10";
    private static final String SID = "3b2a1c00-0000-4000-8000-000000000003";

    @Mock
    private UserSessionRepository userSessionRepository;
    @Mock
    private HttpServletRequest request;
    @InjectMocks
    private UserSessionServiceImpl service;

    private static User user() {
        return User.builder().id(1L).email("candidate@example.com").fullName("Candidate")
                .role(Role.ROLE_USER).enabled(true).build();
    }

    @Test
    @DisplayName("start ghi mot dong user_session: sid UUID, thiet bi, IP, UA, han 7 ngay")
    void start_recordsSessionFromRequest() {
        when(request.getHeader("User-Agent")).thenReturn(CHROME_WINDOWS);
        when(request.getHeader("X-Forwarded-For")).thenReturn(IP);
        LocalDateTime before = LocalDateTime.now();

        String sessionId = service.start(user(), request);

        ArgumentCaptor<UserSession> saved = ArgumentCaptor.forClass(UserSession.class);
        verify(userSessionRepository).save(saved.capture());
        UserSession row = saved.getValue();

        assertThat(UUID.fromString(sessionId)).isNotNull();
        assertThat(row.getSessionId()).isEqualTo(sessionId);
        assertThat(row.getUserId()).isEqualTo(1L);
        assertThat(row.getDeviceLabel()).isEqualTo("Chrome trên Windows");
        assertThat(row.getIpAddress()).isEqualTo(IP);
        assertThat(row.getUserAgent()).isEqualTo(CHROME_WINDOWS);
        assertThat(row.getLastSeenAt()).isCloseTo(before, within(5, ChronoUnit.SECONDS));
        assertThat(row.getExpiresAt()).isCloseTo(before.plusDays(7), within(5, ChronoUnit.SECONDS));
        assertThat(row.getRevokedAt()).isNull();
    }

    @Test
    @DisplayName("start voi request null (job/he thong) khong lam vo luong dang nhap")
    void start_withoutRequest_stillCreatesSession() {
        String sessionId = service.start(user(), null);

        ArgumentCaptor<UserSession> saved = ArgumentCaptor.forClass(UserSession.class);
        verify(userSessionRepository).save(saved.capture());
        UserSession row = saved.getValue();

        assertThat(UUID.fromString(sessionId)).isNotNull();
        assertThat(row.getDeviceLabel()).isEqualTo("Thiết bị không xác định");
        assertThat(row.getIpAddress()).isNull();
        assertThat(row.getUserAgent()).isNull();
    }

    @Test
    @DisplayName("User-Agent dai hon 512 ky tu bi cat truoc khi luu")
    void start_truncatesLongUserAgent() {
        String longUa = CHROME_WINDOWS + "x".repeat(600);
        when(request.getHeader("User-Agent")).thenReturn(longUa);

        service.start(user(), request);

        ArgumentCaptor<UserSession> saved = ArgumentCaptor.forClass(UserSession.class);
        verify(userSessionRepository).save(saved.capture());
        assertThat(saved.getValue().getUserAgent()).hasSize(512);
        assertThat(saved.getValue().getUserAgent()).isEqualTo(longUa.substring(0, 512));
    }

    @Test
    @DisplayName("Token khong co sid duoc coi la con song, khong tra DB")
    void isLive_nullSessionIsTreatedAsLive() {
        assertThat(service.isLive(null)).isTrue();
        verifyNoInteractions(userSessionRepository);
    }

    @Test
    @DisplayName("sid khong co trong user_session la chet")
    void isLive_unknownSessionIsDead() {
        when(userSessionRepository.findBySessionId(SID)).thenReturn(Optional.empty());

        assertThat(service.isLive(SID)).isFalse();
    }

    @Test
    @DisplayName("Phien da thu hoi hoac het han la chet, con han la song")
    void isLive_readsRevokedAndExpiry() {
        when(userSessionRepository.findBySessionId("revoked"))
                .thenReturn(Optional.of(session("revoked", LocalDateTime.now().plusDays(1),
                        LocalDateTime.now())));
        when(userSessionRepository.findBySessionId("expired"))
                .thenReturn(Optional.of(session("expired", LocalDateTime.now().minusMinutes(1), null)));
        when(userSessionRepository.findBySessionId("live"))
                .thenReturn(Optional.of(session("live", LocalDateTime.now().plusDays(1), null)));

        assertThat(service.isLive("revoked")).isFalse();
        assertThat(service.isLive("expired")).isFalse();
        assertThat(service.isLive("live")).isTrue();
    }

    @Test
    @DisplayName("touch voi sid null khong ghi gi")
    void touch_nullSessionDoesNothing() {
        service.touch(null);

        verifyNoInteractions(userSessionRepository);
    }

    @Test
    @DisplayName("touch chi ghi khi last_seen_at cu hon 5 phut (nguong do repository quyet dinh)")
    void touch_passesThrottleWindow() {
        ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> notSeenAfter = ArgumentCaptor.forClass(LocalDateTime.class);

        service.touch(SID);

        verify(userSessionRepository).touch(eq(SID), now.capture(), notSeenAfter.capture());
        assertThat(now.getValue().minusMinutes(5))
                .isCloseTo(notSeenAfter.getValue(), within(1, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("listForUser danh dau dung phien dang goi request")
    void listForUser_marksCurrentSession() {
        UserSession current = session(SID, LocalDateTime.now().plusDays(1), null);
        current.setId(10L);
        UserSession other = session("other", LocalDateTime.now().plusDays(1), null);
        other.setId(11L);
        when(userSessionRepository
                .findByUserIdAndRevokedAtIsNullAndExpiresAtGreaterThanOrderByLastSeenAtDesc(eq(1L), any()))
                .thenReturn(List.of(current, other));

        List<UserSessionResponse> sessions = service.listForUser(1L, SID);

        assertThat(sessions).hasSize(2);
        assertThat(sessions.get(0).isCurrent()).isTrue();
        assertThat(sessions.get(0).getId()).isEqualTo(10L);
        assertThat(sessions.get(1).isCurrent()).isFalse();
    }

    @Test
    @DisplayName("revoke chi chap nhan phien cua chinh minh")
    void revoke_unknownOrForeignSession_throws() {
        when(userSessionRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy phiên đăng nhập này cho tài khoản của bạn");
    }

    @Test
    @DisplayName("revoke dat revoked_at va luu lai")
    void revoke_setsRevokedAt() {
        UserSession row = session(SID, LocalDateTime.now().plusDays(1), null);
        when(userSessionRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(row));

        service.revoke(1L, 10L);

        assertThat(row.getRevokedAt()).isNotNull();
        verify(userSessionRepository).save(row);
    }

    @Test
    @DisplayName("revokeAll thu hoi moi phien con song cua user")
    void revokeAll_revokesEveryActiveSession() {
        service.revokeAll(1L);

        verify(userSessionRepository).revokeAllForUser(eq(1L), any(LocalDateTime.class));
        verify(userSessionRepository, never()).findByIdAndUserId(any(), any());
    }

    private static UserSession session(String sessionId, LocalDateTime expiresAt, LocalDateTime revokedAt) {
        return UserSession.builder()
                .sessionId(sessionId)
                .userId(1L)
                .deviceLabel("Chrome trên Windows")
                .expiresAt(expiresAt)
                .revokedAt(revokedAt)
                .lastSeenAt(LocalDateTime.now())
                .build();
    }
}
