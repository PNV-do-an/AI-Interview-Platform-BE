package com.aiinterview.platform.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Date;

public class JwtUtil {

    private static final String ACCESS_SECRET = "access-secret-access-secret-access-secret";
    private static final String REFRESH_SECRET = "refresh-secret-refresh-secret-refresh-secret";
    private static final SecretKey accessKey = Keys.hmacShaKeyFor(ACCESS_SECRET.getBytes());
    private static final SecretKey refreshKey = Keys.hmacShaKeyFor(REFRESH_SECRET.getBytes());
    private static final long EXPIRATION_TIME_ACCESS_TOKEN = 1000 * 60 * 60 * 24;
    private static final long EXPIRATION_TIME_REFRESH_TOKEN =  1000L * 60 * 60 * 24 * 7;

    public static String generateAccessToken(String account, String role) {

        String accessToken = Jwts.builder()
                .subject(account)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME_ACCESS_TOKEN))
                .signWith(accessKey)
                .compact();

        return accessToken;
    }

    public static String generateRefreshToken(String account, String role) {

        String refreshToken = Jwts.builder()
                .subject(account)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME_REFRESH_TOKEN))
                .signWith(refreshKey)
                .compact();

        return refreshToken;
    }

    public static Claims validateAccessToken(String token) {
        return Jwts.parser()
                .verifyWith(accessKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public static Claims validateRefreshToken(String token) {
        return Jwts.parser()
                .verifyWith(refreshKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public static String getUsername(String token) {
        return validateAccessToken(token).getSubject();
    }

    public static String getRole(String token) {
        return validateAccessToken(token).get("role", String.class);
    }
}

//
//--> Tiếp tục endpoint gọi để refresh token -> ( đặt logic kểm tra ở đâu ) -> kiểm tra
//        mỗi lần client gửi request