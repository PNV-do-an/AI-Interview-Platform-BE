package com.aiinterview.platform.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Key;
import java.time.Duration;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Token factory for the whole auth stack. The two secrets are different on purpose: an access token
 * must not be accepted where a refresh token is expected, and vice versa. The 2FA challenge token is
 * signed with the access key but carries "purpose" — JwtFilter refuses it, only /auth/verify-otp
 * exchanges it for a real pair.
 *
 * <p>TTL constants are private, so expired-token cases build the token here with the same key read
 * from the class (ReflectionTestUtils) instead of waiting 24h.</p>
 */
class JwtUtilTest {

    private static final String EMAIL = "candidate@example.com";
    private static final String ROLE = "ROLE_USER";
    private static final String SID = "1f0f4d2e-0000-4000-8000-000000000001";

    @Test
    @DisplayName("Access token mang du subject, role, tv va sid")
    void accessToken_carriesSubjectRoleVersionAndSession() {
        String token = JwtUtil.generateAccessToken(EMAIL, ROLE, 3, SID);

        Claims claims = JwtUtil.validateAccessToken(token);

        assertThat(claims.getSubject()).isEqualTo(EMAIL);
        assertThat(claims.get("role", String.class)).isEqualTo(ROLE);
        assertThat(claims.get("tv", Integer.class)).isEqualTo(3);
        assertThat(claims.get("sid", String.class)).isEqualTo(SID);
        assertThat(claims.get("purpose", String.class)).isNull();
        assertThat(claims.getExpiration()).isAfter(new Date());
    }

    @Test
    @DisplayName("Token phat truoc wave 010 khong co sid thi claim sid vang mat, khong phai null string")
    void accessToken_withoutSession_leavesSidClaimAbsent() {
        String token = JwtUtil.generateAccessToken(EMAIL, ROLE, 0, null);

        Claims claims = JwtUtil.validateAccessToken(token);

        assertThat(claims.containsKey("sid")).isFalse();
        assertThat(claims.get("sid", String.class)).isNull();
    }

    @Test
    @DisplayName("Refresh token mang cung bo claim nhung ky bang secret khac")
    void refreshToken_carriesSubjectRoleVersionAndSession() {
        String token = JwtUtil.generateRefreshToken(EMAIL, ROLE, 7, SID);

        Claims claims = JwtUtil.validateRefreshToken(token);

        assertThat(claims.getSubject()).isEqualTo(EMAIL);
        assertThat(claims.get("role", String.class)).isEqualTo(ROLE);
        assertThat(claims.get("tv", Integer.class)).isEqualTo(7);
        assertThat(claims.get("sid", String.class)).isEqualTo(SID);
    }

    @Test
    @DisplayName("Access token khong duoc dung lam refresh token")
    void accessToken_isRejectedByRefreshValidator() {
        String accessToken = JwtUtil.generateAccessToken(EMAIL, ROLE, 0, SID);

        assertThatThrownBy(() -> JwtUtil.validateRefreshToken(accessToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Refresh token khong duoc dung lam access token")
    void refreshToken_isRejectedByAccessValidator() {
        String refreshToken = JwtUtil.generateRefreshToken(EMAIL, ROLE, 0, SID);

        assertThatThrownBy(() -> JwtUtil.validateAccessToken(refreshToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Phien song dung bang TTL refresh token: 7 ngay")
    void refreshTokenTtl_isSevenDays() {
        assertThat(JwtUtil.refreshTokenTtl()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    @DisplayName("Challenge 2FA chi qua duoc validate2faChallengeToken")
    void twoFactorChallenge_roundTripsThroughChallengeValidator() {
        String token = JwtUtil.generate2faChallengeToken(EMAIL);

        Claims claims = JwtUtil.validate2faChallengeToken(token);

        assertThat(claims.getSubject()).isEqualTo(EMAIL);
        assertThat(claims.get("purpose", String.class)).isEqualTo(JwtUtil.PURPOSE_2FA_CHALLENGE);
    }

    @Test
    @DisplayName("Challenge 2FA ky bang access key nen JwtFilter phai tu choi dua vao claim purpose")
    void twoFactorChallenge_isSignedWithAccessKey_soPurposeClaimIsTheOnlyGuard() {
        String token = JwtUtil.generate2faChallengeToken(EMAIL);

        Claims claims = JwtUtil.validateAccessToken(token);

        // Chu ky hop le, nhung "purpose" khac null => JwtFilter coi la chua dang nhap.
        assertThat(claims.get("purpose", String.class)).isNotNull();
        assertThat(claims.get("sid", String.class)).isNull();
        assertThat(claims.get("tv", Integer.class)).isNull();
    }

    @Test
    @DisplayName("Access token khong duoc dung thay challenge 2FA")
    void accessToken_isRejectedByChallengeValidator() {
        String accessToken = JwtUtil.generateAccessToken(EMAIL, ROLE, 0, SID);

        assertThatThrownBy(() -> JwtUtil.validate2faChallengeToken(accessToken))
                .isInstanceOf(JwtException.class)
                .hasMessage("Not a 2FA challenge token");
    }

    @Test
    @DisplayName("Access token het han bi tu choi voi ExpiredJwtException")
    void expiredAccessToken_isRejected() {
        String expired = expiredToken(accessKey(), false);

        assertThatThrownBy(() -> JwtUtil.validateAccessToken(expired))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("Challenge 2FA het han sau 5 phut")
    void expiredTwoFactorChallenge_isRejected() {
        String expired = expiredToken(accessKey(), true);

        assertThatThrownBy(() -> JwtUtil.validate2faChallengeToken(expired))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("Token bi sua payload thi sai chu ky va bi tu choi")
    void tamperedToken_isRejected() {
        String token = JwtUtil.generateAccessToken(EMAIL, ROLE, 0, SID);
        String[] parts = token.split("\\.");
        // Doi mot ky tu trong payload: van la base64url hop le, nhung chu ky khong con khop.
        char first = parts[1].charAt(0);
        parts[1] = (first == 'e' ? 'f' : 'e') + parts[1].substring(1);
        String tampered = String.join(".", parts);

        assertThatThrownBy(() -> JwtUtil.validateAccessToken(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Chuoi rong bi tu choi ngay tu dau vao")
    void emptyToken_isRejected() {
        assertThatThrownBy(() -> JwtUtil.validateAccessToken(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Chuoi khong phai JWT bi tu choi")
    void malformedToken_isRejected() {
        assertThatThrownBy(() -> JwtUtil.validateAccessToken("not-a-jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("getUsername / getRole doc dung claim cua access token")
    void getUsernameAndRole_readAccessTokenClaims() {
        String token = JwtUtil.generateAccessToken(EMAIL, ROLE, 2, SID);

        assertThat(JwtUtil.getUsername(token)).isEqualTo(EMAIL);
        assertThat(JwtUtil.getRole(token)).isEqualTo(ROLE);
    }

    @Test
    @DisplayName("getUsername tren refresh token bi tu choi vi sai secret")
    void getUsername_onRefreshToken_isRejected() {
        String refreshToken = JwtUtil.generateRefreshToken(EMAIL, ROLE, 0, SID);

        assertThatThrownBy(() -> JwtUtil.getUsername(refreshToken))
                .isInstanceOf(JwtException.class);
    }

    private static Key accessKey() {
        return (Key) ReflectionTestUtils.getField(JwtUtil.class, "accessKey");
    }

    private static String expiredToken(Key key, boolean withPurpose) {
        long now = System.currentTimeMillis();
        JwtBuilder builder = Jwts.builder()
                .setSubject(EMAIL)
                .setIssuedAt(new Date(now - 60_000))
                .setExpiration(new Date(now - 1_000))
                .signWith(key);
        if (withPurpose) {
            builder.claim("purpose", JwtUtil.PURPOSE_2FA_CHALLENGE);
        }
        return builder.compact();
    }
}
