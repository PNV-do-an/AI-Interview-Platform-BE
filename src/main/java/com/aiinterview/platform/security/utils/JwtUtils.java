package com.aiinterview.platform.security.utils;

import com.aiinterview.platform.model.dto.response.LoginResponse;
import com.aiinterview.platform.security.jwt.JwtUtil;
import io.jsonwebtoken.Claims;

public class JwtUtils {

    /**
     * Access token mới mang đúng "sid" của refresh token đang trình — nếu phát sid khác thì
     * phiên trong `user_session` không theo được và request kế tiếp sẽ bị từ chối.
     */
    public static LoginResponse refreshAccessToken(String refreshToken, String sessionId) {
        Claims claims = JwtUtil.validateRefreshToken(refreshToken);

        String email = claims.getSubject();
        String role  = (String) claims.get("role");

        String newAccessToken = JwtUtil.generateAccessToken(email, role, sessionId);

        return new LoginResponse(newAccessToken, refreshToken, role);
    }
}
