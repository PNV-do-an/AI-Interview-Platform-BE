package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.common.exception.InvalidAccountException;
import com.aiinterview.platform.common.exception.InvalidPasswordException;
import com.aiinterview.platform.model.dto.response.PasswordResetResponse;
import com.aiinterview.platform.model.dto.response.ResetTokenInfoResponse;
import com.aiinterview.platform.model.entity.PasswordResetAttempt;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.repository.PasswordResetAttemptRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.security.utils.SecurityUtils;
import com.aiinterview.platform.service.EmailService;
import com.aiinterview.platform.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetServiceImpl implements PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetAttemptRepository resetAttemptRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%^&+=!]).{8,}$");

    /** Nối với chữ "Reset link expires in 30 minutes" trên màn quên mật khẩu. */
    private static final int RESET_TOKEN_TTL_MINUTES = 30;

    private static final int RESET_REQUEST_WINDOW_MINUTES = 15;
    private static final int MAX_RESET_REQUESTS_PER_EMAIL = 5;
    private static final int MAX_RESET_REQUESTS_PER_IP = 10;

    private static final String GENERIC_MESSAGE = "Nếu email tồn tại, bạn sẽ nhận được link đặt lại mật khẩu.";

    @Override
    public String forgotPassword(String email, HttpServletRequest request) {
        if (email == null || email.isBlank()) {
            return GENERIC_MESSAGE;
        }

        String ipClient = SecurityUtils.getClientIP(request);
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(RESET_REQUEST_WINDOW_MINUTES);

        long emailRequests = resetAttemptRepository.countByAccountAndCreatedAtAfter(email, windowStart);
        long ipRequests = resetAttemptRepository.countByAddressAndCreatedAtAfter(ipClient, windowStart);

        // Ghi lượt yêu cầu trước khi quyết định: lượt bị chặn cũng phải đếm được ở cửa sổ tiếp theo
        resetAttemptRepository.save(new PasswordResetAttempt(ipClient, email));

        if (emailRequests >= MAX_RESET_REQUESTS_PER_EMAIL || ipRequests >= MAX_RESET_REQUESTS_PER_IP) {
            // Vẫn trả thông báo chung như mọi lượt khác: trả lỗi sẽ tố giác email có tồn tại hay không.
            log.warn("Quá tải yêu cầu đặt lại mật khẩu — email={}, ip={}, emailRequests={}, ipRequests={}",
                    email, ipClient, emailRequests, ipRequests);
            return GENERIC_MESSAGE;
        }

        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null || user.getDeletedAt() != null) {
            return GENERIC_MESSAGE;
        }

        String resetToken = UUID.randomUUID().toString();
        user.setResetToken(resetToken);
        user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(RESET_TOKEN_TTL_MINUTES));
        userRepository.save(user);

        try {
            emailService.sendResetPasswordEmail(email, user.getFullName(), resetToken);
        } catch (Exception e) {
            log.warn("Không thể gửi email reset password tới {}: {}", email, e.getMessage());
        }

        return GENERIC_MESSAGE;
    }

    @Override
    public ResetTokenInfoResponse getResetTokenInfo(String token) {
        User user = activeResetUser(token);
        long remainingSeconds = Duration.between(LocalDateTime.now(), user.getResetTokenExpiry()).getSeconds();

        return ResetTokenInfoResponse.builder()
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .remainingSeconds(Math.max(0, remainingSeconds))
                .build();
    }

    @Override
    public PasswordResetResponse resetPassword(String token, String newPassword, String confirmPassword,
                                               boolean signOutEverywhere) {
        User user = activeResetUser(token);

        if (confirmPassword == null || !confirmPassword.equals(newPassword)) {
            throw new InvalidPasswordException("Mật khẩu xác nhận không khớp với mật khẩu mới");
        }

        if (newPassword == null || !PASSWORD_PATTERN.matcher(newPassword).matches()) {
            throw new InvalidPasswordException(
                    "Mật khẩu phải có tối thiểu 8 ký tự, bao gồm chữ hoa, chữ thường, số và ký tự đặc biệt (@#$%^&+=!)"
            );
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        if (signOutEverywhere) {
            user.setTokenVersion(user.getTokenVersion() + 1);
        }
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        user.setLoginAttempts(0);
        user.setLockUntil(null);
        userRepository.save(user);

        try {
            emailService.sendPasswordResetConfirmation(user.getEmail(), user.getFullName());
        } catch (Exception e) {
            log.warn("Không thể gửi email xác nhận reset password tới {}: {}", user.getEmail(), e.getMessage());
        }

        String eventId = "evt_rot_" + user.getId() + "_" + UUID.randomUUID().toString().substring(0, 8);
        log.info("Đã đặt lại mật khẩu userId={}, eventId={}, signedOutEverywhere={}",
                user.getId(), eventId, signOutEverywhere);

        return PasswordResetResponse.builder()
                .eventId(eventId)
                .signedOutEverywhere(signOutEverywhere)
                .build();
    }

    private User activeResetUser(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidAccountException("Link đặt lại mật khẩu không hợp lệ");
        }
        User user = userRepository.findByResetToken(token)
                .orElseThrow(() -> new InvalidAccountException("Link đặt lại mật khẩu không hợp lệ"));

        if (user.getResetTokenExpiry() == null || LocalDateTime.now().isAfter(user.getResetTokenExpiry())) {
            throw new InvalidAccountException("Link đặt lại mật khẩu đã hết hạn. Vui lòng yêu cầu lại.");
        }
        if (user.getDeletedAt() != null) {
            throw new InvalidAccountException("Tài khoản không tồn tại hoặc đã bị xóa");
        }
        return user;
    }
}
