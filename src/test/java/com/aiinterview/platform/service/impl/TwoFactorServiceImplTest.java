package com.aiinterview.platform.service.impl;

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
        when(totpVerifier.newSecret()).thenReturn(SECRET);

        TwoFactorSetupResponse response = service.setup(EMAIL);

        assertEquals(SECRET, response.getSecret());
        assertTrue(response.getOtpauthUri().startsWith("otpauth://totp/"));
        assertTrue(response.getOtpauthUri().contains("secret=" + SECRET));
        assertFalse(user.isTotpEnabled(), "setup only stores the secret; enable needs a verified OTP");
        assertEquals(SECRET, user.getTotpSecret());
    }

    @Test
    void setupRejectedWhen2faAlreadyOn() {
        user.setTotpEnabled(true);

        assertThrows(BadRequestException.class, () -> service.setup(EMAIL));
    }

    @Test
    void enableRequiresPriorSetup() {
        assertThrows(BadRequestException.class, () -> service.enable(EMAIL, "123456"));
    }

    @Test
    void enableWithWrongOtpKeeps2faOff() {
        user.setTotpSecret(SECRET);
        when(totpVerifier.verify(SECRET, "000000")).thenReturn(false);

        assertThrows(InvalidPasswordException.class, () -> service.enable(EMAIL, "000000"));
        assertFalse(user.isTotpEnabled());
        verify(recoveryCodeRepository, never()).saveAll(any());
    }

    @Test
    void enableIssuesEightOneTimeRecoveryCodes() {
        user.setTotpSecret(SECRET);
        when(totpVerifier.verify(SECRET, "654321")).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenReturn("rc-hash");

        TwoFactorEnableResponse response = service.enable(EMAIL, "654321");

        assertTrue(user.isTotpEnabled());
        assertEquals(8, response.getRecoveryCodes().size());
        response.getRecoveryCodes().forEach(code ->
                assertTrue(code.matches("[A-Z0-9]{5}-[A-Z0-9]{5}"), code));

        ArgumentCaptor<List<RecoveryCode>> saved = ArgumentCaptor.forClass(List.class);
        verify(recoveryCodeRepository).saveAll(saved.capture());
        assertEquals(8, saved.getValue().size());
        // DB chỉ nhận hash, không bao giờ nhận mã rõ.
        saved.getValue().forEach(row -> assertEquals("rc-hash", row.getCodeHash()));
    }

    @Test
    void disableWithValidOtpClearsSecretAndBurnsRecoveryCodes() {
        user.setTotpEnabled(true);
        user.setTotpSecret(SECRET);
        when(totpVerifier.verify(SECRET, "111222")).thenReturn(true);

        service.disable(EMAIL, "111222");

        assertFalse(user.isTotpEnabled());
        assertNull(user.getTotpSecret());
        verify(recoveryCodeRepository).deleteByUserId(3L);
    }

    @Test
    void disableWithoutSecondFactorProofIsRejected() {
        user.setTotpEnabled(true);
        user.setTotpSecret(SECRET);
        when(totpVerifier.verify(anyString(), anyString())).thenReturn(false);

        assertThrows(InvalidPasswordException.class, () -> service.disable(EMAIL, "999999"));
        assertTrue(user.isTotpEnabled());
    }

    @Test
    void recoveryCodeIsSingleUseAndCaseDashInsensitive() {
        user.setTotpEnabled(true);
        user.setTotpSecret(SECRET);
        RecoveryCode row = RecoveryCode.builder().id(9L).userId(3L).codeHash("hash").build();
        when(recoveryCodeRepository.findByUserIdAndUsedAtIsNull(3L)).thenReturn(List.of(row));
        when(passwordEncoder.matches("ABCDE12345", "hash")).thenReturn(true);

        assertTrue(service.verifySecondFactor(user, "abcde-12345"));

        assertNotNull(row.getUsedAt(), "mã dự phòng phải bị đánh dấu đã dùng ngay lần đầu");
        verify(recoveryCodeRepository).save(row);
    }

    @Test
    void verifySecondFactorRejectsUnknownCode() {
        user.setTotpEnabled(true);
        user.setTotpSecret(SECRET);
        when(totpVerifier.verify(SECRET, "000001")).thenReturn(false);

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
        // Token thường (không có claim purpose) không được đổi được JWT qua /verify-otp.
        String accessToken = JwtUtil.generateAccessToken(EMAIL, Role.ROLE_USER.name(), 0, null);

        assertThrows(InvalidAccountException.class,
                () -> service.verifyLoginOtp(accessToken, "123456", request("203.0.113.9")));
    }

    @Test
    void verifyLoginOtpWithExpiredChallengeAsksReLogin() {
        assertThrows(InvalidAccountException.class,
                () -> service.verifyLoginOtp("not-a-jwt", "123456", request("203.0.113.9")));
    }

    @Test
    void verifyLoginOtpWrongCodeRecordsFailedAttemptAndNoTokens() {
        user.setTotpEnabled(true);
        user.setTotpSecret(SECRET);
        when(totpVerifier.verify(anyString(), anyString())).thenReturn(false);
        when(loginAttemptRepository.countByIpAddressAndSuccessFalseAndTimestampAfter(anyString(), any()))
                .thenReturn(0L);
        when(loginAttemptRepository.findByEmailAndSuccessFalseAndTimestampAfter(anyString(), any()))
                .thenReturn(List.of());

        var response = assertThrows(InvalidPasswordException.class, () ->
                service.verifyLoginOtp(JwtUtil.generate2faChallengeToken(EMAIL), "000000", request("203.0.113.9")));

        assertEquals("Mã xác thực không đúng", response.getMessage());
        verify(loginAttemptRepository).save(any(LoginAttempt.class));
    }

    @Test
    void verifyLoginOtpSixthFailureInWindowIsLocked() {
        user.setTotpEnabled(true);
        user.setTotpSecret(SECRET);
        when(loginAttemptRepository.countByIpAddressAndSuccessFalseAndTimestampAfter(anyString(), any()))
                .thenReturn(0L);
        when(loginAttemptRepository.findByEmailAndSuccessFalseAndTimestampAfter(anyString(), any()))
                .thenReturn(java.util.Collections.nCopies(6, (LoginAttempt) null));

        assertThrows(InvalidAccountLockedException.class, () ->
                service.verifyLoginOtp(JwtUtil.generate2faChallengeToken(EMAIL), "123456", request("203.0.113.9")));
        verify(totpVerifier, never()).verify(anyString(), anyString());
    }

    @Test
    void verifyLoginOtpSuccessIssuesRealTokens() {
        user.setTotpEnabled(true);
        user.setTotpSecret(SECRET);
        when(totpVerifier.verify(SECRET, "135246")).thenReturn(true);
        when(loginAttemptRepository.countByIpAddressAndSuccessFalseAndTimestampAfter(anyString(), any()))
                .thenReturn(0L);
        when(loginAttemptRepository.findByEmailAndSuccessFalseAndTimestampAfter(anyString(), any()))
                .thenReturn(List.of());

        var tokens = service.verifyLoginOtp(
                JwtUtil.generate2faChallengeToken(EMAIL), "135246", request("203.0.113.9"));

        assertNotNull(tokens.getAccessToken());
        assertNotNull(tokens.getRefreshToken());
        assertFalse(tokens.isMfaRequired());
        // Access token phát hành phải dùng được thật (subject + không còn là challenge).
        assertEquals(EMAIL, JwtUtil.validateAccessToken(tokens.getAccessToken()).getSubject());
    }
}
