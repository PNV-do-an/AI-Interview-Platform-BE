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
 *
 * <p>Mỗi test chia 3 khối comment: <b>Arrange</b> → <b>Act</b> → <b>Assert</b>.</p>
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
        // Don du lieu an toan: SecurityContext la ThreadLocal, khong clear se ro sang test khac
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Khong co header Authorization thi di tiep voi danh tinh an danh")
    void noAuthorizationHeader_continuesAnonymously() throws Exception {
        // Arrange: request khong co header Authorization (mock mac dinh tra null)

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert: khong cap quyen, chuoi filter van chay tiep, va khong goi DB/phien
        assertThat(currentAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(userService, userSessionService);
    }

    @Test
    @DisplayName("Header khong phai Bearer (Basic, Token...) bi bo qua")
    void nonBearerHeader_isIgnored() throws Exception {
        // Arrange: client gui scheme khac (Basic) — khong phai thu filter nay xu ly
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert: bo qua header, van an danh va van di tiep
        assertThat(currentAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("Tien to bearer viet thuong van duoc chap nhan")
    void lowercaseBearerPrefix_isAccepted() throws Exception {
        // Arrange: token that, nhung scheme viet thuong "bearer " (mot so client lam vay)
        String token = JwtUtil.generateAccessToken(EMAIL, "ROLE_USER", 0, SID);
        when(request.getHeader("Authorization")).thenReturn("bearer " + token);
        when(userSessionService.isLive(SID)).thenReturn(true);
        when(userService.loadUserByUsername(EMAIL)).thenReturn(user);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert: van duoc cap quyen binh thuong (so sanh khong phan biet hoa thuong)
        assertThat(currentAuthentication()).isNotNull();
        assertThat(currentAuthentication().getName()).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("Mot so client boc token trong dau ngoac kep, filter phai go bo")
    void quotedToken_isUnwrapped() throws Exception {
        // Arrange: token hop le nhung bi boc trong cap ngoac kep
        String token = JwtUtil.generateAccessToken(EMAIL, "ROLE_USER", 0, SID);
        when(request.getHeader("Authorization")).thenReturn("Bearer \"" + token + "\"");
        when(userSessionService.isLive(SID)).thenReturn(true);
        when(userService.loadUserByUsername(EMAIL)).thenReturn(user);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert: filter tu go ngoac kep nen token van parse duoc => co quyen
        assertThat(currentAuthentication()).isNotNull();
        assertThat(currentAuthentication().getName()).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("Token hop le: dat authentication, ghi session id vao request va touch phien")
    void validToken_setsAuthenticationAndTouchesSession() throws Exception {
        // Arrange: token + phien song + user binh thuong
        String token = JwtUtil.generateAccessToken(EMAIL, "ROLE_USER", 0, SID);
        stubValidSession(token);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert: authentication mang dung authority lay tu user
        Authentication auth = currentAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");

        // Assert: sid duoc gan vao request (de controller logout biet xoa phien nao) va duoc touch
        verify(request).setAttribute(JwtFilter.SESSION_ID_ATTRIBUTE, SID);
        verify(userSessionService).touch(SID);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Token khong co sid (phat truoc wave 010) chi bo qua buoc touch")
    void tokenWithoutSid_skipsTouchButStillAuthenticates() throws Exception {
        // Arrange: token cu, khong mang claim sid
        String token = JwtUtil.generateAccessToken(EMAIL, "ROLE_USER", 0, null);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(userSessionService.isLive(null)).thenReturn(true);
        when(userService.loadUserByUsername(EMAIL)).thenReturn(user);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert: van dang nhap duoc (khong pha vo token cu con han)
        assertThat(currentAuthentication()).isNotNull();

        // Assert: nhung khong co gi de touch / gan attribute
        verify(userSessionService, never()).touch(any());
        verify(request, never()).setAttribute(anyString(), any());
    }

    @Test
    @DisplayName("Challenge 2FA ky bang access key: chu ky dung nhung khong duoc cap quyen")
    void twoFactorChallengeToken_isNotGrantedAccess() throws Exception {
        // Arrange: challenge token cua buoc 2FA (chu ky hop le, co claim purpose)
        String challenge = JwtUtil.generate2faChallengeToken(EMAIL);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + challenge);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert: bi tu choi dua vao claim purpose, khong cham vao user/session
        assertThat(currentAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(userService, userSessionService);
    }

    @Test
    @DisplayName("Token hong / sai chu ky / het han: di tiep an danh, khong nem loi ra ngoai")
    void malformedToken_continuesAnonymously() throws Exception {
        // Arrange: chuoi khong phai JWT (tuong duong token hong / bi sua / het han)
        when(request.getHeader("Authorization")).thenReturn("Bearer not-a-jwt");

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert: fail-open — nuot loi, van goi tiep chain (de @PreAuthorize tra 401/403)
        assertThat(currentAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("Phien da bi thu hoi (logout / doi mat khau / admin khoa) mat quyen ngay")
    void revokedSession_continuesAnonymously() throws Exception {
        // Arrange: token van con han nhung phien da bi thu hoi trong DB
        String token = JwtUtil.generateAccessToken(EMAIL, "ROLE_USER", 0, SID);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(userSessionService.isLive(SID)).thenReturn(false);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert: chan ngay o buoc isLive, khong can doc user
        assertThat(currentAuthentication()).isNull();
        verifyNoInteractions(userService);
        verify(userSessionService, never()).touch(any());
    }

    @Test
    @DisplayName("Nguoi dung khong con ton tai thi khong cap quyen")
    void userNotFound_continuesAnonymously() throws Exception {
        // Arrange: token + phien hop le nhung user da bi xoa cung khoi DB
        String token = JwtUtil.generateAccessToken(EMAIL, "ROLE_USER", 0, SID);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(userSessionService.isLive(SID)).thenReturn(true);
        when(userService.loadUserByUsername(EMAIL))
                .thenThrow(new InvalidAccountException("User not found with email: " + EMAIL));

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert: nuot exception cua UserDetailsService, khong cap quyen va van di tiep
        assertThat(currentAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Tai khoan chua kich hoat hoac da xoa mem khong duoc cap quyen")
    void disabledOrDeletedUser_continuesAnonymously() throws Exception {
        // Arrange: user ton tai nhung enabled = false (chua kich hoat email)
        String token = JwtUtil.generateAccessToken(EMAIL, "ROLE_USER", 0, SID);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(userSessionService.isLive(SID)).thenReturn(true);
        User disabled = User.builder().id(1L).email(EMAIL).fullName("X").enabled(false).build();
        when(userService.loadUserByUsername(EMAIL)).thenReturn(disabled);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert: token hop le nhung tai khoan chua du dieu kien => an danh
        assertThat(currentAuthentication()).isNull();
    }

    @Test
    @DisplayName("Tai khoan bi admin khoa khong duoc cap quyen")
    void lockedUser_continuesAnonymously() throws Exception {
        // Arrange: user enabled nhung bi admin khoa (locked = true)
        String token = JwtUtil.generateAccessToken(EMAIL, "ROLE_USER", 0, SID);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(userSessionService.isLive(SID)).thenReturn(true);
        User locked = User.builder().id(1L).email(EMAIL).fullName("X").enabled(true).locked(true).build();
        when(userService.loadUserByUsername(EMAIL)).thenReturn(locked);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert: khoa co hieu luc ngay voi token dang luu hanh
        assertThat(currentAuthentication()).isNull();
    }

    @Test
    @DisplayName("Tai khoan da xoa mem (deletedAt) khong duoc cap quyen")
    void softDeletedUser_continuesAnonymously() throws Exception {
        // Arrange: user da xoa mem (deletedAt khac null) nhung ban ghi con trong DB
        String token = JwtUtil.generateAccessToken(EMAIL, "ROLE_USER", 0, SID);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(userSessionService.isLive(SID)).thenReturn(true);
        User deleted = User.builder().id(1L).email(EMAIL).fullName("X").enabled(true)
                .deletedAt(LocalDateTime.now()).build();
        when(userService.loadUserByUsername(EMAIL)).thenReturn(deleted);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert: tai khoan xoa mem khong con quyen truy cap
        assertThat(currentAuthentication()).isNull();
    }

    @Test
    @DisplayName("tv trong token lech cot users.token_version (logout/doi mat khau) thi mat quyen")
    void staleTokenVersion_continuesAnonymously() throws Exception {
        // Arrange: token phat ra voi tv=0, nhung DB da tang len 4 (doi mat khau / logout all)
        String token = JwtUtil.generateAccessToken(EMAIL, "ROLE_USER", 0, SID);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(userSessionService.isLive(SID)).thenReturn(true);
        user.setTokenVersion(4);
        when(userService.loadUserByUsername(EMAIL)).thenReturn(user);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert: lech tokenVersion = token da bi dao thai, khong cap quyen va khong touch
        assertThat(currentAuthentication()).isNull();
        verify(userSessionService, never()).touch(any());
    }

    /** Stub chung cho cac test "token hop le": header + phien song + user binh thuong. */
    private void stubValidSession(String token) {
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(userSessionService.isLive(SID)).thenReturn(true);
        when(userService.loadUserByUsername(EMAIL)).thenReturn(user);
    }

    private static Authentication currentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
