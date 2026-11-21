package com.aiinterview.platform.security.jwt;

import com.aiinterview.platform.common.exception.InvalidAccountException;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.service.auth.UserService;
import com.aiinterview.platform.service.auth.UserSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Every request passes here, and the class is written fail-open: any problem (bad signature, revoked
 * session, disabled user, stale tokenVersion) ends with "continue unauthenticated", never with an
 * exception. That is the contract the @PreAuthorize annotations rely on, so each rejection path is
 * asserted together with "the authentication was NOT set".
 */
@ExtendWith(MockitoExtension.class)
class JwtFilterTest {

    private static final String EMAIL = "candidate@example.com";
    private static final String SID = "6a0f1c00-0000-4000-8000-000000000006";

    @Mock
    private UserService userService;
    @Mock
    private UserSessionService userSessionService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;
    @InjectMocks
    private JwtFilter filter;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .email(EMAIL)
                .password("encoded")
                .fullName("Candidate")
                .role(Role.ROLE_USER)
                .enabled(true)
                .locked(false)
                .tokenVersion(0)
                .build();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Khong co header Authorization thi di tiep voi danh tinh an danh")
    void noAuthorizationHeader_continuesAnonymously() throws Exception {
        filter.doFilterInternal(request, response, filterChain);

        assertThat(currentAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(userService, userSessionService);
    }

    @Test
    @DisplayName("Header khong phai Bearer (Basic, Token...) bi bo qua")
    void nonBearerHeader_isIgnored() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(currentAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("Tien to bearer viet thuong van duoc chap nhan")
    void lowercaseBearerPrefix_isAccepted() throws Exception {
        String token = JwtUtil.generateAccessToken(EMAIL, "ROLE_USER", 0, SID);
        when(request.getHeader("Authorization")).thenReturn("bearer " + token);
        when(userSessionService.isLive(SID)).thenReturn(true);
        when(userService.loadUserByUsername(EMAIL)).thenReturn(user);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(currentAuthentication()).isNotNull();
        assertThat(currentAuthentication().getName()).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("Mot so client boc token trong dau ngoac kep, filter phai go bo")
    void quotedToken_isUnwrapped() throws Exception {
        String token = JwtUtil.generateAccessToken(EMAIL, "ROLE_USER", 0, SID);
        when(request.getHeader("Authorization")).thenReturn("Bearer \"" + token + "\"");
        when(userSessionService.isLive(SID)).thenReturn(true);
        when(userService.loadUserByUsername(EMAIL)).thenReturn(user);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(currentAuthentication()).isNotNull();
        assertThat(currentAuthentication().getName()).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("Token hop le: dat authentication, ghi session id vao request va touch phien")
    void validToken_setsAuthenticationAndTouchesSession() throws Exception {
        String token = JwtUtil.generateAccessToken(EMAIL, "ROLE_USER", 0, SID);
        stubValidSession(token);

        filter.doFilterInternal(request, response, filterChain);

        Authentication auth = currentAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
        verify(request).setAttribute(JwtFilter.SESSION_ID_ATTRIBUTE, SID);
        verify(userSessionService).touch(SID);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Token khong co sid (phat truoc wave 010) chi bo qua buoc touch")
    void tokenWithoutSid_skipsTouchButStillAuthenticates() throws Exception {
        String token = JwtUtil.generateAccessToken(EMAIL, "ROLE_USER", 0, null);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(userSessionService.isLive(null)).thenReturn(true);
        when(userService.loadUserByUsername(EMAIL)).thenReturn(user);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(currentAuthentication()).isNotNull();
        verify(userSessionService, never()).touch(any());
        verify(request, never()).setAttribute(anyString(), any());
    }

    @Test
    @DisplayName("Challenge 2FA ky bang access key: chu ky dung nhung khong duoc cap quyen")
    void twoFactorChallengeToken_isNotGrantedAccess() throws Exception {
        String challenge = JwtUtil.generate2faChallengeToken(EMAIL);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + challenge);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(currentAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(userService, userSessionService);
    }

    @Test
    @DisplayName("Token hong / sai chu ky / het han: di tiep an danh, khong nem loi ra ngoai")
    void malformedToken_continuesAnonymously() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer not-a-jwt");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(currentAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("Phien da bi thu hoi (logout / doi mat khau / admin khoa) mat quyen ngay")
    void revokedSession_continuesAnonymously() throws Exception {
        String token = JwtUtil.generateAccessToken(EMAIL, "ROLE_USER", 0, SID);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(userSessionService.isLive(SID)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(currentAuthentication()).isNull();
        verifyNoInteractions(userService);
        verify(userSessionService, never()).touch(any());
    }

    @Test
    @DisplayName("Nguoi dung khong con ton tai thi khong cap quyen")
    void userNotFound_continuesAnonymously() throws Exception {
        String token = JwtUtil.generateAccessToken(EMAIL, "ROLE_USER", 0, SID);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(userSessionService.isLive(SID)).thenReturn(true);
        when(userService.loadUserByUsername(EMAIL))
                .thenThrow(new InvalidAccountException("User not found with email: " + EMAIL));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(currentAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Tai khoan chua kich hoat hoac da xoa mem khong duoc cap quyen")
    void disabledOrDeletedUser_continuesAnonymously() throws Exception {
        String token = JwtUtil.generateAccessToken(EMAIL, "ROLE_USER", 0, SID);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(userSessionService.isLive(SID)).thenReturn(true);
        User disabled = User.builder().id(1L).email(EMAIL).fullName("X").enabled(false).build();
        when(userService.loadUserByUsername(EMAIL)).thenReturn(disabled);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(currentAuthentication()).isNull();
    }

    @Test
    @DisplayName("Tai khoan bi admin khoa khong duoc cap quyen")
    void lockedUser_continuesAnonymously() throws Exception {
        String token = JwtUtil.generateAccessToken(EMAIL, "ROLE_USER", 0, SID);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(userSessionService.isLive(SID)).thenReturn(true);
        User locked = User.builder().id(1L).email(EMAIL).fullName("X").enabled(true).locked(true).build();
        when(userService.loadUserByUsername(EMAIL)).thenReturn(locked);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(currentAuthentication()).isNull();
    }

    @Test
    @DisplayName("Tai khoan da xoa mem (deletedAt) khong duoc cap quyen")
    void softDeletedUser_continuesAnonymously() throws Exception {
        String token = JwtUtil.generateAccessToken(EMAIL, "ROLE_USER", 0, SID);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(userSessionService.isLive(SID)).thenReturn(true);
        User deleted = User.builder().id(1L).email(EMAIL).fullName("X").enabled(true)
                .deletedAt(LocalDateTime.now()).build();
        when(userService.loadUserByUsername(EMAIL)).thenReturn(deleted);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(currentAuthentication()).isNull();
    }

    @Test
    @DisplayName("tv trong token lech cot users.token_version (logout/doi mat khau) thi mat quyen")
    void staleTokenVersion_continuesAnonymously() throws Exception {
        String token = JwtUtil.generateAccessToken(EMAIL, "ROLE_USER", 0, SID);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(userSessionService.isLive(SID)).thenReturn(true);
        user.setTokenVersion(4);
        when(userService.loadUserByUsername(EMAIL)).thenReturn(user);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(currentAuthentication()).isNull();
        verify(userSessionService, never()).touch(any());
    }

    private void stubValidSession(String token) {
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(userSessionService.isLive(SID)).thenReturn(true);
        when(userService.loadUserByUsername(EMAIL)).thenReturn(user);
    }

    private static Authentication currentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
