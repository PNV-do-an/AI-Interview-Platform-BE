package com.aiinterview.platform.security.utils;

import com.aiinterview.platform.model.dto.response.LoginResponse;
import com.aiinterview.platform.security.jwt.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Refresh step: only the access token is re-minted. Two details matter and are asserted below —
 * the new token carries the caller's current tokenVersion (not the stale claim), and the refresh
 * token itself is handed back unchanged because the repo has no rotation.
 */
class JwtUtilsTest {

    private static final String EMAIL = "candidate@example.com";
    private static final String ROLE = "ROLE_EMPLOYER";
    private static final String SID = "9d0d5c00-0000-4000-8000-000000000009";

    @Test
    @DisplayName("Doi access token moi, giu nguyen refresh token va role")
    void refreshAccessToken_mintsAccessTokenKeepsRefreshToken() {
        String refreshToken = JwtUtil.generateRefreshToken(EMAIL, ROLE, 0, SID);

        LoginResponse response = JwtUtils.refreshAccessToken(refreshToken, 0, SID);

        assertThat(response.getRefreshToken()).isEqualTo(refreshToken);
        assertThat(response.getRole()).isEqualTo(ROLE);
        assertThat(response.isMfaRequired()).isFalse();
        Claims claims = JwtUtil.validateAccessToken(response.getAccessToken());
        assertThat(claims.getSubject()).isEqualTo(EMAIL);
        assertThat(claims.get("role", String.class)).isEqualTo(ROLE);
        assertThat(claims.get("sid", String.class)).isEqualTo(SID);
    }

    @Test
    @DisplayName("tv cua access token moi lay tu users hien hanh, khong lay tu claim cu")
    void refreshAccessToken_usesCallerTokenVersionNotStaleClaim() {
        String refreshToken = JwtUtil.generateRefreshToken(EMAIL, ROLE, 1, SID);

        LoginResponse response = JwtUtils.refreshAccessToken(refreshToken, 5, SID);

        Claims claims = JwtUtil.validateAccessToken(response.getAccessToken());
        assertThat(claims.get("tv", Integer.class)).isEqualTo(5);
        assertThat(JwtUtil.validateRefreshToken(refreshToken).get("tv", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("sid null thi access token moi khong co claim sid")
    void refreshAccessToken_withNullSession_leavesSidAbsent() {
        String refreshToken = JwtUtil.generateRefreshToken(EMAIL, ROLE, 0, null);

        LoginResponse response = JwtUtils.refreshAccessToken(refreshToken, 0, null);

        assertThat(JwtUtil.validateAccessToken(response.getAccessToken()).containsKey("sid")).isFalse();
    }

    @Test
    @DisplayName("Access token khong duoc dung o buoc refresh")
    void refreshAccessToken_rejectsAccessToken() {
        String accessToken = JwtUtil.generateAccessToken(EMAIL, ROLE, 0, SID);

        assertThatThrownBy(() -> JwtUtils.refreshAccessToken(accessToken, 0, SID))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Refresh token het han / bi sua deu bi tu choi")
    void refreshAccessToken_rejectsMalformedToken() {
        assertThatThrownBy(() -> JwtUtils.refreshAccessToken("broken", 0, SID))
                .isInstanceOf(JwtException.class);
    }
}
