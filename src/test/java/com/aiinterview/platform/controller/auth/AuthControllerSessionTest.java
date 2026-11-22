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
 * <p>
 * Mỗi test chia 3 khối comment: <b>Arrange</b> → <b>Act</b> → <b>Assert</b>. Test một lời gọi thì
 * gộp Act &amp; Assert.
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
        // Arrange: body rong (FE quen gui refreshToken)

        // Act & Assert: 401 voi message "het phien" chu khong phai loi 500
        assertThatThrownBy(() -> controller.refresh(Map.of(), httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Phiên đăng nhập không còn hiệu lực. Vui lòng đăng nhập lại.");

        // Khong can doc DB khi input da thieu
        verifyNoInteractions(userService, userSessionService);
    }

    @Test
    @DisplayName("Refresh voi chuoi rac: 401 chu khong phai 500")
    void refresh_withMalformedToken_isRejectedAsExpiredSession() {
        // Arrange: chuoi khong phai JWT

        // Act & Assert: JwtException duoc doi thanh InvalidAccountException (401), khong lo 500
        assertThatThrownBy(() -> controller.refresh(Map.of("refreshToken", "not-a-jwt"), httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Phiên đăng nhập không còn hiệu lực. Vui lòng đăng nhập lại.");

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("Gui access token vao o refresh token: sai khoa ky nen bi tu choi")
    void refresh_withAccessToken_isRejectedAsExpiredSession() {
        // Arrange: mint access token that (ky bang access secret)
        String accessToken = JwtUtil.generateAccessToken(EMAIL, ROLE, 2, SESSION_ID);

        // Act & Assert: refresh validator ky bang refresh secret nen token nay khong qua duoc
        assertThatThrownBy(() -> controller.refresh(Map.of("refreshToken", accessToken), httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Phiên đăng nhập không còn hiệu lực. Vui lòng đăng nhập lại.");

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("Refresh token phat hanh truoc khi doi mat khau mang tv cu: bat dang nhap lai")
    void refresh_withStaleTokenVersion_isRejected() {
        // Arrange: token mang tv=1, user hien tai da len tv=2 (vua doi mat khau)
        String stale = JwtUtil.generateRefreshToken(EMAIL, ROLE, 1, SESSION_ID);
        when(userService.findByEmail(EMAIL)).thenReturn(user);

        // Act & Assert: tv lech = token cu, phai dang xuat lai
        assertThatThrownBy(() -> controller.refresh(Map.of("refreshToken", stale), httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Phiên đăng nhập không còn hiệu lực. Vui lòng đăng nhập lại.");

        // Chan o buoc tv thi chua can hoi session
        verifyNoInteractions(userSessionService);
    }

    @Test
    @DisplayName("Phien da bi thu hoi (logout/admin khoa) phai chet ca duong refresh")
    void refresh_whenSessionRevoked_isRejected() {
        // Arrange: tv dung, nhung session sid da bi thu hoi
        String token = JwtUtil.generateRefreshToken(EMAIL, ROLE, 2, SESSION_ID);
        when(userService.findByEmail(EMAIL)).thenReturn(user);
        when(userSessionService.isLive(SESSION_ID)).thenReturn(false);

        // Act & Assert: message rieng cho "phien bi thu hoi" de FE phan biet voi token het han
        assertThatThrownBy(() -> controller.refresh(Map.of("refreshToken", token), httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Phiên đăng nhập này đã bị thu hồi. Vui lòng đăng nhập lại.");
    }

    @Test
    @DisplayName("Token doi cu khong co sid: cap phien moi de thiet bi do thu hoi duoc")
    void refresh_legacyTokenWithoutSid_mintsNewSession() {
        // Arrange: refresh token phat truoc wave 010 (khong co sid), user con tv=2
        String legacy = JwtUtil.generateRefreshToken(EMAIL, ROLE, 2, null);
        when(userService.findByEmail(EMAIL)).thenReturn(user);
        when(userSessionService.start(user, httpRequest)).thenReturn(NEW_SESSION_ID);

        // Act
        AuthResponse response = controller.refresh(Map.of("refreshToken", legacy), httpRequest).getBody()
                .getData();

        // Assert: cap sid moi (de sau nay thu hoi duoc theo thiet bi) va giu dung tv=2
        Claims access = JwtUtil.validateAccessToken(response.getAccessToken());
        assertThat(access.get("sid", String.class)).isEqualTo(NEW_SESSION_ID);
        assertThat(access.get("tv", Integer.class)).isEqualTo(2);
        verify(userSessionService).start(user, httpRequest);
    }

    @Test
    @DisplayName("Phien con song: giu nguyen sid, khong tao phien moi, giu nguyen refresh token")
    void refresh_liveSession_keepsSessionAndRefreshToken() {
        // Arrange: tv dung va session sid con song
        String token = JwtUtil.generateRefreshToken(EMAIL, ROLE, 2, SESSION_ID);
        when(userService.findByEmail(EMAIL)).thenReturn(user);
        when(userSessionService.isLive(SESSION_ID)).thenReturn(true);

        // Act
        ResponseEntity<ApiResponse<AuthResponse>> entity =
                controller.refresh(Map.of("refreshToken", token), httpRequest);

        // Assert: 200, message hop dong voi FE, access token mang lai dung sid cu
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
        // Arrange: request khong qua filter (authentication = null)

        // Act & Assert: 401 voi message "Yeu cau dang nhap"
        assertThatThrownBy(() -> controller.getMe(null))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Yêu cầu đăng nhập");

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("GET /auth/me voi anonymous token cung tra 401")
    void me_withAnonymousAuthentication_isRejected() {
        // Arrange: Spring Security gan AnonymousAuthenticationToken cho request khong dang nhap
        Authentication anonymous = new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        // Act & Assert: anonymous cung bi coi la chua dang nhap (khong duoc di tiep tim user)
        assertThatThrownBy(() -> controller.getMe(anonymous))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Yêu cầu đăng nhập");
    }

    @Test
    @DisplayName("GET /auth/me tra dung ho so va co 2FA cua nguoi dang dang nhap")
    void me_whenAuthenticated_returnsCurrentUserInfo() {
        // Arrange: da dang nhap, user bat TOTP
        user.setTotpEnabled(true);
        when(userService.findByEmail(EMAIL)).thenReturn(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(EMAIL, null);

        // Act
        AuthResponse.UserInfo info = controller.getMe(authentication).getBody().getData();

        // Assert: du 6 truong FE can, khong lo password/tokenVersion ra DTO
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
        // Arrange: da dang nhap
        Authentication authentication = new UsernamePasswordAuthenticationToken(EMAIL, null);

        // Act
        ResponseEntity<ApiResponse<Void>> entity = controller.logout(authentication);

        // Assert: 200 va thu hoi theo email (toan bo thiet bi), khong phai chi phien hien tai
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entity.getBody().isSuccess()).isTrue();
        verify(userService).revokeSessions(EMAIL);
    }

    @Test
    @DisplayName("POST /auth/register tra 201 va chi tra message, khong tra token")
    void register_returns201WithServiceMessage() {
        // Arrange: service dang ky thanh cong va tra message huong dan kiem tra email
        when(registerInterface.register(eq(EMAIL), eq("Str0ng!Pass123"), eq("Candidate User"),
                eq("0901234567"), eq(httpRequest)))
                .thenReturn(new RegisterResponse("Dang ky thanh cong, vui long kiem tra email de xac thuc"));

        // Act
        ResponseEntity<ApiResponse<AuthResponse>> entity = controller.register(
                registerRequest(), httpRequest);

        // Assert: 201, message lay tu service, data = null (chua co token truoc khi xac thuc email)
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(entity.getBody().isSuccess()).isTrue();
        assertThat(entity.getBody().getMessage())
                .isEqualTo("Dang ky thanh cong, vui long kiem tra email de xac thuc");
        assertThat(entity.getBody().getData()).isNull();
    }

    @Test
    @DisplayName("GET /auth/google/client-id rong nghia la chua bat Google, FE an nut")
    void googleClientId_returnsEmptyStringWhenDisabled() {
        // Arrange: service bao chua cau hinh
        when(googleAuthInterface.getClientId()).thenReturn("");

        // Act & Assert: chuoi rong duoc chuyen tiep nguyen van, FE dua vao do de an nut
        assertThat(controller.googleClientId().getBody().getData()).isEmpty();
    }

    @Test
    @DisplayName("GET /auth/github/client-id chuyen tiep dung client id cua service")
    void githubClientId_returnsConfiguredValue() {
        // Arrange: service da cau hinh client id
        when(githubAuthInterface.getClientId()).thenReturn("gh-client");

        // Act & Assert: khong bien doi gia tri tren duong truyen ra FE
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
