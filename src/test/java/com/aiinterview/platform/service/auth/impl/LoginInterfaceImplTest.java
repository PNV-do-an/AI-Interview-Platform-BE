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
 *
 * <p>Mỗi test chia 3 khối comment: <b>Arrange</b> (dựng gì) → <b>Act</b> (gọi gì) → <b>Assert</b>
 * (khẳng định gì). Test một lời gọi thì gộp Act &amp; Assert.</p>
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
        // Arrange: IP da co dung 20 lan sai trong cua so 15 phut (nguong chan thiet bi)
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(IP + ", 10.0.0.1");
        when(loginAttemptRepository.countByIpAddressAndSuccessFalseAndTimestampAfter(
                eq(IP), any(LocalDateTime.class))).thenReturn(20L);

        // Act & Assert: chan o buoc dem theo IP, message huong dan cho nguoi dung thật
        assertThatThrownBy(() -> service.login(EMAIL, PASSWORD, httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Hệ thống phát hiện hoạt động bất thường từ thiết bị của bạn. Vui lòng thử lại sau.");

        // Chan theo IP thi khong duoc doc users hay so mat khau (tiet kiem + khong lo thong tin)
        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("Chi lay IP dau tien trong chuoi X-Forwarded-For")
    void login_usesFirstIpOfForwardedChain() {
        // Arrange: chuoi proxy 3 hop, IP that cua client la hop dau
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(IP + ", 10.0.0.1, 172.16.0.9");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        // Act: login that bai vi email khong ton tai (khong quan trong o case nay)
        assertThatThrownBy(() -> service.login(EMAIL, PASSWORD, httpRequest))
                .isInstanceOf(InvalidAccountException.class);

        // Assert: bo dem IP phai dung hop dau, neu lay hop cuoi thi moi request deu bi gom
        // vao IP cua proxy va rate-limit se chan nham toan bo he thong
        verify(loginAttemptRepository).countByIpAddressAndSuccessFalseAndTimestampAfter(
                eq(IP), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Email khong ton tai: ghi mot lan sai de con dem rate-limit")
    void login_unknownEmail_recordsFailedAttemptAndRejects() {
        // Arrange: email khong co trong DB
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        // Act & Assert: message chung cho email sai
        assertThatThrownBy(() -> service.login(EMAIL, PASSWORD, httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Email không tồn tại");

        // Assert: van phai ghi LoginAttempt that bai, neu khong ke tan cong do email se khong
        // bao gio bi dem va khong bao gio bi chan
        ArgumentCaptor<LoginAttempt> attempt = ArgumentCaptor.forClass(LoginAttempt.class);
        verify(loginAttemptRepository).save(attempt.capture());
        assertThat(attempt.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(attempt.getValue().isSuccess()).isFalse();
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Tai khoan da xoa mem bi coi la khong ton tai")
    void login_softDeletedAccount_isRejected() {
        // Arrange: user con trong DB nhung da xoa mem
        user.setDeletedAt(LocalDateTime.now().minusDays(1));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        // Act & Assert: message danh cho tai khoan da xoa, khong lo da tung ton tai
        assertThatThrownBy(() -> service.login(EMAIL, PASSWORD, httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Tài khoản không tồn tại hoặc đã bị xóa");
    }

    @Test
    @DisplayName("Tai khoan chua xac thuc email chua duoc dang nhap")
    void login_unverifiedAccount_isRejected() {
        // Arrange: enabled=false = chua bam link kich hoat trong mail
        user.setEnabled(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        // Act & Assert: message rieng de FE hien "kiem tra email"
        assertThatThrownBy(() -> service.login(EMAIL, PASSWORD, httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Tài khoản chưa được kích hoạt. Vui lòng kiểm tra email.");

        // Chua kich hoat thi khong duoc so mat khau
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("Khoa boi admin khac voi khoa do nhap sai: khong co moc lockUntil")
    void login_adminLockedAccount_isRejected() {
        // Arrange: user bi admin khoa (locked=true, khong co lockUntil)
        user.setLocked(true);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        // Act & Assert: dung loai exception rieng (InvalidAccountLockedException) de FE biet
        // day la khoa vinh vien, khong phai khoa tam 15 phut
        assertThatThrownBy(() -> service.login(EMAIL, PASSWORD, httpRequest))
                .isInstanceOf(InvalidAccountLockedException.class)
                .hasMessage("Tài khoản của bạn đã bị khóa bởi quản trị viên. Vui lòng liên hệ hỗ trợ.");
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("Con trong 15 phut khoa tam thi tra kem thoi diem mo khoa")
    void login_whileTemporaryLockStillActive_isRejectedWithDeadline() {
        // Arrange: dang trong thoi gian khoa tam, con 10 phut
        LocalDateTime unlockAt = LocalDateTime.now().plusMinutes(10);
        user.setLockUntil(unlockAt);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        // Act & Assert: FE can moc mo khoa de hien countdown, nen kiem tra ca getLockUntil()
        assertThatThrownBy(() -> service.login(EMAIL, PASSWORD, httpRequest))
                .isInstanceOf(InvalidAccountLockedException.class)
                .hasMessage("Tài khoản đã bị khóa do nhập sai quá nhiều lần. Vui lòng thử lại sau 15 phút.")
                .satisfies(thrown -> assertThat(((InvalidAccountLockedException) thrown).getLockUntil())
                        .isEqualTo(unlockAt));
    }

    @Test
    @DisplayName("Het 15 phut khoa thi dem lai tu dau, neu khong nguoi dung bi khoa lai ngay lan sai ke tiep")
    void login_afterLockExpired_resetsCounterBeforeCheckingPassword() {
        // Arrange: khoa tam da het han nhung bo dem van con 5 va mat khau lan nay dung
        user.setLockUntil(LocalDateTime.now().minusMinutes(1));
        user.setLoginAttempts(5);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, ENCODED)).thenReturn(true);
        when(userSessionService.start(user, httpRequest)).thenReturn(SESSION_ID);

        // Act
        service.login(EMAIL, PASSWORD, httpRequest);

        // Assert: bo dem va moc khoa phai duoc dat lai truoc khi so mat khau
        assertThat(user.getLoginAttempts()).isZero();
        assertThat(user.getLockUntil()).isNull();
        // luu lan dat lai bo dem, roi luu lai o nhanh thanh cong
        verify(userRepository, times(2)).save(user);
    }

    @Test
    @DisplayName("Sai mat khau: tang bo dem, ghi attempt that bai, khong khoa o lan thu 3")
    void login_wrongPassword_incrementsCounterAndRecordsFailure() {
        // Arrange: da sai 2 lan truoc do, lan nay sai tiep
        user.setLoginAttempts(2);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, ENCODED)).thenReturn(false);

        // Act & Assert: chi la sai mat khau, chua den nguong khoa
        assertThatThrownBy(() -> service.login(EMAIL, PASSWORD, httpRequest))
                .isInstanceOf(InvalidPasswordException.class)
                .hasMessage("Mật khẩu sai");

        // Assert: bo dem 2 -> 3, chua co moc khoa, ghi attempt that bai, khong luu user
        assertThat(user.getLoginAttempts()).isEqualTo(3);
        assertThat(user.getLockUntil()).isNull();
        verify(loginAttemptRepository).save(argThat(attempt -> !attempt.isSuccess()));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Sai lan thu 5: khoa 15 phut va tra loi kem moc khoa")
    void login_wrongPasswordAtFifthAttempt_locksAccount() {
        // Arrange: da sai 4 lan, lan nay la lan thu 5 (nguong khoa)
        user.setLoginAttempts(4);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, ENCODED)).thenReturn(false);

        // Act & Assert: message kem moc khoa dang chuoi (khac case khoa tam con hieu luc)
        assertThatThrownBy(() -> service.login(EMAIL, PASSWORD, httpRequest))
                .isInstanceOf(InvalidAccountLockedException.class)
                .hasMessageStartingWith("Tài khoản đã bị khóa do nhập sai quá nhiều lần. Khóa đến: ");

        // Assert: bo dem = 5, moc khoa o tuong lai, va user duoc luu de khoa co hieu luc
        assertThat(user.getLoginAttempts()).isEqualTo(5);
        assertThat(user.getLockUntil()).isAfter(LocalDateTime.now());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Da du 5 lan sai trong 15 phut thi khoa ca khi mat khau dung")
    void login_recentFailuresAtThreshold_locksBeforePasswordCheck() {
        // Arrange: bo dem tren user chua du 5 nhung lich su 15 phut da co 5 lan sai
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(loginAttemptRepository.findByEmailAndSuccessFalseAndTimestampAfter(
                eq(EMAIL), any(LocalDateTime.class)))
                .thenReturn(Collections.nCopies(5, new LoginAttempt(EMAIL, IP, false)));

        // Act & Assert: khoa truoc khi so mat khau
        assertThatThrownBy(() -> service.login(EMAIL, PASSWORD, httpRequest))
                .isInstanceOf(InvalidAccountLockedException.class);

        // Assert: khoa duoc ghi vao user, va mat khau dung cung khong duoc kiem tra
        // (neu kiem tra truoc thi ke tan cong do mat khau se biet mat khau nao dung)
        assertThat(user.getLockUntil()).isAfter(LocalDateTime.now());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("Bat 2FA: mat khau dung van chua cap JWT, chi tra challenge song 5 phut")
    void login_withTotpEnabled_returnsChallengeWithoutSession() {
        // Arrange: user da bat TOTP va lan nay nhap dung mat khau
        user.setTotpEnabled(true);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, ENCODED)).thenReturn(true);

        // Act
        LoginResponse response = service.login(EMAIL, PASSWORD, httpRequest);

        // Assert: khong cap access/refresh token, chi tra challenge mang purpose 2fa-challenge
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
        // Arrange: user binh thuong, mat khau dung, session moi duoc tao
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, ENCODED)).thenReturn(true);
        when(userSessionService.start(user, httpRequest)).thenReturn(SESSION_ID);

        // Act
        LoginResponse response = service.login(EMAIL, PASSWORD, httpRequest);

        // Assert: access token mang sub/role/tv/sid; refresh token mang cung sid de thu hoi theo phien
        assertThat(response.isMfaRequired()).isFalse();
        Claims access = JwtUtil.validateAccessToken(response.getAccessToken());
        assertThat(access.getSubject()).isEqualTo(EMAIL);
        assertThat(access.get("role", String.class)).isEqualTo(Role.ROLE_USER.name());
        assertThat(access.get("tv", Integer.class)).isZero();
        assertThat(access.get("sid", String.class)).isEqualTo(SESSION_ID);

        Claims refresh = JwtUtil.validateRefreshToken(response.getRefreshToken());
        assertThat(refresh.getSubject()).isEqualTo(EMAIL);
        assertThat(refresh.get("sid", String.class)).isEqualTo(SESSION_ID);

        // Assert: dang nhap thanh cong thi dat lai bo dem va ghi attempt thanh cong
        assertThat(user.getLoginAttempts()).isZero();
        assertThat(user.getLockUntil()).isNull();
        verify(loginAttemptRepository).save(argThat(LoginAttempt::isSuccess));
    }

    @Test
    @DisplayName("Token moi mang tokenVersion hien tai, nen logout cu lam token cu chet")
    void login_afterLogout_stampsCurrentTokenVersion() {
        // Arrange: user vua logout 3 lan (tokenVersion=3) nen token cu da bi vo hieu
        user.setTokenVersion(3);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, ENCODED)).thenReturn(true);
        when(userSessionService.start(user, httpRequest)).thenReturn(SESSION_ID);

        // Act
        LoginResponse response = service.login(EMAIL, PASSWORD, httpRequest);

        // Assert: token moi phai mang tv=3 (gia tri hien tai), khong phai 0
        assertThat(JwtUtil.validateAccessToken(response.getAccessToken()).get("tv", Integer.class))
                .isEqualTo(3);
    }
}
