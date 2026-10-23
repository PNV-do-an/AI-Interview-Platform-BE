package com.aiinterview.platform.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import java.security.Key;
import java.util.Date;

public class JwtUtil {

    private static final String ACCESS_SECRET =
            secretFromEnv("JWT_ACCESS_SECRET", "access-secret-access-secret-access-secret");
    private static final String REFRESH_SECRET =
            secretFromEnv("JWT_REFRESH_SECRET", "refresh-secret-refresh-secret-refresh-secret");
    private static final Key accessKey = Keys.hmacShaKeyFor(ACCESS_SECRET.getBytes());
    private static final Key refreshKey = Keys.hmacShaKeyFor(REFRESH_SECRET.getBytes());
    private static final long EXPIRATION_TIME_ACCESS_TOKEN = 1000 * 60 * 60 * 24;
    private static final long EXPIRATION_TIME_REFRESH_TOKEN =  1000L * 60 * 60 * 24 * 7;

    /**
     * Access token mang "tv" của phiên đang cấp. JwtFilter đối chiếu claim này với
     * users.token_version mọi request, nên logout/đổi mật khẩu thu hồi cả access token đang sống
     * chứ không chỉ refresh token.
     */
    public static String generateAccessToken(String account, String role, int tokenVersion) {

        String accessToken = Jwts.builder()
                .setSubject(account)
                .claim("role", role)
                .claim("tv", tokenVersion)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME_ACCESS_TOKEN))
                .signWith(accessKey)
                .compact();

        return accessToken;
    }

    public static String generateRefreshToken(String account, String role, int tokenVersion) {

        String refreshToken = Jwts.builder()
                .setSubject(account)
                .claim("role", role)
                .claim("tv", tokenVersion)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME_REFRESH_TOKEN))
                .signWith(refreshKey)
                .compact();

        return refreshToken;
    }

    public static Claims validateAccessToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(accessKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public static Claims validateRefreshToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(refreshKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public static String getUsername(String token) {
        return validateAccessToken(token).getSubject();
    }

    public static String getRole(String token) {
        return validateAccessToken(token).get("role", String.class);
    }

    private static String secretFromEnv(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}

//
//--> Tiếp tục endpoint gọi để refresh token -> ( đặt logic kểm tra ở đâu ) -> kiểm tra
//        mỗi lần client gửi request