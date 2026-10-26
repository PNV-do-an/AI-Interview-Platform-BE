package com.aiinterview.platform.controller.auth;

import com.aiinterview.platform.common.exception.InvalidAccountException;
import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.request.GithubLoginRequest;
import com.aiinterview.platform.model.dto.request.GoogleLoginRequest;
import com.aiinterview.platform.model.dto.request.LoginRequest;
import com.aiinterview.platform.model.dto.request.RegisterRequest;
import com.aiinterview.platform.model.dto.request.ResetPasswordRequest;
import com.aiinterview.platform.model.dto.request.SsoLoginRequest;
import com.aiinterview.platform.model.dto.request.VerifyOtpRequest;
import com.aiinterview.platform.model.dto.response.AuthResponse;
import com.aiinterview.platform.model.dto.response.LoginResponse;
import com.aiinterview.platform.model.dto.response.PasswordResetResponse;
import com.aiinterview.platform.model.dto.response.RegisterResponse;
import com.aiinterview.platform.model.dto.response.ResetTokenInfoResponse;
import com.aiinterview.platform.model.dto.response.SsoConfigResponse;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.security.jwt.JwtUtil;
import com.aiinterview.platform.security.utils.JwtUtils;
import com.aiinterview.platform.service.GithubAuthInterface;
import com.aiinterview.platform.service.GoogleAuthInterface;
import com.aiinterview.platform.service.LoginInterface;
import com.aiinterview.platform.service.PasswordResetService;
import com.aiinterview.platform.service.RegisterInterface;
import com.aiinterview.platform.service.SsoAuthInterface;
import com.aiinterview.platform.service.TwoFactorService;
import com.aiinterview.platform.service.UserService;
import com.aiinterview.platform.service.UserSessionService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginInterface loginInterface;
    private final RegisterInterface registerInterface;
    private final PasswordResetService passwordResetService;
    private final UserService userService;
    private final GoogleAuthInterface googleAuthInterface;
    private final GithubAuthInterface githubAuthInterface;
    private final SsoAuthInterface ssoAuthInterface;
    private final TwoFactorService twoFactorService;
    private final UserSessionService userSessionService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request,
                                                               HttpServletRequest httpRequest) {
        RegisterResponse response = registerInterface.register(
                request.getEmail(), request.getPassword(),
                request.getFullName(),request.getPhone(),
                httpRequest
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response.getMessage(), null));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request,
                                                             HttpServletRequest httpRequest) {
        LoginResponse response = loginInterface.login(
                request.getEmail(), request.getPassword(), httpRequest
        );

        // Mật khẩu đúng nhưng tài khoản bật 2FA: chưa cấp JWT, FE nộp OTP lên /auth/verify-otp.
        if (response.isMfaRequired()) {
            AuthResponse challenge = AuthResponse.builder()
                    .tokenType("Bearer")
                    .mfaRequired(true)
                    .challengeToken(response.getChallengeToken())
                    .build();
            return ResponseEntity.ok(ApiResponse.success("Cần mã xác thực hai yếu tố", challenge));
        }

        // Load user info để trả về AuthResponse đầy đủ cho frontend
        var user = userService.findByEmail(request.getEmail());
        AuthResponse authResponse = AuthResponse.builder()
                .accessToken(response.getAccessToken())
                .refreshToken(response.getRefreshToken())
                .tokenType("Bearer")
                .user(userInfoOf(user))
                .build();
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công", authResponse));
    }

    /** POST /api/v1/auth/verify-otp — bước hai đăng nhập: challenge token + OTP/recovery code → JWT. */
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request,
                                                               HttpServletRequest httpRequest) {
        LoginResponse response = twoFactorService.verifyLoginOtp(
                request.getChallengeToken(), request.getCode(), httpRequest);
        var user = userService.findByEmail(responseEmail(response));
        AuthResponse authResponse = AuthResponse.builder()
                .accessToken(response.getAccessToken())
                .refreshToken(response.getRefreshToken())
                .tokenType("Bearer")
                .user(userInfoOf(user))
                .build();
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công", authResponse));
    }

    private static String responseEmail(LoginResponse response) {
        return JwtUtil.validateAccessToken(response.getAccessToken()).getSubject();
    }

    private static AuthResponse.UserInfo userInfoOf(User user) {
        return AuthResponse.UserInfo.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .role(user.getRole().name())
                .twoFactorEnabled(user.isTotpEnabled())
                .build();
    }

    /** GET /api/v1/auth/google/client-id — frontend hỏi OAuth client ID (rỗng = chưa bật) */
    @GetMapping("/google/client-id")
    public ResponseEntity<ApiResponse<String>> googleClientId() {
        return ResponseEntity.ok(ApiResponse.success("ok", googleAuthInterface.getClientId()));
    }

    /** POST /api/v1/auth/google — nhận idToken từ Google Identity Services, cấp JWT nội bộ */
    @PostMapping("/google")
    public ResponseEntity<ApiResponse<AuthResponse>> googleLogin(@Valid @RequestBody GoogleLoginRequest request,
                                                                  HttpServletRequest httpRequest) {
        User user = googleAuthInterface.loginWithGoogle(request.getIdToken(), httpRequest);

        String sessionId = userSessionService.start(user, httpRequest);
        String accessToken = JwtUtil.generateAccessToken(
                user.getEmail(), user.getRole().name(), user.getTokenVersion(), sessionId);
        String refreshToken = JwtUtil.generateRefreshToken(
                user.getEmail(), user.getRole().name(), user.getTokenVersion(), sessionId);

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .user(userInfoOf(user))
                .build();
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công", authResponse));
    }

    /** GET /api/v1/auth/github/client-id — frontend hỏi OAuth client ID (rỗng = chưa bật) */
    @GetMapping("/github/client-id")
    public ResponseEntity<ApiResponse<String>> githubClientId() {
        return ResponseEntity.ok(ApiResponse.success("ok", githubAuthInterface.getClientId()));
    }

    /** POST /api/v1/auth/github — nhận OAuth code từ callback, đổi lấy token GitHub, cấp JWT nội bộ */
    @PostMapping("/github")
    public ResponseEntity<ApiResponse<AuthResponse>> githubLogin(@Valid @RequestBody GithubLoginRequest request,
                                                                  HttpServletRequest httpRequest) {
        User user = githubAuthInterface.loginWithGithub(request.getCode(), request.getRedirectUri(), httpRequest);

        String sessionId = userSessionService.start(user, httpRequest);
        String accessToken = JwtUtil.generateAccessToken(
                user.getEmail(), user.getRole().name(), user.getTokenVersion(), sessionId);
        String refreshToken = JwtUtil.generateRefreshToken(
                user.getEmail(), user.getRole().name(), user.getTokenVersion(), sessionId);

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .user(userInfoOf(user))
                .build();
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công", authResponse));
    }

    /** GET /api/v1/auth/sso/config — frontend hỏi config OIDC (enabled=false khi chưa bật SSO) */
    @GetMapping("/sso/config")
    public ResponseEntity<ApiResponse<SsoConfigResponse>> ssoConfig() {
        return ResponseEntity.ok(ApiResponse.success("ok", ssoAuthInterface.getPublicConfig()));
    }

    /** POST /api/v1/auth/sso — nhận OAuth code từ IdP, verify id_token, cấp JWT nội bộ */
    @PostMapping("/sso")
    public ResponseEntity<ApiResponse<AuthResponse>> ssoLogin(@Valid @RequestBody SsoLoginRequest request,
                                                              HttpServletRequest httpRequest) {
        User user = ssoAuthInterface.loginWithSso(request.getCode(), request.getRedirectUri(), request.getNonce());

        String sessionId = userSessionService.start(user, httpRequest);
        String accessToken = JwtUtil.generateAccessToken(
                user.getEmail(), user.getRole().name(), user.getTokenVersion(), sessionId);
        String refreshToken = JwtUtil.generateRefreshToken(
                user.getEmail(), user.getRole().name(), user.getTokenVersion(), sessionId);

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .user(userInfoOf(user))
                .build();
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công", authResponse));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@RequestBody Map<String, String> request,
                                                             HttpServletRequest httpRequest) {
        String refreshToken = request.get("refreshToken");
        Claims claims = parseRefreshToken(refreshToken);
        String email = claims.getSubject();
        var user = userService.findByEmail(email);

        // Refresh token phát hành trước khi mật khẩu đổi mang tv cũ -> buộc đăng nhập lại (đăng xuất mọi phiên)
        Integer presentedVersion = claims.get("tv", Integer.class);
        int currentVersion = user.getTokenVersion();
        if (presentedVersion == null || presentedVersion != currentVersion) {
            throw new InvalidAccountException("Phiên đăng nhập không còn hiệu lực. Vui lòng đăng nhập lại.");
        }

        // Refresh giữ nguyên sid: một lần đăng nhập = một dòng user_session, "last seen" nối dài
        // tới khi refresh token hết hạn. Token đời trước (không có sid) được cấp phiên tại đây
        // để thiết bị đó vào danh sách thu hồi được.
        String sessionId = claims.get("sid", String.class);
        if (sessionId == null) {
            sessionId = userSessionService.start(user, httpRequest);
        } else if (!userSessionService.isLive(sessionId)) {
            // Phiên đã bị thu hồi (logout / admin khóa / đổi mật khẩu) phải chết cả đường refresh:
            // nếu không thiết bị đó xin được access token mới mang sid hỏng cho tới hết hạn 7 ngày,
            // và FE không bao giờ nhận được 401 để đưa người dùng về màn đăng nhập.
            throw new InvalidAccountException(
                    "Phiên đăng nhập này đã bị thu hồi. Vui lòng đăng nhập lại.");
        }

        LoginResponse lr = JwtUtils.refreshAccessToken(refreshToken, currentVersion, sessionId);
        AuthResponse authResponse = AuthResponse.builder()
                .accessToken(lr.getAccessToken())
                .refreshToken(lr.getRefreshToken())
                .tokenType("Bearer")
                .user(userInfoOf(user))
                .build();
        return ResponseEntity.ok(ApiResponse.success("Token refreshed", authResponse));
    }

    /**
     * Token thiếu, sai chữ ký hoặc quá hạn là "hết phiên" (401) — FE xoá storage rồi về /login.
     * Trước đây jjwt ném JwtException/NPE tràn ra ngoài thành 500 "Lỗi hệ thống".
     */
    private static Claims parseRefreshToken(String refreshToken) {
        try {
            return JwtUtil.validateRefreshToken(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidAccountException("Phiên đăng nhập không còn hiệu lực. Vui lòng đăng nhập lại.");
        }
    }

    @GetMapping("/verify")
    public ResponseEntity<ApiResponse<String>> verifyEmail(@RequestParam("token") String token) {
        String message = registerInterface.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.success(message));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(@RequestBody Map<String, String> request) {
        registerInterface.resendVerification(request.get("email"));
        return ResponseEntity.ok(ApiResponse.success("Email xac thuc da duoc gui lai."));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(@RequestBody Map<String, String> request,
                                                              HttpServletRequest httpRequest) {
        String message = passwordResetService.forgotPassword(request.get("email"), httpRequest);
        return ResponseEntity.ok(ApiResponse.success(message));
    }

    /** GET /api/v1/auth/reset-password?token= — kiểm tra link còn hiệu lực và trả danh tính tài khoản */
    @GetMapping("/reset-password")
    public ResponseEntity<ApiResponse<ResetTokenInfoResponse>> resetTokenInfo(@RequestParam("token") String token) {
        return ResponseEntity.ok(ApiResponse.success("Link đặt lại mật khẩu còn hiệu lực",
                passwordResetService.getResetTokenInfo(token)));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<PasswordResetResponse>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        boolean signOutEverywhere = !Boolean.FALSE.equals(request.getSignOutEverywhere());
        PasswordResetResponse response = passwordResetService.resetPassword(
                request.getToken(), request.getPassword(), request.getConfirmPassword(), signOutEverywhere
        );
        return ResponseEntity.ok(ApiResponse.success(
                "Mật khẩu đã được đặt lại thành công. Vui lòng đăng nhập lại.", response));
    }

    /** GET /api/v1/auth/me — trả thông tin user hiện tại dựa vào JWT */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthResponse.UserInfo>> getMe(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            throw new InvalidAccountException("Yêu cầu đăng nhập");
        }
        var user = userService.findByEmail(authentication.getName());
        AuthResponse.UserInfo userInfo = userInfoOf(user);
        return ResponseEntity.ok(ApiResponse.success(userInfo));
    }

    /**
     * POST /api/v1/auth/logout — thu hồi ngay mọi phiên của tài khoản đang đăng nhập.
     * JWT stateless nên không có blacklist: tokenVersion++ làm claim "tv" trong cả access
     * lẫn refresh token cũ lệch với users.token_version, JwtFilter chặn từ cửa, /refresh hết đường;
     * song song mọi dòng user_session bị đóng nên màn "Thiết bị đăng nhập" hết hiện thiết bị.
     * Thu hồi từng thiết bị: DELETE /api/user/sessions/{id}.
     */
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> logout(Authentication authentication) {
        userService.revokeSessions(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Đã đăng xuất mọi phiên", null));
    }
}
