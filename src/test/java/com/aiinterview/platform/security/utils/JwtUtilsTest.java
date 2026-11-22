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
 *
 * <p>Mỗi test chia 3 khối comment: <b>Arrange</b> → <b>Act</b> → <b>Assert</b>.</p>
 */
class JwtUtilsTest {

    private static final String EMAIL = "candidate@example.com";
    private static final String ROLE = "ROLE_EMPLOYER";
    private static final String SID = "9d0d5c00-0000-4000-8000-000000000009";

    @Test
    @DisplayName("Doi access token moi, giu nguyen refresh token va role")
    void refreshAccessToken_mintsAccessTokenKeepsRefreshToken() {
        // Arrange: nguoi dung dang co phien SID hop le, tv hien hanh = 0
        String refreshToken = JwtUtil.generateRefreshToken(EMAIL, ROLE, 0, SID);

        // Act: doi ra cap token moi
        LoginResponse response = JwtUtils.refreshAccessToken(refreshToken, 0, SID);

        // Assert: refresh token duoc tra lai nguyen ven (khong rotate) + role + mfaRequired=false
        assertThat(response.getRefreshToken()).isEqualTo(refreshToken);
        assertThat(response.getRole()).isEqualTo(ROLE);
        assertThat(response.isMfaRequired()).isFalse();

        // Assert: access token moi doc lai phai mang du subject / role / sid
        Claims claims = JwtUtil.validateAccessToken(response.getAccessToken());
        assertThat(claims.getSubject()).isEqualTo(EMAIL);
        assertThat(claims.get("role", String.class)).isEqualTo(ROLE);
        assertThat(claims.get("sid", String.class)).isEqualTo(SID);
    }

    @Test
    @DisplayName("tv cua access token moi lay tu users hien hanh, khong lay tu claim cu")
    void refreshAccessToken_usesCallerTokenVersionNotStaleClaim() {
        // Arrange: refresh token phat ra khi tv=1, nhung DB hien tai da la tv=5
        String refreshToken = JwtUtil.generateRefreshToken(EMAIL, ROLE, 1, SID);

        // Act: noi goi truyen tv hien hanh (5) chu khong phai tv nam trong claim (1)
        LoginResponse response = JwtUtils.refreshAccessToken(refreshToken, 5, SID);

        // Assert: access token moi mang tv=5 => doi mat khau cu se bi dao thai ngay
        Claims claims = JwtUtil.validateAccessToken(response.getAccessToken());
        assertThat(claims.get("tv", Integer.class)).isEqualTo(5);

        // Assert: refresh token giu tv cu (1) vi khong duoc phat lai
        assertThat(JwtUtil.validateRefreshToken(refreshToken).get("tv", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("sid null thi access token moi khong co claim sid")
    void refreshAccessToken_withNullSession_leavesSidAbsent() {
        // Arrange: refresh token phat truoc wave 010 (khong co phien)
        String refreshToken = JwtUtil.generateRefreshToken(EMAIL, ROLE, 0, null);

        // Act
        LoginResponse response = JwtUtils.refreshAccessToken(refreshToken, 0, null);

        // Assert: claim sid phai VANG MAT (khong phai chuoi "null"), de isLive xu ly dung
        assertThat(JwtUtil.validateAccessToken(response.getAccessToken()).containsKey("sid")).isFalse();
    }

    @Test
    @DisplayName("Access token khong duoc dung o buoc refresh")
    void refreshAccessToken_rejectsAccessToken() {
        // Arrange: token that nhung la access token (sai secret de refresh)
        String accessToken = JwtUtil.generateAccessToken(EMAIL, ROLE, 0, SID);

        // Act & Assert: refresh validator khong doc duoc => JwtException
        assertThatThrownBy(() -> JwtUtils.refreshAccessToken(accessToken, 0, SID))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Refresh token het han / bi sua deu bi tu choi")
    void refreshAccessToken_rejectsMalformedToken() {
        // Arrange: chuoi khong phai JWT (tuong duong token bi sua / hong)

        // Act & Assert: phai nem JwtException (khong duoc tra ve cap token moi)
        assertThatThrownBy(() -> JwtUtils.refreshAccessToken("broken", 0, SID))
                .isInstanceOf(JwtException.class);
    }
}
