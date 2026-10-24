package com.aiinterview.platform.security.utils;

import com.aiinterview.platform.model.dto.response.LoginResponse;
import com.aiinterview.platform.security.jwt.JwtUtil;
import io.jsonwebtoken.Claims;

public class JwtUtils {

    /**
     * tokenVersion lấy từ users hiện hành của chủ token, không từ claim — claim chỉ dùng để
     * đối chiếu ở bước kiểm tra, còn token mới phải mang phiên đang sống.
     * "sid" thì ngược lại: giữ nguyên claim của refresh token, vì một lần đăng nhập chỉ có
     * một dòng user_session — phát sid mới thì access token không khớp phiên nào nữa.
     */
    public static LoginResponse refreshAccessToken(String refreshToken, int tokenVersion) {
        Claims claims = JwtUtil.validateRefreshToken(refreshToken);

        String email = claims.getSubject();
        String role  = (String) claims.get("role");
        String sessionId = claims.get("sid", String.class);

        String newAccessToken = JwtUtil.generateAccessToken(email, role, tokenVersion, sessionId);

        return new LoginResponse(newAccessToken, refreshToken, role);
    }
}
