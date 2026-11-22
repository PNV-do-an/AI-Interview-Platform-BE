package com.aiinterview.platform.service.auth.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.exception.InvalidAccountException;
import com.aiinterview.platform.common.exception.InvalidAccountLockedException;
import com.aiinterview.platform.common.exception.InvalidPasswordException;
import com.aiinterview.platform.model.dto.response.TwoFactorEnableResponse;
import com.aiinterview.platform.model.dto.response.TwoFactorSetupResponse;
import com.aiinterview.platform.model.entity.LoginAttempt;
import com.aiinterview.platform.model.entity.RecoveryCode;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.LoginAttemptRepository;
import com.aiinterview.platform.model.repository.RecoveryCodeRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.security.jwt.JwtUtil;
import com.aiinterview.platform.service.auth.TotpVerifier;
import com.aiinterview.platform.service.auth.TotpVerifier;
import com.aiinterview.platform.service.auth.impl.TwoFactorServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * /api/v1/2fa/* va buoc hai cua dang nhap (/auth/verify-otp, folder 00). Trong tam: 2FA chi duoc bat
 * khi da chung minh bang mot ma OTP hop le, ma du phong chi dung mot lan va chi luu duoi dang hash.
 *
 * <p>Mỗi test chia 3 khối comment: <b>Arrange</b> → <b>Act</b> → <b>Assert</b>.</p>
 */
@ExtendWith(MockitoExtension.class)
class TwoFactorServiceImplTest {

    private static final String EMAIL = "owner@example.com";
    private static final String SECRET = "JBSWY3DPEHPK3PXP";

    @Mock
    private UserRepository userRepository;
    @Mock
    private RecoveryCodeRepository recoveryCodeRepository;
    @Mock
    private LoginAttemptRepository loginAttemptRepository;
    @Mock
    private TotpVerifier totpVerifier;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private com.aiinterview.platform.service.auth.UserSessionService userSessionService;

    @InjectMocks
    private TwoFactorServiceImpl service;

    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "issuer", "AI Interview Platform");
        user = User.builder()
                .id(3L).email(EMAIL).password("hash").fullName("Owner")
                .role(Role.ROLE_USER).enabled(true)
                .build();
        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    @Test
    void setupStoresPendingSecretAndReturnsOtpauthUri() {
        // Arrange: nha cung cap TOTP sinh secret moi
        when(totpVerifier.newSecret()).thenReturn(SECRET);

        // Act
        TwoFactorSetupResponse response = service.setup(EMAIL);

        // Assert: tra secret + URI de FE ve QR code
        assertEquals(SECRET, response.getSecret());
        assertTrue(response.getOtpauthUri().startsWith("otpauth://totp/"));
        assertTrue(response.getOtpauthUri().contains("secret=" + SECRET));

        // Assert: setup CHI luu secret tam, chua bat 2FA (phai cho OTP chung minh)
        assertFalse(user.isTotpEnabled(), "setup only stores the secret; enable needs a verified OTP");
        assertEquals(SECRET, user.getTotpSecret());
    }

    @Test
    void setupRejectedWhen2faAlreadyOn() {
        // Arrange: user da bat 2FA
        user.setTotpEnabled(true);

        // Act & Assert: khong cho setup lai (se ghi de secret dang dung)
        assertThrows(BadRequestException.class, () -> service.setup(EMAIL));
    }

    @Test
    void enableRequiresPriorSetup() {
        // Arrange: user chua goi setup nen totpSecret = null

        // Act & Assert: bat 2FA ma khong co secret la vo nghia
        assertThrows(BadRequestException.class, () -> service.enable(EMAIL, "123456"));
    }

    @Test
    void enableWithWrongOtpKeeps2faOff() {
        // Arrange: da co secret nhung nguoi dung nhap sai ma
        user.setTotpSecret(SECRET);
        when(totpVerifier.verify(SECRET, "000000")).thenReturn(false);

        // Act & Assert: sai ma thi 2FA van tat
        assertThrows(InvalidPasswordException.class, () -> service.enable(EMAIL, "000000"));
        assertFalse(user.isTotpEnabled());

        // Va khong duoc phep phat ma du phong khi chua xac thuc duoc thiet bi
        verify(recoveryCodeRepository, never()).saveAll(any());
    }

    @Test
    void enableIssuesEightOneTimeRecoveryCodes() {
        // Arrange: OTP dung, encoder duoc stub de kiem tra "chi luu hash"
        user.setTotpSecret(SECRET);
        when(totpVerifier.verify(SECRET, "654321")).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenReturn("rc-hash");

        // Act
        TwoFactorEnableResponse response = service.enable(EMAIL, "654321");

        // Assert: 2FA bat, tra dung 8 ma, moi ma theo dinh dang XXXXX-XXXXX
        assertTrue(user.isTotpEnabled());
        assertEquals(8, response.getRecoveryCodes().size());
        response.getRecoveryCodes().forEach(code ->
                assertTrue(code.matches("[A-Z0-9]{5}-[A-Z0-9]{5}"), code));

        // Assert: DB nhan 8 dong va tat ca deu la hash, khong co ma ro
        ArgumentCaptor<List<RecoveryCode>> saved = ArgumentCaptor.forClass(List.class);
        verify(recoveryCodeRepository).saveAll(saved.capture());
        assertEquals(8, saved.getValue().size());
        // DB chỉ nhận hash, không bao giờ nhận mã rõ.
        saved.getValue().forEach(row -> assertEquals("rc-hash", row.getCodeHash()));
    }

    @Test
    void disableWithValidOtpClearsSecretAndBurnsRecoveryCodes() {
        // Arrange: 2FA dang bat, nguoi dung chung minh bang OTP dung
        user.setTotpEnabled(true);
        user.setTotpSecret(SECRET);
        when(totpVerifier.verify(SECRET, "111222")).thenReturn(true);

        // Act
        service.disable(EMAIL, "111222");

        // Assert: tat 2FA, xoa secret, va xoa toan bo ma du phong (khong con duong vao lai)
        assertFalse(user.isTotpEnabled());
        assertNull(user.getTotpSecret());
        verify(recoveryCodeRepository).deleteByUserId(3L);
    }

    @Test
    void disableWithoutSecondFactorProofIsRejected() {
        // Arrange: 2FA dang bat nhung ma nhap sai
        user.setTotpEnabled(true);
        user.setTotpSecret(SECRET);
        when(totpVerifier.verify(anyString(), anyString())).thenReturn(false);

        // Act & Assert: khong the tat 2FA chi bang mat khau / phien dang nhap
        assertThrows(InvalidPasswordException.class, () -> service.disable(EMAIL, "999999"));
        assertTrue(user.isTotpEnabled());
    }

    @Test
    void recoveryCodeIsSingleUseAndCaseDashInsensitive() {
        // Arrange: co 1 ma du phong chua dung; nguoi dung nhap chu thuong va co dau gach
        user.setTotpEnabled(true);
        user.setTotpSecret(SECRET);
        RecoveryCode row = RecoveryCode.builder().id(9L).userId(3L).codeHash("hash").build();
        when(recoveryCodeRepository.findByUserIdAndUsedAtIsNull(3L)).thenReturn(List.of(row));
        when(passwordEncoder.matches("ABCDE12345", "hash")).thenReturn(true);

        // Act
        assertTrue(service.verifySecondFactor(user, "abcde-12345"));

        // Assert: ma phai bi danh dau da dung ngay lan dau va duoc luu lai
        assertNotNull(row.getUsedAt(), "mã dự phòng phải bị đánh dấu đã dùng ngay lần đầu");
        verify(recoveryCodeRepository).save(row);
    }

    @Test
    void verifySecondFactorRejectsUnknownCode() {
        // Arrange: ma khong khop TOTP va cung khong phai ma du phong
        user.setTotpEnabled(true);
        user.setTotpSecret(SECRET);
        when(totpVerifier.verify(SECRET, "000001")).thenReturn(false);

        // Act & Assert: tra false (khong nem loi) de noi goi tu quyet dinh cach bao
        assertFalse(service.verifySecondFactor(user, "000001"));
    }

    // ── bước hai đăng nhập: verifyLoginOtp ───────────────────────────────────

    private MockHttpServletRequest request(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        return request;
    }

    @Test
    void verifyLoginOtpRejectsPlainAccessTokenAsChallenge() {
        // Arrange: dung access token that (khong co claim purpose)
        // Token thường (không có claim purpose) không được đổi được JWT qua /verify-otp.
        String accessToken = JwtUtil.generateAccessToken(EMAIL, Role.ROLE_USER.name(), 0, null);

        // Act & Assert: phai tu choi, neu khong thi access token se tu "nang cap" thanh phien 2FA
        assertThrows(InvalidAccountException.class,
                () -> service.verifyLoginOtp(accessToken, "123456", request("203.0.113.9")));
    }

    @Test
    void verifyLoginOtpWithExpiredChallengeAsksReLogin() {
        // Arrange: chuoi khong phai challenge token (tuong duong challenge het han / bi sua)

        // Act & Assert: nem InvalidAccountException de FE bat dang nhap lai
        assertThrows(InvalidAccountException.class,
                () -> service.verifyLoginOtp("not-a-jwt", "123456", request("203.0.113.9")));
    }

    @Test
    void verifyLoginOtpWrongCodeRecordsFailedAttemptAndNoTokens() {
        // Arrange: challenge hop le nhung ma sai, cua so dem dang trong
        user.setTotpEnabled(true);
        user.setTotpSecret(SECRET);
        when(totpVerifier.verify(anyString(), anyString())).thenReturn(false);
        when(loginAttemptRepository.countByIpAddressAndSuccessFalseAndTimestampAfter(anyString(), any()))
                .thenReturn(0L);
        when(loginAttemptRepository.findByEmailAndSuccessFalseAndTimestampAfter(anyString(), any()))
                .thenReturn(List.of());

        // Act
        var response = assertThrows(InvalidPasswordException.class, () ->
                service.verifyLoginOtp(JwtUtil.generate2faChallengeToken(EMAIL), "000000", request("203.0.113.9")));

        // Assert: message rieng cho ma sai, va phai ghi attempt that bai de con dem
        assertEquals("Mã xác thực không đúng", response.getMessage());
        verify(loginAttemptRepository).save(any(LoginAttempt.class));
    }

    @Test
    void verifyLoginOtpSixthFailureInWindowIsLocked() {
        // Arrange: da co 6 lan sai trong cua so (vuot nguong 2FA)
        user.setTotpEnabled(true);
        user.setTotpSecret(SECRET);
        when(loginAttemptRepository.countByIpAddressAndSuccessFalseAndTimestampAfter(anyString(), any()))
                .thenReturn(0L);
        when(loginAttemptRepository.findByEmailAndSuccessFalseAndTimestampAfter(anyString(), any()))
                .thenReturn(java.util.Collections.nCopies(6, (LoginAttempt) null));

        // Act & Assert: bi khoa ngay tu dau
        assertThrows(InvalidAccountLockedException.class, () ->
                service.verifyLoginOtp(JwtUtil.generate2faChallengeToken(EMAIL), "123456", request("203.0.113.9")));

        // Va khong duoc goi bo verify OTP (da khoa thi khoi ton cong, cung khong lo timing)
        verify(totpVerifier, never()).verify(anyString(), anyString());
    }

    @Test
    void verifyLoginOtpSuccessIssuesRealTokens() {
        // Arrange: challenge hop le, OTP dung, session moi duoc tao
        user.setTotpEnabled(true);
        user.setTotpSecret(SECRET);
        when(totpVerifier.verify(SECRET, "135246")).thenReturn(true);
        when(loginAttemptRepository.countByIpAddressAndSuccessFalseAndTimestampAfter(anyString(), any()))
                .thenReturn(0L);
        when(loginAttemptRepository.findByEmailAndSuccessFalseAndTimestampAfter(anyString(), any()))
                .thenReturn(List.of());
        when(userSessionService.start(eq(user), any())).thenReturn("sid-from-011");

        // Act
        var tokens = service.verifyLoginOtp(
                JwtUtil.generate2faChallengeToken(EMAIL), "135246", request("203.0.113.9"));

        // Assert: cap ca access + refresh token that, khong con la challenge
        assertNotNull(tokens.getAccessToken());
        assertNotNull(tokens.getRefreshToken());
        assertFalse(tokens.isMfaRequired());
        // Access token phát hành phải dùng được thật (subject + không còn là challenge).
        assertEquals(EMAIL, JwtUtil.validateAccessToken(tokens.getAccessToken()).getSubject());
        // Sóng 011: bước hai của luồng 2FA cũng phải mở một dòng user_session.
        assertEquals("sid-from-011",
                JwtUtil.validateAccessToken(tokens.getAccessToken()).get("sid", String.class));
    }
}
