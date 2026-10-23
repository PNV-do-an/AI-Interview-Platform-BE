package com.aiinterview.platform.security.utils;

import com.aiinterview.platform.model.dto.response.LoginResponse;
import com.aiinterview.platform.security.jwt.JwtUtil;
import io.jsonwebtoken.Claims;

public class JwtUtils {

    /**
     * tokenVersion lấy từ users hiện hành của chủ token, không từ claim — claim chỉ dùng để
     * đối chiếu ở bước kiểm tra, còn token mới phải mang phiên đang sống.
     */
    public static LoginResponse refreshAccessToken(String refreshToken, int tokenVersion) {
        Claims claims = JwtUtil.validateRefreshToken(refreshToken);

        String email = claims.getSubject();
        String role  = (String) claims.get("role");

        String newAccessToken = JwtUtil.generateAccessToken(email, role, tokenVersion);

        return new LoginResponse(newAccessToken, refreshToken, role);
    }
}
