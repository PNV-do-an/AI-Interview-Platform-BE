package com.aiinterview.platform.security.utils;

import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static String getClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // Trả về IP đầu tiên trong chuỗi vì đó là IP gốc của máy Client
            return xForwardedFor.split(",")[0].trim();
        }
        // Nếu không có proxy -> lấy trực tiếp
        return request.getRemoteAddr();
    }

    /** Returns the currently authenticated user, or throws if not found. */
    public static User getCurrentUser(UserRepository userRepository) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new ResourceNotFoundException("Người dùng chưa đăng nhập");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + auth.getName()));
    }
}