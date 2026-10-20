package com.aiinterview.platform;

import com.aiinterview.platform.common.exception.EmailAlreadyRegisteredException;
import com.aiinterview.platform.common.exception.InvalidAccountException;
import com.aiinterview.platform.common.exception.InvalidAccountLockedException;
import com.aiinterview.platform.common.exception.InvalidPasswordException;
import com.aiinterview.platform.config.DataInitializer;
import com.aiinterview.platform.model.dto.response.PasswordResetResponse;
import com.aiinterview.platform.model.dto.response.ResetTokenInfoResponse;
import com.aiinterview.platform.model.entity.LoginAttempt;
import com.aiinterview.platform.model.entity.PasswordResetAttempt;
import com.aiinterview.platform.model.entity.RegisterAttempt;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.LoginAttemptRepository;
import com.aiinterview.platform.model.repository.PasswordResetAttemptRepository;
import com.aiinterview.platform.model.repository.RegisterAttemptRepository;
import com.aiinterview.platform.model.repository.JpaUserRepository;
import com.aiinterview.platform.service.EmailService;
import com.aiinterview.platform.service.LoginInterface;
import com.aiinterview.platform.service.PasswordResetService;
import com.aiinterview.platform.service.RegisterInterface;
import com.aiinterview.platform.service.UserService;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
class PersistenceTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    private static final String PASSWORD = "PersistenceTest1!";
    private static final String NEW_PASSWORD = "ChangedTest2!";
    private static final String FULL_NAME = "Persistence Test User";
    private static final String PHONE = "2025550100";

    private ConfigurableApplicationContext context;
    private JpaUserRepository userRepository;
    private LoginAttemptRepository loginAttemptRepository;
    private RegisterAttemptRepository registerAttemptRepository;
    private PasswordResetAttemptRepository resetAttemptRepository;
    private LoginInterface loginService;
    private RegisterInterface registerService;
    private PasswordResetService passwordResetService;
    private UserService userService;
    private PasswordEncoder passwordEncoder;
    private EmailService emailService;

    @BeforeEach
    void openApplicationAndCleanContainer() {
        openContext();
        loginAttemptRepository.deleteAll();
        registerAttemptRepository.deleteAll();
        resetAttemptRepository.deleteAll();
        userRepository.deleteAll();
    }

    @AfterEach
    void closeApplication() {
        closeContext();
    }

    @Test
    void registrationAndVerificationKeepEnabledProfileAfterRestart() {
        String email = "registration@example.test";
        String ip = "203.0.113.11";

        assertNotNull(registerService.register(email, PASSWORD, FULL_NAME, PHONE, request(ip)));

        assertEquals(1L, userRepository.count());
        assertTrue(userRepository.existsByEmail(email));
        User registered = storedUser(email);
        Long userId = registered.getId();
        assertNotNull(userId);
        // User mới đăng ký CHƯA được kích hoạt — phải verify email (bên dưới) mới bật enabled
        assertFalse(registered.isEnabled());
        assertEquals(FULL_NAME, registered.getFullName());
        assertEquals(PHONE, registered.getPhone());
        assertEquals(Role.ROLE_USER, registered.getRole());
        assertNotEquals(PASSWORD, registered.getPassword());
        assertTrue(passwordEncoder.matches(PASSWORD, registered.getPassword()));
        assertNotNull(registered.getCreatedAt());
        assertNotNull(registered.getUpdatedAt());
        String token = registered.getVerificationToken();
        assertNotNull(token);
        assertFalse(token.isBlank());
        assertTrue(registered.getVerificationTokenExpiry().isAfter(LocalDateTime.now()));
        assertEquals(userId, userRepository.findByVerificationToken(token).orElseThrow().getId());
        // Đăng ký thành công không còn ghi register_attempts (rate-limit đếm theo cửa sổ 5 phút)
        assertEquals(0L, registerAttemptRepository.count());
        verify(emailService).sendVerificationEmail(email, FULL_NAME, token);

        registerService.verifyEmail(token);
        assertTrue(storedUser(email).isEnabled());
        assertTrue(userRepository.findByVerificationToken(token).isEmpty());

        restartContext();

        User profile = userService.findByEmail(email);
        assertEquals(userId, profile.getId());
        assertEquals(email, profile.getEmail());
        assertEquals(FULL_NAME, profile.getFullName());
        assertEquals(PHONE, profile.getPhone());
        assertEquals(Role.ROLE_USER, profile.getRole());
        assertTrue(profile.isEnabled());
        assertEquals(registered.getPassword(), profile.getPassword());
        assertTrue(passwordEncoder.matches(PASSWORD, profile.getPassword()));
        assertNull(profile.getVerificationToken());
        assertNull(profile.getVerificationTokenExpiry());
        assertEquals(registered.getCreatedAt(), profile.getCreatedAt());
        assertNotNull(profile.getUpdatedAt());
        assertEquals(0L, registerAttemptRepository.count());
        assertEquals(0L, loginAttemptRepository.count());
    }

    @Test
    void failedLoginsCommitCountersHistoryAndFifthAttemptLockAcrossRestart() {
        String email = "login-lock@example.test";
        String ip = "203.0.113.12";
        Long userId = seedEnabledUser(email).getId();
        MockHttpServletRequest request = request(ip);

        for (int call = 1; call <= 4; call++) {
            assertThrows(InvalidPasswordException.class,
                    () -> loginService.login(email, "WrongPassword1!", request));
            User persisted = storedUser(email);
            assertEquals(call, persisted.getLoginAttempts());
            assertNull(persisted.getLockUntil());
            assertFailedHistory(email, ip, call);
        }

        assertThrows(InvalidAccountLockedException.class,
                () -> loginService.login(email, "WrongPassword1!", request));
        User locked = storedUser(email);
        assertEquals(5, locked.getLoginAttempts());
        LocalDateTime lockUntil = locked.getLockUntil();
        assertNotNull(lockUntil);
        assertTrue(lockUntil.isAfter(LocalDateTime.now()));
        Set<Object> historyIds = assertFailedHistory(email, ip, 5).stream()
                .map(this::identifier).collect(Collectors.toSet());
        assertEquals(5, historyIds.size());

        restartContext();

        User persisted = storedUser(email);
        assertEquals(userId, persisted.getId());
        assertEquals(5, persisted.getLoginAttempts());
        assertEquals(lockUntil, persisted.getLockUntil());
        assertEquals(historyIds, assertFailedHistory(email, ip, 5).stream()
                .map(this::identifier).collect(Collectors.toSet()));
        assertThrows(InvalidAccountLockedException.class,
                () -> loginService.login(email, "WrongPassword1!", request));
        assertEquals(5, storedUser(email).getLoginAttempts());
        assertEquals(lockUntil, storedUser(email).getLockUntil());
        assertFailedHistory(email, ip, 5);
        verifyNoInteractions(emailService);
    }

    @Test
    void expiredWrongPasswordLockRestartsTheCounterInsteadOfReLockingOnFirstMistake() {
        String email = "login-lock-expired@example.test";
        String ip = "203.0.113.28";
        MockHttpServletRequest request = request(ip);

        // Hiện trạng sau khi người dùng đã bị khóa đủ 5 lần rồi hết hạn 15 phút (và các dòng
        // login_attempts đã nằm ngoài cửa sổ 15 phút — chính nhánh dưới đó chặn): bộ đếm 5
        // vẫn nằm trên users, lockUntil đã qua.
        User expiredLock = seedEnabledUser(email);
        expiredLock.setLoginAttempts(5);
        expiredLock.setLockUntil(LocalDateTime.now().minusMinutes(1));
        userRepository.saveAndFlush(expiredLock);

        // Trước bản vá: 5 + 1 >= 5 nên một lần sai duy nhất khóa lại ngay (423), người dùng
        // không bao giờ thoát được trạng thái khóa nếu chưa kịp đăng nhập thành công.
        assertThrows(InvalidPasswordException.class,
                () -> loginService.login(email, "WrongPassword1!", request));
        User afterMistake = storedUser(email);
        assertEquals(1, afterMistake.getLoginAttempts());
        assertNull(afterMistake.getLockUntil());

        assertNotNull(loginService.login(email, PASSWORD, request).getAccessToken());
        User recovered = storedUser(email);
        assertEquals(0, recovered.getLoginAttempts());
        assertNull(recovered.getLockUntil());
    }

    @Test
    void unknownEmailLoginCommitsFailureDespiteInvalidAccountException() {
        String email = "unknown@example.test";
        String ip = "203.0.113.13";

        assertThrows(InvalidAccountException.class,
                () -> loginService.login(email, PASSWORD, request(ip)));
        assertEquals(0L, userRepository.count());
        Object attemptId = identifier(assertFailedHistory(email, ip, 1).get(0));

        restartContext();

        assertEquals(0L, userRepository.count());
        assertEquals(attemptId, identifier(assertFailedHistory(email, ip, 1).get(0)));
        assertEquals(0L, registerAttemptRepository.count());
        verifyNoInteractions(emailService);
    }

    @Test
    void invalidRegistrationsRecordOneAttemptPerFailureAndWindowLimitBlocksEmailThenIp() {
        String email = "registration-lock@example.test";
        String ip = "203.0.113.14";
        MockHttpServletRequest request = request(ip);

        for (int call = 1; call <= 5; call++) {
            assertThrows(InvalidPasswordException.class,
                    () -> registerService.register(email, "weak", FULL_NAME, PHONE, request));
            assertEquals((long) call, registerAttemptRepository.count());
            assertEquals(call, registerAttemptRepository.countByAddressAndTimestampAfter(
                    ip, LocalDateTime.now().minusMinutes(5)));
            assertEquals(call, registerAttemptRepository.countByAccountAndTimestampAfter(
                    email, LocalDateTime.now().minusMinutes(5)));
            assertEquals(0L, userRepository.count());
        }

        // Đủ 5 lần thất bại cho email trong 5 phút: chặn trước cả khi kiểm tra độ mạnh mật khẩu
        InvalidPasswordException emailBlocked = assertThrows(InvalidPasswordException.class,
                () -> registerService.register(email, PASSWORD, FULL_NAME, PHONE, request));
        assertTrue(emailBlocked.getMessage().contains("Vui lòng thử lại sau 5 phút"));
        assertEquals(5L, registerAttemptRepository.count());

        // 5 email khác nhau cùng IP: chưa tới ngưỡng 10 nên vẫn ghi được thất bại thứ 6
        for (int extra = 6; extra <= 10; extra++) {
            String otherEmail = "registration-lock-" + extra + "@example.test";
            assertThrows(InvalidPasswordException.class,
                    () -> registerService.register(otherEmail, "weak", FULL_NAME, PHONE, request));
            assertEquals((long) extra, registerAttemptRepository.count());
        }

        // Ngưỡng IP >= 10 trong 5 phút: phong tỏa cả email mới
        String freshEmail = "brand-new@example.test";
        InvalidPasswordException ipBlocked = assertThrows(InvalidPasswordException.class,
                () -> registerService.register(freshEmail, PASSWORD, FULL_NAME, PHONE, request));
        assertTrue(ipBlocked.getMessage().contains("bất thường"));
        assertEquals(10L, registerAttemptRepository.count());
        assertEquals(0L, userRepository.count());
        verifyNoInteractions(emailService);
    }

    @Test
    void duplicateEmailRegistrationCommitsAttemptAndKeepsWindowCount() {
        String email = "duplicate-register@example.test";
        String ip = "203.0.113.16";
        Long userId = seedEnabledUser(email).getId();

        assertThrows(EmailAlreadyRegisteredException.class,
                () -> registerService.register(email, PASSWORD, FULL_NAME, PHONE, request(ip)));

        LocalDateTime since = LocalDateTime.now().minusMinutes(5);
        assertEquals(1L, registerAttemptRepository.count());
        assertEquals(1L, registerAttemptRepository.countByAccountAndTimestampAfter(email, since));
        assertEquals(1L, registerAttemptRepository.countByAddressAndTimestampAfter(ip, since));
        assertEquals(1L, userRepository.count());
        assertEquals(userId, storedUser(email).getId());
        verifyNoInteractions(emailService);

        restartContext();

        assertEquals(1L, registerAttemptRepository.count());
        assertEquals(1L, registerAttemptRepository.countByAccountAndTimestampAfter(email, since));
    }

    @Test
    void recentAttemptQueriesRespectSuccessEmailIpAndStrictTimestampCutoffs() {
        String email = "queries@example.test";
        String otherEmail = "other-queries@example.test";
        String ip = "203.0.113.15";
        String otherIp = "203.0.113.115";
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(15).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime recent = cutoff.plusMinutes(1);

        loginAttemptRepository.saveAllAndFlush(List.of(
                loginAttempt(email, ip, false, recent),
                loginAttempt(email, ip, false, cutoff),
                loginAttempt(email, ip, false, cutoff.minusSeconds(1)),
                loginAttempt(email, ip, true, recent),
                loginAttempt(otherEmail, ip, false, recent),
                loginAttempt(email, otherIp, false, recent),
                loginAttempt(otherEmail, otherIp, false, recent)));

        List<LoginAttempt> emailFailures = loginAttemptRepository
                .findByEmailAndSuccessFalseAndTimestampAfter(email, cutoff);
        assertEquals(2, emailFailures.size());
        assertEquals(Set.of(ip, otherIp), emailFailures.stream()
                .map(LoginAttempt::getIpAddress).collect(Collectors.toSet()));
        emailFailures.forEach(attempt -> {
            assertEquals(email, attempt.getEmail());
            assertFalse(attempt.isSuccess());
            assertTrue(attempt.getTimestamp().isAfter(cutoff));
            assertNotNull(identifier(attempt));
        });
        assertEquals(2L, loginAttemptRepository.countByIpAddressAndSuccessFalseAndTimestampAfter(ip, cutoff));
        assertEquals(2L, loginAttemptRepository.countByIpAddressAndSuccessFalseAndTimestampAfter(otherIp, cutoff));
        assertEquals(0L, loginAttemptRepository.countByIpAddressAndSuccessFalseAndTimestampAfter(
                "203.0.113.215", cutoff));
        assertTrue(loginAttemptRepository.findByEmailAndSuccessFalseAndTimestampAfter(
                "absent@example.test", cutoff).isEmpty());

        RegisterAttempt oldest = registerAttempt(ip, email, cutoff.minusSeconds(1));
        registerAttemptRepository.saveAllAndFlush(List.of(
                oldest,
                registerAttempt(ip, email, cutoff),
                registerAttempt(ip, email, recent),
                registerAttempt(ip, otherEmail, recent.plusMinutes(1)),
                registerAttempt(otherIp, email, recent)));

        assertEquals(2L, registerAttemptRepository.countByAddressAndTimestampAfter(ip, cutoff));
        assertEquals(1L, registerAttemptRepository.countByAddressAndTimestampAfter(otherIp, cutoff));
        assertEquals(0L, registerAttemptRepository.countByAddressAndTimestampAfter("203.0.113.215", cutoff));
        RegisterAttempt firstByIp = registerAttemptRepository.findFirstByAddressOrderByIdAsc(ip);
        RegisterAttempt firstByEmail = registerAttemptRepository.findFirstByAccountOrderByIdAsc(email);
        assertNotNull(firstByIp);
        assertNotNull(firstByEmail);
        assertNotNull(identifier(oldest));
        assertEquals(identifier(oldest), identifier(firstByIp));
        assertEquals(identifier(oldest), identifier(firstByEmail));
        assertNull(registerAttemptRepository.findFirstByAddressOrderByIdAsc("203.0.113.215"));
        assertNull(registerAttemptRepository.findFirstByAccountOrderByIdAsc("absent@example.test"));
    }

    @Test
    void passwordResetPersistsTokenAndLockThenPersistsNewPasswordAndClearedState() {
        String email = "reset@example.test";
        User seeded = seedEnabledUser(email);
        seeded.setLoginAttempts(5);
        seeded.setLockUntil(LocalDateTime.now().plusMinutes(15));
        userRepository.saveAndFlush(seeded);

        assertNotNull(passwordResetService.forgotPassword(email, request("203.0.113.30")));
        User pending = storedUser(email);
        Long userId = pending.getId();
        String token = pending.getResetToken();
        LocalDateTime expiry = pending.getResetTokenExpiry();
        LocalDateTime lockUntil = pending.getLockUntil();
        String oldHash = pending.getPassword();
        assertNotNull(token);
        assertFalse(token.isBlank());
        assertNotNull(expiry);
        assertTrue(expiry.isAfter(LocalDateTime.now()));
        long grantedMinutes = Duration.between(LocalDateTime.now(), expiry).toMinutes();
        assertTrue(grantedMinutes > 0 && grantedMinutes <= 30,
                "Token phải có hạn sử dụng 30 phút, đo được " + grantedMinutes);
        assertEquals(5, pending.getLoginAttempts());
        assertNotNull(lockUntil);
        assertTrue(lockUntil.isAfter(LocalDateTime.now()));
        assertTrue(passwordEncoder.matches(PASSWORD, oldHash));
        assertEquals(userId, userRepository.findByResetToken(token).orElseThrow().getId());
        verify(emailService).sendResetPasswordEmail(email, FULL_NAME, token);

        ResetTokenInfoResponse tokenInfo = passwordResetService.getResetTokenInfo(token);
        assertEquals(email, tokenInfo.getEmail());
        assertEquals(FULL_NAME, tokenInfo.getFullName());
        assertEquals(Role.ROLE_USER.name(), tokenInfo.getRole());
        assertTrue(tokenInfo.getRemainingSeconds() > 0 && tokenInfo.getRemainingSeconds() <= 1800);

        restartContext();

        User persisted = userRepository.findByResetToken(token).orElseThrow();
        assertEquals(userId, persisted.getId());
        assertEquals(token, persisted.getResetToken());
        assertEquals(expiry, persisted.getResetTokenExpiry());
        assertEquals(oldHash, persisted.getPassword());
        assertEquals(5, persisted.getLoginAttempts());
        assertEquals(lockUntil, persisted.getLockUntil());
        int tokenVersionBeforeReset = persisted.getTokenVersion();
        PasswordResetResponse resetResult =
                passwordResetService.resetPassword(token, NEW_PASSWORD, NEW_PASSWORD, true);
        assertNotNull(resetResult.getEventId());
        assertTrue(resetResult.isSignedOutEverywhere());
        verify(emailService).sendPasswordResetConfirmation(email, FULL_NAME);
        User reset = storedUser(email);
        String newHash = reset.getPassword();
        assertNotEquals(oldHash, newHash);
        assertNotEquals(NEW_PASSWORD, newHash);
        assertTrue(passwordEncoder.matches(NEW_PASSWORD, newHash));
        assertFalse(passwordEncoder.matches(PASSWORD, newHash));
        assertNull(reset.getResetToken());
        assertNull(reset.getResetTokenExpiry());
        assertNull(reset.getLockUntil());
        assertEquals(0, reset.getLoginAttempts());
        assertEquals(tokenVersionBeforeReset + 1, reset.getTokenVersion());
        assertTrue(userRepository.findByResetToken(token).isEmpty());

        restartContext();

        User profile = userService.findByEmail(email);
        assertEquals(userId, profile.getId());
        assertEquals(FULL_NAME, profile.getFullName());
        assertEquals(PHONE, profile.getPhone());
        assertEquals(Role.ROLE_USER, profile.getRole());
        assertTrue(profile.isEnabled());
        assertEquals(newHash, profile.getPassword());
        assertTrue(passwordEncoder.matches(NEW_PASSWORD, profile.getPassword()));
        assertNull(profile.getResetToken());
        assertNull(profile.getResetTokenExpiry());
        assertNull(profile.getLockUntil());
        assertEquals(0, profile.getLoginAttempts());
        assertThrows(InvalidAccountException.class,
                () -> passwordResetService.resetPassword(token, PASSWORD, PASSWORD, true));
        assertEquals(newHash, storedUser(email).getPassword());
    }

    @Test
    void resetPasswordRejectsMismatchWeakPasswordAndExpiredTokenAndHonoursSignOutFlag() {
        String email = "reset-rules@example.test";
        int tokenVersionBefore = seedEnabledUser(email).getTokenVersion();

        passwordResetService.forgotPassword(email, request("203.0.113.33"));
        String token = storedUser(email).getResetToken();
        assertNotNull(token);

        assertThrows(InvalidPasswordException.class,
                () -> passwordResetService.resetPassword(token, NEW_PASSWORD, PASSWORD, true));
        verify(emailService, never()).sendPasswordResetConfirmation(anyString(), anyString());
        assertEquals(tokenVersionBefore, storedUser(email).getTokenVersion());
        assertTrue(passwordEncoder.matches(PASSWORD, storedUser(email).getPassword()));

        assertThrows(InvalidPasswordException.class,
                () -> passwordResetService.resetPassword(token, "weak", "weak", true));

        User expired = storedUser(email);
        expired.setResetTokenExpiry(LocalDateTime.now().minusMinutes(1));
        userRepository.saveAndFlush(expired);
        assertThrows(InvalidAccountException.class, () -> passwordResetService.getResetTokenInfo(token));
        assertThrows(InvalidAccountException.class,
                () -> passwordResetService.resetPassword(token, NEW_PASSWORD, NEW_PASSWORD, true));
        assertThrows(InvalidAccountException.class, () -> passwordResetService.getResetTokenInfo("  "));
        assertThrows(InvalidAccountException.class,
                () -> passwordResetService.resetPassword("absent-token", NEW_PASSWORD, NEW_PASSWORD, true));

        passwordResetService.forgotPassword(email, request("203.0.113.33"));
        String freshToken = storedUser(email).getResetToken();
        assertFalse(freshToken.equals(token));
        PasswordResetResponse kept =
                passwordResetService.resetPassword(freshToken, NEW_PASSWORD, NEW_PASSWORD, false);
        assertFalse(kept.isSignedOutEverywhere());
        User afterReset = storedUser(email);
        assertTrue(passwordEncoder.matches(NEW_PASSWORD, afterReset.getPassword()));
        assertEquals(tokenVersionBefore, afterReset.getTokenVersion());
        assertNull(afterReset.getResetToken());
        assertNull(afterReset.getResetTokenExpiry());
    }

    @Test
    void forgotPasswordCommitsEveryRequestAndThrottlesByEmailThenByIp() {
        String generic = "Nếu email tồn tại, bạn sẽ nhận được link đặt lại mật khẩu.";
        String email = "reset-throttle@example.test";
        String ip = "203.0.113.31";
        seedEnabledUser(email);
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(15);

        for (int call = 1; call <= 5; call++) {
            assertEquals(generic, passwordResetService.forgotPassword(email, request(ip)));
            assertEquals(call, resetAttemptRepository.countByAccountAndCreatedAtAfter(email, windowStart));
            assertEquals(call, resetAttemptRepository.countByAddressAndCreatedAtAfter(ip, windowStart));
            assertNotNull(storedUser(email).getResetToken());
        }
        verify(emailService, times(5)).sendResetPasswordEmail(eq(email), eq(FULL_NAME), anyString());

        // Lượt thứ 6 vượt ngưỡng 5/email: token và hạn token giữ nguyên, không gửi email, thông báo không đổi
        User beforeThrottle = storedUser(email);
        String keptToken = beforeThrottle.getResetToken();
        LocalDateTime keptExpiry = beforeThrottle.getResetTokenExpiry();
        assertEquals(generic, passwordResetService.forgotPassword(email, request(ip)));
        User afterThrottle = storedUser(email);
        assertEquals(keptToken, afterThrottle.getResetToken());
        assertEquals(keptExpiry, afterThrottle.getResetTokenExpiry());
        assertEquals(6L, resetAttemptRepository.countByAccountAndCreatedAtAfter(email, windowStart));
        verify(emailService, times(5)).sendResetPasswordEmail(eq(email), eq(FULL_NAME), anyString());

        // 6 luot dau + vong lap nay 4 luot = du 10 luot/IP trong cua so 15 phut
        for (int extra = 6; extra <= 9; extra++) {
            String otherEmail = "reset-throttle-" + extra + "@example.test";
            seedEnabledUser(otherEmail);
            passwordResetService.forgotPassword(otherEmail, request(ip));
            assertNotNull(storedUser(otherEmail).getResetToken());
        }
        assertEquals(10L, resetAttemptRepository.countByAddressAndCreatedAtAfter(ip, windowStart));

        String tenthEmail = "reset-throttle-10@example.test";
        seedEnabledUser(tenthEmail);
        assertEquals(generic, passwordResetService.forgotPassword(tenthEmail, request(ip)));
        assertNull(storedUser(tenthEmail).getResetToken());
        verify(emailService, never()).sendResetPasswordEmail(eq(tenthEmail), anyString(), anyString());

        String freshEmail = "reset-throttle-fresh@example.test";
        seedEnabledUser(freshEmail);
        assertEquals(generic, passwordResetService.forgotPassword(freshEmail, request(ip)));
        assertNull(storedUser(freshEmail).getResetToken());
        verify(emailService, never()).sendResetPasswordEmail(eq(freshEmail), anyString(), anyString());

        restartContext();

        // Lượt bị chặn vẫn nằm lại bảng nên cửa sổ 15 phút còn đếm được sau khi restart
        assertEquals(12L, resetAttemptRepository.count());
        assertEquals(12L,
                resetAttemptRepository.countByAddressAndCreatedAtAfter(ip, LocalDateTime.now().minusMinutes(15)));
        assertNull(storedUser(freshEmail).getResetToken());
        assertNull(storedUser(tenthEmail).getResetToken());
    }

    @Test
    void resetAttemptWindowQueriesRespectEmailIpAndStrictTimestampCutoff() {
        String email = "reset-window@example.test";
        String otherEmail = "reset-window-other@example.test";
        String ip = "203.0.113.32";
        String otherIp = "203.0.113.132";
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(15).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime recent = cutoff.plusMinutes(1);

        resetAttemptRepository.saveAllAndFlush(List.of(
                resetAttempt(ip, email, cutoff.minusSeconds(1)),
                resetAttempt(ip, email, cutoff),
                resetAttempt(ip, email, recent),
                resetAttempt(ip, otherEmail, recent),
                resetAttempt(otherIp, email, recent)));

        assertEquals(2L, resetAttemptRepository.countByAccountAndCreatedAtAfter(email, cutoff));
        assertEquals(1L, resetAttemptRepository.countByAccountAndCreatedAtAfter(otherEmail, cutoff));
        assertEquals(2L, resetAttemptRepository.countByAddressAndCreatedAtAfter(ip, cutoff));
        assertEquals(1L, resetAttemptRepository.countByAddressAndCreatedAtAfter(otherIp, cutoff));
        assertEquals(0L, resetAttemptRepository.countByAddressAndCreatedAtAfter("203.0.113.232", cutoff));

        PasswordResetAttempt firstByEmail = resetAttemptRepository.findFirstByAccountOrderByIdAsc(email);
        assertNotNull(firstByEmail);
        assertNotNull(identifier(firstByEmail));
        assertEquals(email, firstByEmail.getAccount());
        assertEquals(ip, firstByEmail.getAddress());
        assertEquals(cutoff.minusSeconds(1), firstByEmail.getCreatedAt());
        assertNull(resetAttemptRepository.findFirstByAccountOrderByIdAsc("absent@example.test"));
    }

    @Test
    void duplicateEmailIsRejectedByMySqlUniqueConstraintOnFlush() {
        String email = "duplicate@example.test";
        User original = seedEnabledUser(email);
        User duplicate = User.builder()
                .email(email)
                .password(passwordEncoder.encode(NEW_PASSWORD))
                .fullName("Duplicate Test User")
                .phone("2025550101")
                .role(Role.ROLE_USER)
                .enabled(true)
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> userRepository.saveAndFlush(duplicate));
        assertEquals(1L, userRepository.count());
        assertEquals(original.getId(), storedUser(email).getId());
        assertEquals(FULL_NAME, storedUser(email).getFullName());
        assertEquals(original.getPassword(), storedUser(email).getPassword());

        restartContext();

        assertEquals(1L, userRepository.count());
        assertEquals(original.getId(), storedUser(email).getId());
        assertEquals(FULL_NAME, storedUser(email).getFullName());
        assertTrue(passwordEncoder.matches(PASSWORD, storedUser(email).getPassword()));
    }

    @Test
    void verificationEmailFailureRollsBackRegistration() {
        String email = "verification-mail-failure@example.test";
        String ip = "203.0.113.18";
        doThrow(new IllegalStateException("Simulated verification email failure"))
                .when(emailService).sendVerificationEmail(eq(email), eq(FULL_NAME), anyString());

        assertThrows(IllegalStateException.class,
                () -> registerService.register(email, PASSWORD, FULL_NAME, PHONE, request(ip)));
        verify(emailService).sendVerificationEmail(eq(email), eq(FULL_NAME), anyString());
        // Mail fail -> 502 cho client và @Transactional hoàn tác user: không có tài khoản mồ côi
        assertEquals(0L, userRepository.count());
        assertEquals(0L, registerAttemptRepository.count());
        assertEquals(0L, loginAttemptRepository.count());

        restartContext();

        assertEquals(0L, userRepository.count());
        assertEquals(0L, registerAttemptRepository.count());
        assertEquals(0L, loginAttemptRepository.count());
    }

    private void openContext() {
        context = new SpringApplicationBuilder(AiInterviewPlatformApplication.class, TestEmailConfiguration.class)
                .web(WebApplicationType.SERVLET)
                .run(
                        "--server.port=0",
                        "--spring.main.allow-bean-definition-overriding=true",
                        "--spring.datasource.url=" + mysql.getJdbcUrl(),
                        "--spring.datasource.username=" + mysql.getUsername(),
                        "--spring.datasource.password=" + mysql.getPassword(),
                        "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                        "--spring.jpa.hibernate.ddl-auto=update",
                        "--spring.jpa.show-sql=false",
                        "--spring.jpa.open-in-view=false",
                        "--spring.mail.host=localhost",
                        "--spring.mail.port=2525",
                        "--spring.mail.username=persistence-test@example.test",
                        "--spring.mail.password=test-mail-password",
                        "--spring.mail.test-connection=false",
                        "--logging.level.root=WARN",
                        "--logging.level.com.aiinterview.platform=WARN",
                        "--logging.level.org.springframework.security=WARN",
                        "--logging.level.org.hibernate=WARN",
                        "--logging.level.org.hibernate.SQL=OFF",
                        "--logging.level.org.hibernate.orm.jdbc.bind=OFF");
        userRepository = context.getBean(JpaUserRepository.class);
        loginAttemptRepository = context.getBean(LoginAttemptRepository.class);
        registerAttemptRepository = context.getBean(RegisterAttemptRepository.class);
        resetAttemptRepository = context.getBean(PasswordResetAttemptRepository.class);
        loginService = context.getBean(LoginInterface.class);
        registerService = context.getBean(RegisterInterface.class);
        passwordResetService = context.getBean(PasswordResetService.class);
        userService = context.getBean(UserService.class);
        passwordEncoder = context.getBean(PasswordEncoder.class);
        emailService = context.getBean(EmailService.class);
        ServletWebServerApplicationContext servletContext =
                assertInstanceOf(ServletWebServerApplicationContext.class, context);
        assertTrue(servletContext.getWebServer().getPort() > 0);
        assertInstanceOf(BCryptPasswordEncoder.class, passwordEncoder);
        assertFalse(mockingDetails(passwordEncoder).isMock());
        Object securityFilterChain = context.getBean("springSecurityFilterChain");
        assertInstanceOf(FilterChainProxy.class, securityFilterChain);
        assertFalse(mockingDetails(securityFilterChain).isMock());
        assertTrue(mockingDetails(emailService).isMock());
    }

    private void restartContext() {
        closeContext();
        openContext();
    }

    private void closeContext() {
        if (context != null) {
            context.close();
            context = null;
        }
    }

    private User seedEnabledUser(String email) {
        return userRepository.saveAndFlush(User.builder()
                .email(email)
                .password(passwordEncoder.encode(PASSWORD))
                .fullName(FULL_NAME)
                .phone(PHONE)
                .role(Role.ROLE_USER)
                .enabled(true)
                .build());
    }

    private User storedUser(String email) {
        return userRepository.findByEmail(email).orElseThrow();
    }

    private MockHttpServletRequest request(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        return request;
    }

    private Object identifier(Object entity) {
        return context.getBean(EntityManagerFactory.class).getPersistenceUnitUtil().getIdentifier(entity);
    }

    private List<LoginAttempt> assertFailedHistory(String email, String ip, int expected) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(15);
        List<LoginAttempt> attempts = loginAttemptRepository
                .findByEmailAndSuccessFalseAndTimestampAfter(email, since);
        assertEquals(expected, attempts.size());
        assertEquals((long) expected, loginAttemptRepository.count());
        assertEquals((long) expected,
                loginAttemptRepository.countByIpAddressAndSuccessFalseAndTimestampAfter(ip, since));
        attempts.forEach(attempt -> {
            assertNotNull(identifier(attempt));
            assertEquals(email, attempt.getEmail());
            assertEquals(ip, attempt.getIpAddress());
            assertFalse(attempt.isSuccess());
            assertTrue(attempt.getTimestamp().isAfter(since));
        });
        return attempts;
    }

    private RegisterAttempt assertRegistrationAttempt(String email, String ip, int expectedFailures, boolean locked) {
        assertEquals(1L, registerAttemptRepository.count());
        RegisterAttempt byEmail = registerAttemptRepository.findFirstByAccountOrderByIdAsc(email);
        RegisterAttempt byIp = registerAttemptRepository.findFirstByAddressOrderByIdAsc(ip);
        assertNotNull(byEmail);
        assertNotNull(byIp);
        assertNotNull(identifier(byEmail));
        assertEquals(identifier(byEmail), identifier(byIp));
        assertEquals(email, byEmail.getAccount());
        assertEquals(ip, byEmail.getAddress());
        assertNull(byEmail.getIdUser());
        assertEquals(expectedFailures, byEmail.getCounterFail());
        assertEquals(expectedFailures, byIp.getCounterFail());
        assertEquals(locked, Boolean.TRUE.equals(byEmail.getLocked()));
        assertEquals(locked, Boolean.TRUE.equals(byIp.getLocked()));
        assertNotNull(byEmail.getTimestamp());
        if (locked) {
            assertNotNull(byEmail.getLockUntil());
            assertEquals(byEmail.getLockUntil(), byIp.getLockUntil());
        } else {
            assertNull(byEmail.getLockUntil());
            assertNull(byIp.getLockUntil());
        }
        return byEmail;
    }

    private LoginAttempt loginAttempt(String email, String ip, boolean success, LocalDateTime timestamp) {
        LoginAttempt attempt = new LoginAttempt(email, ip, success);
        ReflectionTestUtils.setField(attempt, "timestamp", timestamp);
        return attempt;
    }

    private RegisterAttempt registerAttempt(String ip, String email, LocalDateTime timestamp) {
        RegisterAttempt attempt = new RegisterAttempt(ip, email);
        attempt.setTimestamp(timestamp);
        return attempt;
    }

    private PasswordResetAttempt resetAttempt(String ip, String email, LocalDateTime timestamp) {
        PasswordResetAttempt attempt = new PasswordResetAttempt(ip, email);
        ReflectionTestUtils.setField(attempt, "createdAt", timestamp);
        return attempt;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestEmailConfiguration {

        @Bean(name = "dataInitializer")
        DataInitializer persistenceTestDataInitializer() {
            return mock(DataInitializer.class);
        }

        @Bean
        @Primary
        EmailService persistenceTestEmailService() {
            return mock(EmailService.class);
        }
    }
}
