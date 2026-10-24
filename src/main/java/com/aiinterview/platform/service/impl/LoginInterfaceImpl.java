package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.common.exception.InvalidAccountLockedException;
import com.aiinterview.platform.common.exception.InvalidAccountException;
import com.aiinterview.platform.common.exception.InvalidPasswordException;
import com.aiinterview.platform.model.dto.response.LoginResponse;
import com.aiinterview.platform.model.entity.LoginAttempt;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.repository.LoginAttemptRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.security.jwt.JwtUtil;
import com.aiinterview.platform.security.utils.SecurityUtils;
import com.aiinterview.platform.service.LoginInterface;
import com.aiinterview.platform.service.UserSessionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LoginInterfaceImpl implements LoginInterface {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptRepository loginAttemptRepository;
    private final UserSessionService userSessionService;

    @Override
    @Transactional(noRollbackFor = {
            InvalidAccountException.class,
            InvalidPasswordException.class,
            InvalidAccountLockedException.class
    })
    public LoginResponse login(String email, String password, HttpServletRequest request) {
        String ipClient = SecurityUtils.getClientIP(request);

        // Bước 1: Check IP rate-limit — 20 lần thất bại từ IP này trong 15 phút (bất kể email nào)
        long ipFailures = loginAttemptRepository.countByIpAddressAndSuccessFalseAndTimestampAfter(ipClient, LocalDateTime.now().minusMinutes(15));
        if (ipFailures >= 20) {
            throw new InvalidAccountException(
                    "Hệ thống phát hiện hoạt động bất thường từ thiết bị của bạn. Vui lòng thử lại sau."
            );
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    loginAttemptRepository.save(new LoginAttempt(email, ipClient, false));
                    return new InvalidAccountException("Email không tồn tại");
                });

        if (user.getDeletedAt() != null) {
            throw new InvalidAccountException("Tài khoản không tồn tại hoặc đã bị xóa");
        }

        if (!user.isEnabled()) {
            throw new InvalidAccountException("Tài khoản chưa được kích hoạt. Vui lòng kiểm tra email.");
        }

        // Kiểm tra admin lock (khóa bởi Admin, khác với lockUntil do nhập sai password)
        if (user.isLocked()) {
            throw new InvalidAccountLockedException(
                    "Tài khoản của bạn đã bị khóa bởi quản trị viên. Vui lòng liên hệ hỗ trợ.",
                    null
            );
        }

        if (user.getLockUntil() != null && LocalDateTime.now().isBefore(user.getLockUntil())) {
            throw new InvalidAccountLockedException(
                    "Tài khoản đã bị khóa do nhập sai quá nhiều lần. Vui lòng thử lại sau 15 phút.",
                    user.getLockUntil()
            );
        }

        // Khóa do nhập sai đã hết hạn 15 phút: phải tính lại từ đầu. Nếu để loginAttempts=5
        // nằm nguyên thì lần sai kế tiếp (5+1 >= 5) khóa lại ngay lập tức — người dùng chờ
        // đủ 15 phút vẫn không có cửa nào ngoài đổi mật khẩu.
        if (user.getLockUntil() != null) {
            user.setLockUntil(null);
            user.setLoginAttempts(0);
            userRepository.save(user);
        }

        long recentFailures = loginAttemptRepository.findByEmailAndSuccessFalseAndTimestampAfter(
                email, LocalDateTime.now().minusMinutes(15)
        ).size();
        if (recentFailures >= 5) {
            user.setLockUntil(LocalDateTime.now().plusMinutes(15));
            userRepository.save(user);
            throw new InvalidAccountLockedException(
                    "Tài khoản đã bị khóa do nhập sai quá nhiều lần. Vui lòng thử lại sau 15 phút.",
                    user.getLockUntil()
            );
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            user.setLoginAttempts(user.getLoginAttempts() + 1);
            loginAttemptRepository.save(new LoginAttempt(email, ipClient, false));

            if (user.getLoginAttempts() >= 5) {
                user.setLockUntil(LocalDateTime.now().plusMinutes(15));
                userRepository.save(user);
                throw new InvalidAccountLockedException(
                        "Tài khoản đã bị khóa do nhập sai quá nhiều lần. Khóa đến: ",
                        user.getLockUntil()
                );
            }

            throw new InvalidPasswordException("Mật khẩu sai");
        }

        // Mật khẩu đúng mới tới bước hai. Chưa qua OTP thì chưa được tính là một lần đăng nhập
        // thành công: không ghi attempt success, không reset counter.
        if (user.isTotpEnabled()) {
            return LoginResponse.mfaChallenge(JwtUtil.generate2faChallengeToken(user.getEmail()));
        }

        loginAttemptRepository.save(new LoginAttempt(email, ipClient, true));
        user.setLoginAttempts(0);
        user.setLockUntil(null);
        userRepository.save(user);

        String accessToken = JwtUtil.generateAccessToken(
                user.getEmail(), user.getRole().name(), user.getTokenVersion(), null);
        String refreshToken = JwtUtil.generateRefreshToken(
                user.getEmail(), user.getRole().name(), user.getTokenVersion(), null);

        return new LoginResponse(accessToken, refreshToken, user.getRole().name());
    }
}
