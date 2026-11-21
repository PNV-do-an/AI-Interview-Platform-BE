package com.aiinterview.platform.controller.auth;

import com.aiinterview.platform.common.exception.InvalidAccountException;
import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.request.RegisterRequest;
import com.aiinterview.platform.model.dto.response.AuthResponse;
import com.aiinterview.platform.model.dto.response.RegisterResponse;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.security.jwt.JwtUtil;
import com.aiinterview.platform.service.auth.GithubAuthInterface;
import com.aiinterview.platform.service.auth.GoogleAuthInterface;
import com.aiinterview.platform.service.auth.LoginInterface;
import com.aiinterview.platform.service.auth.PasswordResetService;
import com.aiinterview.platform.service.auth.RegisterInterface;
import com.aiinterview.platform.service.auth.SsoAuthInterface;
import com.aiinterview.platform.service.auth.TwoFactorService;
import com.aiinterview.platform.service.auth.UserService;
import com.aiinterview.platform.service.auth.UserSessionService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Folder 00 endpoints whose logic lives in {@link AuthController} rather than in a service:
 * POST /refresh, GET /me, POST /logout and the 201 contract of POST /register.
 * <p>
 * The refresh rules are the security-critical part. A refresh token is never rotated here — the
 * session id ("sid") is the anchor and two checks run on every call: the tokenVersion ("tv") claim
 * must still match the user row (password change / logout bumps it) and the session must still be
 * live (admin lock / per-device revoke closes it). Both failures must surface as
 * {@link InvalidAccountException} so the frontend gets 401 and clears storage, never 500.
 * <p>
 * The JWT helpers are static and use the built-in fallback secrets, so real signed tokens are
 * minted and parsed in-process: no Spring, no clock manipulation, no Docker.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerSessionTest {

    private static final String EMAIL = "candidate@example.com";
    private static final String ROLE = "ROLE_USER";
    private static final String SESSION_ID = "sess-live";
    private static final String NEW_SESSION_ID = "sess-new";

    @Mock
    private LoginInterface loginInterface;
    @Mock
    private RegisterInterface registerInterface;
    @Mock
    private PasswordResetService passwordResetService;
    @Mock
    private UserService userService;
    @Mock
    private GoogleAuthInterface googleAuthInterface;
    @Mock
    private GithubAuthInterface githubAuthInterface;
    @Mock
    private SsoAuthInterface ssoAuthInterface;
    @Mock
    private TwoFactorService twoFactorService;
    @Mock
    private UserSessionService userSessionService;
    @Mock
    private HttpServletRequest httpRequest;
    @InjectMocks
    private AuthController controller;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(20L)
                .email(EMAIL)
                .fullName("Candidate User")
                .phone("0901234567")
                .password("encoded")
                .role(Role.ROLE_USER)
                .enabled(true)
                .tokenVersion(2)
                .build();
    }

    @Test
    @DisplayName("Refresh thieu token: 401 'het phien', khong doc database")
    void refresh_withoutToken_isRejectedAsExpiredSession() {
        assertThatThrownBy(() -> controller.refresh(Map.of(), httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Phiên đăng nhập không còn hiệu lực. Vui lòng đăng nhập lại.");
        verifyNoInteractions(userService, userSessionService);
    }

    @Test
    @DisplayName("Refresh voi chuoi rac: 401 chu khong phai 500")
    void refresh_withMalformedToken_isRejectedAsExpiredSession() {
        assertThatThrownBy(() -> controller.refresh(Map.of("refreshToken", "not-a-jwt"), httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Phiên đăng nhập không còn hiệu lực. Vui lòng đăng nhập lại.");
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("Gui access token vao o refresh token: sai khoa ky nen bi tu choi")
    void refresh_withAccessToken_isRejectedAsExpiredSession() {
        String accessToken = JwtUtil.generateAccessToken(EMAIL, ROLE, 2, SESSION_ID);

        assertThatThrownBy(() -> controller.refresh(Map.of("refreshToken", accessToken), httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Phiên đăng nhập không còn hiệu lực. Vui lòng đăng nhập lại.");
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("Refresh token phat hanh truoc khi doi mat khau mang tv cu: bat dang nhap lai")
    void refresh_withStaleTokenVersion_isRejected() {
        String stale = JwtUtil.generateRefreshToken(EMAIL, ROLE, 1, SESSION_ID);
        when(userService.findByEmail(EMAIL)).thenReturn(user);

        assertThatThrownBy(() -> controller.refresh(Map.of("refreshToken", stale), httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Phiên đăng nhập không còn hiệu lực. Vui lòng đăng nhập lại.");
        verifyNoInteractions(userSessionService);
    }

    @Test
    @DisplayName("Phien da bi thu hoi (logout/admin khoa) phai chet ca duong refresh")
    void refresh_whenSessionRevoked_isRejected() {
        String token = JwtUtil.generateRefreshToken(EMAIL, ROLE, 2, SESSION_ID);
        when(userService.findByEmail(EMAIL)).thenReturn(user);
        when(userSessionService.isLive(SESSION_ID)).thenReturn(false);

        assertThatThrownBy(() -> controller.refresh(Map.of("refreshToken", token), httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Phiên đăng nhập này đã bị thu hồi. Vui lòng đăng nhập lại.");
    }

    @Test
    @DisplayName("Token doi cu khong co sid: cap phien moi de thiet bi do thu hoi duoc")
    void refresh_legacyTokenWithoutSid_mintsNewSession() {
        String legacy = JwtUtil.generateRefreshToken(EMAIL, ROLE, 2, null);
        when(userService.findByEmail(EMAIL)).thenReturn(user);
        when(userSessionService.start(user, httpRequest)).thenReturn(NEW_SESSION_ID);

        AuthResponse response = controller.refresh(Map.of("refreshToken", legacy), httpRequest).getBody()
                .getData();

        Claims access = JwtUtil.validateAccessToken(response.getAccessToken());
        assertThat(access.get("sid", String.class)).isEqualTo(NEW_SESSION_ID);
        assertThat(access.get("tv", Integer.class)).isEqualTo(2);
        verify(userSessionService).start(user, httpRequest);
    }

    @Test
    @DisplayName("Phien con song: giu nguyen sid, khong tao phien moi, giu nguyen refresh token")
    void refresh_liveSession_keepsSessionAndRefreshToken() {
        String token = JwtUtil.generateRefreshToken(EMAIL, ROLE, 2, SESSION_ID);
        when(userService.findByEmail(EMAIL)).thenReturn(user);
        when(userSessionService.isLive(SESSION_ID)).thenReturn(true);

        ResponseEntity<ApiResponse<AuthResponse>> entity =
                controller.refresh(Map.of("refreshToken", token), httpRequest);

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entity.getBody().getMessage()).isEqualTo("Token refreshed");

        AuthResponse body = entity.getBody().getData();
        assertThat(JwtUtil.validateAccessToken(body.getAccessToken()).get("sid", String.class))
                .isEqualTo(SESSION_ID);
        // khong xoay refresh token: sid la neo, tv la cong tat thu hoi
        assertThat(body.getRefreshToken()).isEqualTo(token);
        assertThat(body.getUser().getEmail()).isEqualTo(EMAIL);
        verify(userSessionService, never()).start(any(User.class), any());
    }

    @Test
    @DisplayName("GET /auth/me khong co authentication tra 401")
    void me_withoutAuthentication_isRejected() {
        assertThatThrownBy(() -> controller.getMe(null))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Yêu cầu đăng nhập");
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("GET /auth/me voi anonymous token cung tra 401")
    void me_withAnonymousAuthentication_isRejected() {
        Authentication anonymous = new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        assertThatThrownBy(() -> controller.getMe(anonymous))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Yêu cầu đăng nhập");
    }

    @Test
    @DisplayName("GET /auth/me tra dung ho so va co 2FA cua nguoi dang dang nhap")
    void me_whenAuthenticated_returnsCurrentUserInfo() {
        user.setTotpEnabled(true);
        when(userService.findByEmail(EMAIL)).thenReturn(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(EMAIL, null);

        AuthResponse.UserInfo info = controller.getMe(authentication).getBody().getData();

        assertThat(info.getId()).isEqualTo(20L);
        assertThat(info.getEmail()).isEqualTo(EMAIL);
        assertThat(info.getFullName()).isEqualTo("Candidate User");
        assertThat(info.getPhone()).isEqualTo("0901234567");
        assertThat(info.getRole()).isEqualTo(ROLE);
        assertThat(info.isTwoFactorEnabled()).isTrue();
    }

    @Test
    @DisplayName("POST /auth/logout thu hoi toan bo phien cua tai khoan dang dang nhap")
    void logout_revokesEverySessionOfCurrentUser() {
        Authentication authentication = new UsernamePasswordAuthenticationToken(EMAIL, null);

        ResponseEntity<ApiResponse<Void>> entity = controller.logout(authentication);

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entity.getBody().isSuccess()).isTrue();
        verify(userService).revokeSessions(EMAIL);
    }

    @Test
    @DisplayName("POST /auth/register tra 201 va chi tra message, khong tra token")
    void register_returns201WithServiceMessage() {
        when(registerInterface.register(eq(EMAIL), eq("Str0ng!Pass123"), eq("Candidate User"),
                eq("0901234567"), eq(httpRequest)))
                .thenReturn(new RegisterResponse("Dang ky thanh cong, vui long kiem tra email de xac thuc"));

        ResponseEntity<ApiResponse<AuthResponse>> entity = controller.register(
                registerRequest(), httpRequest);

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(entity.getBody().isSuccess()).isTrue();
        assertThat(entity.getBody().getMessage())
                .isEqualTo("Dang ky thanh cong, vui long kiem tra email de xac thuc");
        assertThat(entity.getBody().getData()).isNull();
    }

    @Test
    @DisplayName("GET /auth/google/client-id rong nghia la chua bat Google, FE an nut")
    void googleClientId_returnsEmptyStringWhenDisabled() {
        when(googleAuthInterface.getClientId()).thenReturn("");

        assertThat(controller.googleClientId().getBody().getData()).isEmpty();
    }

    @Test
    @DisplayName("GET /auth/github/client-id chuyen tiep dung client id cua service")
    void githubClientId_returnsConfiguredValue() {
        when(githubAuthInterface.getClientId()).thenReturn("gh-client");

        assertThat(controller.githubClientId().getBody().getData()).isEqualTo("gh-client");
    }

    private RegisterRequest registerRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(EMAIL);
        request.setPassword("Str0ng!Pass123");
        request.setFullName("Candidate User");
        request.setPhone("0901234567");
        return request;
    }
}
