package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.exception.InvalidAccountException;
import com.aiinterview.platform.common.exception.InvalidAccountLockedException;
import com.aiinterview.platform.common.exception.InvalidPasswordException;
import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.model.dto.response.LoginResponse;
import com.aiinterview.platform.model.dto.response.TwoFactorEnableResponse;
import com.aiinterview.platform.model.dto.response.TwoFactorSetupResponse;
import com.aiinterview.platform.model.dto.response.TwoFactorStatusResponse;
import com.aiinterview.platform.model.entity.LoginAttempt;
import com.aiinterview.platform.model.entity.RecoveryCode;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.repository.LoginAttemptRepository;
import com.aiinterview.platform.model.repository.RecoveryCodeRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.security.jwt.JwtUtil;
import com.aiinterview.platform.security.utils.SecurityUtils;
import com.aiinterview.platform.service.TwoFactorService;
import com.aiinterview.platform.service.UserSessionService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TwoFactorServiceImpl implements TwoFactorService {

    private static final int RECOVERY_CODE_COUNT = 8;
    /** Alphabet loại bỏ các ký tự dễ nhầm (0/O, 1/I/L). */
    private static final char[] RECOVERY_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RecoveryCodeRepository recoveryCodeRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final TotpVerifier totpVerifier;
    private final PasswordEncoder passwordEncoder;
    private final UserSessionService userSessionService;

    @Value("${app.name:AI Interview Platform}")
    private String issuer;

    @Override
    @Transactional(noRollbackFor = {
            InvalidAccountException.class,
            InvalidPasswordException.class,
            InvalidAccountLockedException.class
    })
    public LoginResponse verifyLoginOtp(String challengeToken, String code, HttpServletRequest request) {
        String email;
        try {
            email = JwtUtil.validate2faChallengeToken(challengeToken).getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidAccountException("Phiên xác thực 2FA đã hết hạn. Vui lòng đăng nhập lại.");
        }
        String ipClient = SecurityUtils.getClientIP(request);

        // OTP sai bị đếm y hệt mật khẩu sai: 5 lần/15 phút theo email, 20 lần/15 phút theo IP.
        long ipFailures = loginAttemptRepository.countByIpAddressAndSuccessFalseAndTimestampAfter(
                ipClient, LocalDateTime.now().minusMinutes(15));
        if (ipFailures >= 20) {
            throw new InvalidAccountException(
                    "Hệ thống phát hiện hoạt động bất thường từ thiết bị của bạn. Vui lòng thử lại sau.");
        }
        User user = requireUser(email);
        if (!user.isTotpEnabled()) {
            throw new InvalidAccountException("Tài khoản không bật 2FA. Vui lòng đăng nhập lại.");
        }
        long recentFailures = loginAttemptRepository.findByEmailAndSuccessFalseAndTimestampAfter(
                email, LocalDateTime.now().minusMinutes(15)).size();
        if (recentFailures >= 5) {
            throw new InvalidAccountLockedException(
                    "Nhập sai mã quá nhiều lần. Vui lòng thử lại sau 15 phút.", null);
        }

        if (!verifySecondFactor(user, code)) {
            loginAttemptRepository.save(new LoginAttempt(email, ipClient, false));
            throw new InvalidPasswordException("Mã xác thực không đúng");
        }

        loginAttemptRepository.save(new LoginAttempt(email, ipClient, true));
        user.setLoginAttempts(0);
        user.setLockUntil(null);
        userRepository.save(user);

        String sessionId = userSessionService.start(user, request);
        String accessToken = JwtUtil.generateAccessToken(
                user.getEmail(), user.getRole().name(), user.getTokenVersion(), sessionId);
        String refreshToken = JwtUtil.generateRefreshToken(
                user.getEmail(), user.getRole().name(), user.getTokenVersion(), sessionId);
        return new LoginResponse(accessToken, refreshToken, user.getRole().name());
    }

    @Override
    public TwoFactorStatusResponse getStatus(String email) {
        return TwoFactorStatusResponse.builder().enabled(requireUser(email).isTotpEnabled()).build();
    }

    @Override
    @Transactional
    public TwoFactorSetupResponse setup(String email) {
        User user = requireUser(email);
        if (user.isTotpEnabled()) {
            throw new BadRequestException("2FA đã bật. Hãy tắt 2FA trước khi cấu hình lại.");
        }
        String secret = totpVerifier.newSecret();
        user.setTotpSecret(secret);
        userRepository.save(user);

        String encodedIssuer = encode(issuer);
        String encodedAccount = encode(user.getEmail());
        String otpauthUri = String.format(
                "otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=6&period=30",
                encodedIssuer, encodedAccount, secret, encodedIssuer);

        return TwoFactorSetupResponse.builder()
                .secret(secret)
                .otpauthUri(otpauthUri)
                .accountName(user.getEmail())
                .issuer(issuer)
                .build();
    }

    @Override
    @Transactional
    public TwoFactorEnableResponse enable(String email, String code) {
        User user = requireUser(email);
        if (user.isTotpEnabled()) {
            throw new BadRequestException("2FA đã bật");
        }
        if (user.getTotpSecret() == null) {
            throw new BadRequestException("Chưa bắt đầu cấu hình 2FA. Hãy gọi POST /api/v1/auth/2fa/setup.");
        }
        if (!totpVerifier.verify(user.getTotpSecret(), code)) {
            throw new InvalidPasswordException("Mã OTP không đúng. Hãy kiểm tra lại ứng dụng xác thực.");
        }

        user.setTotpEnabled(true);
        userRepository.save(user);

        List<String> plain = new ArrayList<>();
        List<RecoveryCode> rows = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String plainCode = randomRecoveryCode();
            plain.add(plainCode);
            rows.add(RecoveryCode.builder()
                    .userId(user.getId())
                    .codeHash(passwordEncoder.encode(normalizeRecoveryCode(plainCode)))
                    .build());
        }
        recoveryCodeRepository.saveAll(rows);

        return TwoFactorEnableResponse.builder().recoveryCodes(plain).build();
    }

    @Override
    @Transactional
    public void disable(String email, String code) {
        User user = requireUser(email);
        if (!user.isTotpEnabled()) {
            throw new BadRequestException("2FA chưa bật");
        }
        if (!verifySecondFactor(user, code)) {
            throw new InvalidPasswordException("Mã không hợp lệ. Cần OTP hiện tại hoặc recovery code còn hiệu lực.");
        }
        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        userRepository.save(user);
        recoveryCodeRepository.deleteByUserId(user.getId());
    }

    /** OTP đúng secret HOẶC recovery code còn hiệu lực (recovery dùng một lần). */
    @Override
    public boolean verifySecondFactor(User user, String code) {
        if (user.getTotpSecret() != null && code != null && code.matches("\\d{6}")
                && totpVerifier.verify(user.getTotpSecret(), code)) {
            return true;
        }
        String normalized = normalizeRecoveryCode(code);
        if (normalized == null) {
            return false;
        }
        for (RecoveryCode row : recoveryCodeRepository.findByUserIdAndUsedAtIsNull(user.getId())) {
            if (passwordEncoder.matches(normalized, row.getCodeHash())) {
                row.setUsedAt(LocalDateTime.now());
                recoveryCodeRepository.save(row);
                return true;
            }
        }
        return false;
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + email));
    }

    private static String randomRecoveryCode() {
        char[] raw = new char[10];
        for (int i = 0; i < raw.length; i++) {
            raw[i] = RECOVERY_ALPHABET[RANDOM.nextInt(RECOVERY_ALPHABET.length)];
        }
        return new String(raw, 0, 5) + "-" + new String(raw, 5, 5);
    }

    /** Bỏ dấu gạch nối, đưa về hoa — user gõ "abcde-fghij" vẫn khớp. */
    private static String normalizeRecoveryCode(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.replace("-", "").trim().toUpperCase();
        return normalized.length() == 10 ? normalized : null;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
