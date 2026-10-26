package com.aiinterview.platform.security.utils;

import com.aiinterview.platform.model.dto.response.LoginResponse;
import com.aiinterview.platform.security.jwt.JwtUtil;
import io.jsonwebtoken.Claims;

public class JwtUtils {

    /**
     * tokenVersion lấy từ users hiện hành của chủ token, không từ claim — claim chỉ dùng để
     * đối chiếu ở bước kiểm tra, còn token mới phải mang phiên đang sống.
     * "sid" do caller truyền ra: caller giữ nguyên sid của refresh token, chỉ mint sid mới
     * cho token đời trước chưa có phiên, và đã chặn sid bị thu hồi trước khi tới đây.
     */
    public static LoginResponse refreshAccessToken(String refreshToken, int tokenVersion, String sessionId) {
        Claims claims = JwtUtil.validateRefreshToken(refreshToken);

        String email = claims.getSubject();
        String role  = (String) claims.get("role");

        String newAccessToken = JwtUtil.generateAccessToken(email, role, tokenVersion, sessionId);

        return new LoginResponse(newAccessToken, refreshToken, role);
    }
}
