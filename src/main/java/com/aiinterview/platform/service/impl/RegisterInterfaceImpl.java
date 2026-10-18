package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.common.exception.EmailAlreadyRegisteredException;
import com.aiinterview.platform.common.exception.InvalidAccountException;
import com.aiinterview.platform.common.exception.InvalidPasswordException;
import com.aiinterview.platform.model.dto.response.RegisterResponse;
import com.aiinterview.platform.model.entity.RegisterAttempt;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.RegisterAttemptRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.security.utils.SecurityUtils;
import com.aiinterview.platform.service.EmailService;
import com.aiinterview.platform.service.RegisterInterface;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegisterInterfaceImpl implements RegisterInterface {

    private final UserRepository userRepository;
    private final RegisterAttemptRepository registerAttemptRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%^&+=!]).{8,}$");

    @Override
    @Transactional(noRollbackFor = {InvalidPasswordException.class, EmailAlreadyRegisteredException.class})
    public RegisterResponse register(String email, String password, String fullName, String phone,
                                     HttpServletRequest request) {
        String ipClient = SecurityUtils.getClientIP(request);
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(5);

        // Bước 1: IP rate-limit — >= 10 lần đăng ký thất bại từ IP này trong 5 phút gần nhất
        long ipFailures = registerAttemptRepository.countByAddressAndTimestampAfter(ipClient, windowStart);
        if (ipFailures >= 10) {
            throw new InvalidPasswordException(
                    "Hệ thống phát hiện hoạt động bất thường từ thiết bị của bạn. Vui lòng thử lại sau."
            );
        }

        // Bước 2: email rate-limit — >= 5 lần đăng ký thất bại cho email này trong 5 phút gần nhất
        long emailFailures = registerAttemptRepository.countByAccountAndTimestampAfter(email, windowStart);
        if (emailFailures >= 5) {
            throw new InvalidPasswordException(
                    "Email này đã thực hiện quá nhiều lần đăng ký thất bại. Vui lòng thử lại sau 5 phút."
            );
        }

        if (userRepository.existsByEmail(email)) {
            recordFailedAttempt(ipClient, email);
            throw new EmailAlreadyRegisteredException("Email đã tồn tại trong hệ thống");
        }

        if (password == null || !PASSWORD_PATTERN.matcher(password).matches()) {
            recordFailedAttempt(ipClient, email);
            throw new InvalidPasswordException(
                    "Mật khẩu phải có tối thiểu 8 ký tự, bao gồm chữ hoa, chữ thường, số và ký tự đặc biệt (@#$%^&+=!)"
            );
        }

        String verificationToken = UUID.randomUUID().toString();

        User newUser = User.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .fullName(fullName)
                .phone(phone)
                .role(Role.ROLE_USER)
                // enabled=false: phải bấm link xác thực trong mail (verifyEmail bật lên true) mới login được
                .enabled(false)
                .verificationToken(verificationToken)
                .verificationTokenExpiry(LocalDateTime.now().plusHours(24))
                .build();

        userRepository.save(newUser);

        try {
            emailService.sendVerificationEmail(email, fullName, verificationToken);
        } catch (Exception e) {
            log.warn("Không thể gửi email xác thực tới {}: {}", email, e.getMessage());
        }

        return new RegisterResponse("Đăng ký thành công! Vui lòng kiểm tra email và bấm link xác thực trước khi đăng nhập.");
    }

    private void recordFailedAttempt(String ipClient, String email) {
        RegisterAttempt failed = new RegisterAttempt(ipClient, email);
        failed.setTimestamp(LocalDateTime.now());
        registerAttemptRepository.save(failed);
    }

    @Override
    public String verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new InvalidAccountException("Token xác thực không hợp lệ"));

        if (LocalDateTime.now().isAfter(user.getVerificationTokenExpiry())) {
            throw new InvalidAccountException("Link xác thực đã hết hạn (quá 24 giờ)");
        }

        user.setEnabled(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiry(null);
        userRepository.save(user);

        return "Tài khoản đã được kích hoạt thành công! Bạn có thể đăng nhập ngay.";
    }

    @Override
    public void resendVerification(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidAccountException("Email không tồn tại trong hệ thống"));

        if (user.isEnabled()) {
            throw new InvalidAccountException("Tài khoản đã được kích hoạt");
        }

        String newToken = UUID.randomUUID().toString();
        user.setVerificationToken(newToken);
        user.setVerificationTokenExpiry(LocalDateTime.now().plusHours(24));
        userRepository.save(user);

        try {
            emailService.sendResendVerificationEmail(email, user.getFullName(), newToken);
        } catch (Exception e) {
            log.warn("Không thể gửi lại email xác thực tới {}: {}", email, e.getMessage());
        }
    }
}
