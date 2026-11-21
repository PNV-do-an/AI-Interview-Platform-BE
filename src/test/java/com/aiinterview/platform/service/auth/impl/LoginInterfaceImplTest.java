package com.aiinterview.platform.service.auth.impl;

import com.aiinterview.platform.common.exception.InvalidAccountException;
import com.aiinterview.platform.common.exception.InvalidAccountLockedException;
import com.aiinterview.platform.common.exception.InvalidPasswordException;
import com.aiinterview.platform.model.dto.response.LoginResponse;
import com.aiinterview.platform.model.entity.LoginAttempt;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.LoginAttemptRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.security.jwt.JwtUtil;
import com.aiinterview.platform.service.auth.UserSessionService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * POST /api/v1/auth/login (Postman folder 00). Two counters guard the endpoint and they are
 * deliberately different: 20 failures per IP in 15 minutes blocks the whole device, 5 failures per
 * email locks the account. Everything else is ordering — a locked or unverified account must never
 * reach the password comparison, because that leaks whether the password was right.
 */
@ExtendWith(MockitoExtension.class)
class LoginInterfaceImplTest {

    private static final String EMAIL = "candidate@example.com";
    private static final String PASSWORD = "Str0ng!Pass123";
    private static final String ENCODED = "$2a$10$encoded";
    private static final String IP = "203.0.113.10";
    private static final String SESSION_ID = "sess-1";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private LoginAttemptRepository loginAttemptRepository;
    @Mock
    private UserSessionService userSessionService;
    @Mock
    private HttpServletRequest httpRequest;
    @InjectMocks
    private LoginInterfaceImpl service;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(20L)
                .email(EMAIL)
                .password(ENCODED)
                .fullName("Candidate User")
                .role(Role.ROLE_USER)
                .enabled(true)
                .build();
    }

    @Test
    @DisplayName("20 lan sai tu cung IP trong 15 phut thi chan ca thiet bi, chua cham vao users")
    void login_fromIpWithTooManyRecentFailures_isRejected() {
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(IP + ", 10.0.0.1");
        when(loginAttemptRepository.countByIpAddressAndSuccessFalseAndTimestampAfter(
                eq(IP), any(LocalDateTime.class))).thenReturn(20L);

        assertThatThrownBy(() -> service.login(EMAIL, PASSWORD, httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Hệ thống phát hiện hoạt động bất thường từ thiết bị của bạn. Vui lòng thử lại sau.");
        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("Chi lay IP dau tien trong chuoi X-Forwarded-For")
    void login_usesFirstIpOfForwardedChain() {
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(IP + ", 10.0.0.1, 172.16.0.9");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(EMAIL, PASSWORD, httpRequest))
                .isInstanceOf(InvalidAccountException.class);

        verify(loginAttemptRepository).countByIpAddressAndSuccessFalseAndTimestampAfter(
                eq(IP), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Email khong ton tai: ghi mot lan sai de con dem rate-limit")
    void login_unknownEmail_recordsFailedAttemptAndRejects() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(EMAIL, PASSWORD, httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Email không tồn tại");

        ArgumentCaptor<LoginAttempt> attempt = ArgumentCaptor.forClass(LoginAttempt.class);
        verify(loginAttemptRepository).save(attempt.capture());
        assertThat(attempt.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(attempt.getValue().isSuccess()).isFalse();
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Tai khoan da xoa mem bi coi la khong ton tai")
    void login_softDeletedAccount_isRejected() {
        user.setDeletedAt(LocalDateTime.now().minusDays(1));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(EMAIL, PASSWORD, httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Tài khoản không tồn tại hoặc đã bị xóa");
    }

    @Test
    @DisplayName("Tai khoan chua xac thuc email chua duoc dang nhap")
    void login_unverifiedAccount_isRejected() {
        user.setEnabled(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(EMAIL, PASSWORD, httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Tài khoản chưa được kích hoạt. Vui lòng kiểm tra email.");
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("Khoa boi admin khac voi khoa do nhap sai: khong co moc lockUntil")
    void login_adminLockedAccount_isRejected() {
        user.setLocked(true);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(EMAIL, PASSWORD, httpRequest))
                .isInstanceOf(InvalidAccountLockedException.class)
                .hasMessage("Tài khoản của bạn đã bị khóa bởi quản trị viên. Vui lòng liên hệ hỗ trợ.");
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("Con trong 15 phut khoa tam thi tra kem thoi diem mo khoa")
    void login_whileTemporaryLockStillActive_isRejectedWithDeadline() {
        LocalDateTime unlockAt = LocalDateTime.now().plusMinutes(10);
        user.setLockUntil(unlockAt);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(EMAIL, PASSWORD, httpRequest))
                .isInstanceOf(InvalidAccountLockedException.class)
                .hasMessage("Tài khoản đã bị khóa do nhập sai quá nhiều lần. Vui lòng thử lại sau 15 phút.")
                .satisfies(thrown -> assertThat(((InvalidAccountLockedException) thrown).getLockUntil())
                        .isEqualTo(unlockAt));
    }

    @Test
    @DisplayName("Het 15 phut khoa thi dem lai tu dau, neu khong nguoi dung bi khoa lai ngay lan sai ke tiep")
    void login_afterLockExpired_resetsCounterBeforeCheckingPassword() {
        user.setLockUntil(LocalDateTime.now().minusMinutes(1));
        user.setLoginAttempts(5);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, ENCODED)).thenReturn(true);
        when(userSessionService.start(user, httpRequest)).thenReturn(SESSION_ID);

        service.login(EMAIL, PASSWORD, httpRequest);

        assertThat(user.getLoginAttempts()).isZero();
        assertThat(user.getLockUntil()).isNull();
        // luu lan dat lai bo dem, roi luu lai o nhanh thanh cong
        verify(userRepository, times(2)).save(user);
    }

    @Test
    @DisplayName("Sai mat khau: tang bo dem, ghi attempt that bai, khong khoa o lan thu 3")
    void login_wrongPassword_incrementsCounterAndRecordsFailure() {
        user.setLoginAttempts(2);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, ENCODED)).thenReturn(false);

        assertThatThrownBy(() -> service.login(EMAIL, PASSWORD, httpRequest))
                .isInstanceOf(InvalidPasswordException.class)
                .hasMessage("Mật khẩu sai");

        assertThat(user.getLoginAttempts()).isEqualTo(3);
        assertThat(user.getLockUntil()).isNull();
        verify(loginAttemptRepository).save(argThat(attempt -> !attempt.isSuccess()));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Sai lan thu 5: khoa 15 phut va tra loi kem moc khoa")
    void login_wrongPasswordAtFifthAttempt_locksAccount() {
        user.setLoginAttempts(4);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, ENCODED)).thenReturn(false);

        assertThatThrownBy(() -> service.login(EMAIL, PASSWORD, httpRequest))
                .isInstanceOf(InvalidAccountLockedException.class)
                .hasMessageStartingWith("Tài khoản đã bị khóa do nhập sai quá nhiều lần. Khóa đến: ");

        assertThat(user.getLoginAttempts()).isEqualTo(5);
        assertThat(user.getLockUntil()).isAfter(LocalDateTime.now());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Da du 5 lan sai trong 15 phut thi khoa ca khi mat khau dung")
    void login_recentFailuresAtThreshold_locksBeforePasswordCheck() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(loginAttemptRepository.findByEmailAndSuccessFalseAndTimestampAfter(
                eq(EMAIL), any(LocalDateTime.class)))
                .thenReturn(Collections.nCopies(5, new LoginAttempt(EMAIL, IP, false)));

        assertThatThrownBy(() -> service.login(EMAIL, PASSWORD, httpRequest))
                .isInstanceOf(InvalidAccountLockedException.class);

        assertThat(user.getLockUntil()).isAfter(LocalDateTime.now());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("Bat 2FA: mat khau dung van chua cap JWT, chi tra challenge song 5 phut")
    void login_withTotpEnabled_returnsChallengeWithoutSession() {
        user.setTotpEnabled(true);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, ENCODED)).thenReturn(true);

        LoginResponse response = service.login(EMAIL, PASSWORD, httpRequest);

        assertThat(response.isMfaRequired()).isTrue();
        assertThat(response.getAccessToken()).isNull();
        assertThat(response.getRefreshToken()).isNull();
        Claims challenge = JwtUtil.validate2faChallengeToken(response.getChallengeToken());
        assertThat(challenge.getSubject()).isEqualTo(EMAIL);
        assertThat(challenge.get("purpose", String.class)).isEqualTo(JwtUtil.PURPOSE_2FA_CHALLENGE);

        // chua qua OTP thi khong tinh la mot lan dang nhap thanh cong
        verify(userSessionService, never()).start(any(User.class), any());
        verify(loginAttemptRepository, never()).save(any(LoginAttempt.class));
    }

    @Test
    @DisplayName("Dang nhap thanh cong: token mang tv cua user va sid cua phien vua tao")
    void login_success_issuesTokensCarryingVersionAndSession() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, ENCODED)).thenReturn(true);
        when(userSessionService.start(user, httpRequest)).thenReturn(SESSION_ID);

        LoginResponse response = service.login(EMAIL, PASSWORD, httpRequest);

        assertThat(response.isMfaRequired()).isFalse();
        Claims access = JwtUtil.validateAccessToken(response.getAccessToken());
        assertThat(access.getSubject()).isEqualTo(EMAIL);
        assertThat(access.get("role", String.class)).isEqualTo(Role.ROLE_USER.name());
        assertThat(access.get("tv", Integer.class)).isZero();
        assertThat(access.get("sid", String.class)).isEqualTo(SESSION_ID);

        Claims refresh = JwtUtil.validateRefreshToken(response.getRefreshToken());
        assertThat(refresh.getSubject()).isEqualTo(EMAIL);
        assertThat(refresh.get("sid", String.class)).isEqualTo(SESSION_ID);

        assertThat(user.getLoginAttempts()).isZero();
        assertThat(user.getLockUntil()).isNull();
        verify(loginAttemptRepository).save(argThat(LoginAttempt::isSuccess));
    }

    @Test
    @DisplayName("Token moi mang tokenVersion hien tai, nen logout cu lam token cu chet")
    void login_afterLogout_stampsCurrentTokenVersion() {
        user.setTokenVersion(3);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, ENCODED)).thenReturn(true);
        when(userSessionService.start(user, httpRequest)).thenReturn(SESSION_ID);

        LoginResponse response = service.login(EMAIL, PASSWORD, httpRequest);

        assertThat(JwtUtil.validateAccessToken(response.getAccessToken()).get("tv", Integer.class))
                .isEqualTo(3);
    }
}
