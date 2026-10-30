package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.common.exception.InvalidAccountException;
import com.aiinterview.platform.common.exception.InvalidPasswordException;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.service.EmailService;
import com.aiinterview.platform.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetServiceImpl implements PasswordResetService {

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%^&+=!]).{8,}$");

    private static final String GENERIC_MESSAGE = "Nếu email tồn tại, bạn sẽ nhận được link đặt lại mật khẩu.";

    @Override
    public String forgotPassword(String email) {
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            return GENERIC_MESSAGE;
        }

        String resetToken = UUID.randomUUID().toString();
        user.setResetToken(resetToken);
        user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(15));
        userRepository.save(user);

        try {
            emailService.sendResetPasswordEmail(email, user.getFullName(), resetToken);
        } catch (Exception e) {
            log.warn("Không thể gửi email reset password tới {}: {}", email, e.getMessage());
        }

        return GENERIC_MESSAGE;
    }

    @Override
    public String resetPassword(String token, String newPassword) {
        User user = userRepository.findByResetToken(token)
                .orElseThrow(() -> new InvalidAccountException("Link đặt lại mật khẩu không hợp lệ"));

        if (user.getResetTokenExpiry() == null || LocalDateTime.now().isAfter(user.getResetTokenExpiry())) {
            throw new InvalidAccountException("Link đặt lại mật khẩu đã hết hạn. Vui lòng yêu cầu lại.");
        }

        if (newPassword == null || !PASSWORD_PATTERN.matcher(newPassword).matches()) {
            throw new InvalidPasswordException(
                    "Mật khẩu phải có tối thiểu 8 ký tự, bao gồm chữ hoa, chữ thường, số và ký tự đặc biệt (@#$%^&+=!)"
            );
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setTokenVersion(user.getTokenVersion() + 1);
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

        return "Mật khẩu đã được đặt lại thành công. Vui lòng đăng nhập lại.";
    }
}
