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
    private static final long EXPIRATION_TIME_2FA_CHALLENGE = 1000L * 60 * 5;
    public static final String PURPOSE_2FA_CHALLENGE = "2fa-challenge";

    public static String generateAccessToken(String account, String role, String sessionId) {

        JwtBuilder builder = Jwts.builder()
                .setSubject(account)
                .claim("role", role)
//                .claim("age",age)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME_ACCESS_TOKEN))
                .signWith(accessKey);
        if (sessionId != null) {
            builder.claim("sid", sessionId);
        }

        return builder.compact();
    }

    public static String generateRefreshToken(String account, String role, int tokenVersion, String sessionId) {

        JwtBuilder builder = Jwts.builder()
                .setSubject(account)
                .claim("role", role)
                .claim("tv", tokenVersion)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME_REFRESH_TOKEN))
                .signWith(refreshKey);
        if (sessionId != null) {
            builder.claim("sid", sessionId);
        }

        return builder.compact();
    }

    /** Phiên sống đúng bằng refresh token; `user_session.expires_at` lấy theo giá trị này. */
    public static java.time.Duration refreshTokenTtl() {
        return java.time.Duration.ofMillis(EXPIRATION_TIME_REFRESH_TOKEN);
    }

    public static Claims validateAccessToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(accessKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Token giữa luồng đăng nhập 2FA: đúng chữ ký access nhưng có claim "purpose"
     * nên JwtFilter từ chối — chỉ /auth/verify-otp đổi được nó lấy JWT thật.
     */
    public static String generate2faChallengeToken(String account) {
        return Jwts.builder()
                .setSubject(account)
                .claim("purpose", PURPOSE_2FA_CHALLENGE)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME_2FA_CHALLENGE))
                .signWith(accessKey)
                .compact();
    }

    /** @throws io.jsonwebtoken.JwtException khi token sai chữ ký hoặc hết hạn 5 phút. */
    public static Claims validate2faChallengeToken(String token) {
        Claims claims = validateAccessToken(token);
        if (!PURPOSE_2FA_CHALLENGE.equals(claims.get("purpose", String.class))) {
            throw new JwtException("Not a 2FA challenge token");
        }
        return claims;
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