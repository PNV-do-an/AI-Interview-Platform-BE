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
 *
 * <p>Mỗi test chia 3 khối comment: <b>Arrange</b> → <b>Act</b> → <b>Assert</b>.</p>
 */
class JwtUtilTest {

    private static final String EMAIL = "candidate@example.com";
    private static final String ROLE = "ROLE_USER";
    private static final String SID = "1f0f4d2e-0000-4000-8000-000000000001";

    @Test
    @DisplayName("Access token mang du subject, role, tv va sid")
    void accessToken_carriesSubjectRoleVersionAndSession() {
        // Arrange: user co tv=3 va dang trong session SID
        // Act: mint access token roi validate lai (di qua ca ky va doc)
        String token = JwtUtil.generateAccessToken(EMAIL, ROLE, 3, SID);

        // Assert: claim dung ten + dung kieu; "purpose" phai vang mat (day la token that su)
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
        // Arrange: sinh token voi sid = null (mo phong token doi cu)
        String token = JwtUtil.generateAccessToken(EMAIL, ROLE, 0, null);

        // Act
        Claims claims = JwtUtil.validateAccessToken(token);

        // Assert: claim phai VANG MAT (containsKey=false), khong duoc ghi "null" thanh chuoi —
        // vi `''`.isBlank() se khac `null` va lam sai logic isLive
        assertThat(claims.containsKey("sid")).isFalse();
        assertThat(claims.get("sid", String.class)).isNull();
    }

    @Test
    @DisplayName("Refresh token mang cung bo claim nhung ky bang secret khac")
    void refreshToken_carriesSubjectRoleVersionAndSession() {
        // Arrange: tv=7 de phan biet voi access token trong cac test khac
        // Act
        String token = JwtUtil.generateRefreshToken(EMAIL, ROLE, 7, SID);

        // Assert: cung bo claim voi access token, chi khac secret dung de ky
        Claims claims = JwtUtil.validateRefreshToken(token);

        assertThat(claims.getSubject()).isEqualTo(EMAIL);
        assertThat(claims.get("role", String.class)).isEqualTo(ROLE);
        assertThat(claims.get("tv", Integer.class)).isEqualTo(7);
        assertThat(claims.get("sid", String.class)).isEqualTo(SID);
    }

    @Test
    @DisplayName("Access token khong duoc dung lam refresh token")
    void accessToken_isRejectedByRefreshValidator() {
        // Arrange: token that nhung la access token
        String accessToken = JwtUtil.generateAccessToken(EMAIL, ROLE, 0, SID);

        // Act & Assert: refresh validator ky bang secret khac nen khong the doc duoc
        assertThatThrownBy(() -> JwtUtil.validateRefreshToken(accessToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Refresh token khong duoc dung lam access token")
    void refreshToken_isRejectedByAccessValidator() {
        // Arrange: token that nhung la refresh token
        String refreshToken = JwtUtil.generateRefreshToken(EMAIL, ROLE, 0, SID);

        // Act & Assert: chieu nguoc lai cung phai bi tu choi (2 secret khac nhau)
        assertThatThrownBy(() -> JwtUtil.validateAccessToken(refreshToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Phien song dung bang TTL refresh token: 7 ngay")
    void refreshTokenTtl_isSevenDays() {
        // Arrange: khong can dung gi, day la hang so

        // Act & Assert: khoang song cua session = TTL refresh token; doi so nay la doi han
        // ma nguoi dung duoc giu dang nhap
        assertThat(JwtUtil.refreshTokenTtl()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    @DisplayName("Challenge 2FA chi qua duoc validate2faChallengeToken")
    void twoFactorChallenge_roundTripsThroughChallengeValidator() {
        // Arrange: chi co email (chua xac thuc duoc gi)
        // Act
        String token = JwtUtil.generate2faChallengeToken(EMAIL);

        // Assert: validator dung cho challenge doc duoc, va co claim purpose
        Claims claims = JwtUtil.validate2faChallengeToken(token);

        assertThat(claims.getSubject()).isEqualTo(EMAIL);
        assertThat(claims.get("purpose", String.class)).isEqualTo(JwtUtil.PURPOSE_2FA_CHALLENGE);
    }

    @Test
    @DisplayName("Challenge 2FA ky bang access key nen JwtFilter phai tu choi dua vao claim purpose")
    void twoFactorChallenge_isSignedWithAccessKey_soPurposeClaimIsTheOnlyGuard() {
        // Arrange: challenge 2FA dung chung secret voi access token
        // Act: validate bang access validator (giong JwtFilter lam)
        String token = JwtUtil.generate2faChallengeToken(EMAIL);

        // Assert: chu ky hop le nen validate khong nem loi, vi vay toan bo viec chan nam o
        // claim purpose; challenge cung khong co sid/tv de khong bi coi la phien that
        Claims claims = JwtUtil.validateAccessToken(token);

        // Chu ky hop le, nhung "purpose" khac null => JwtFilter coi la chua dang nhap.
        assertThat(claims.get("purpose", String.class)).isNotNull();
        assertThat(claims.get("sid", String.class)).isNull();
        assertThat(claims.get("tv", Integer.class)).isNull();
    }

    @Test
    @DisplayName("Access token khong duoc dung thay challenge 2FA")
    void accessToken_isRejectedByChallengeValidator() {
        // Arrange: access token that (khong co purpose)
        String accessToken = JwtUtil.generateAccessToken(EMAIL, ROLE, 0, SID);

        // Act & Assert: message rieng de phan biet voi token het han
        assertThatThrownBy(() -> JwtUtil.validate2faChallengeToken(accessToken))
                .isInstanceOf(JwtException.class)
                .hasMessage("Not a 2FA challenge token");
    }

    @Test
    @DisplayName("Access token het han bi tu choi voi ExpiredJwtException")
    void expiredAccessToken_isRejected() {
        // Arrange: mint token het han tai cho bang chinh accessKey doc qua reflection
        // (khong the cho 24h, cung khong mock static)
        String expired = expiredToken(accessKey(), false);

        // Act & Assert: ExpiredJwtException la loai rieng de con phan biet voi sai chu ky
        assertThatThrownBy(() -> JwtUtil.validateAccessToken(expired))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("Challenge 2FA het han sau 5 phut")
    void expiredTwoFactorChallenge_isRejected() {
        // Arrange: token het han nhung co claim purpose (dung hinh dang challenge)
        String expired = expiredToken(accessKey(), true);

        // Act & Assert: van la ExpiredJwtException, tuc TTL 5 phut duoc thu thi qua exp
        assertThatThrownBy(() -> JwtUtil.validate2faChallengeToken(expired))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("Token bi sua payload thi sai chu ky va bi tu choi")
    void tamperedToken_isRejected() {
        // Arrange: doi 1 ky tu trong phan payload, giu nguyen base64url hop le
        String token = JwtUtil.generateAccessToken(EMAIL, ROLE, 0, SID);
        String[] parts = token.split("\\.");
        // Doi mot ky tu trong payload: van la base64url hop le, nhung chu ky khong con khop.
        char first = parts[1].charAt(0);
        parts[1] = (first == 'e' ? 'f' : 'e') + parts[1].substring(1);
        String tampered = String.join(".", parts);

        // Act & Assert: sai chu ky (SignatureException, la con cua JwtException)
        assertThatThrownBy(() -> JwtUtil.validateAccessToken(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Chuoi rong bi tu choi ngay tu dau vao")
    void emptyToken_isRejected() {
        // Arrange: input rong

        // Act & Assert: loi tham so (IllegalArgumentException) chu khong phai JwtException,
        // vi thu vien tu choi truoc khi parse
        assertThatThrownBy(() -> JwtUtil.validateAccessToken(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Chuoi khong phai JWT bi tu choi")
    void malformedToken_isRejected() {
        // Arrange: chuoi khong co 3 phan cach nhau bang dau cham

        // Act & Assert: MalformedJwtException -> JwtException, khong lo 500
        assertThatThrownBy(() -> JwtUtil.validateAccessToken("not-a-jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("getUsername / getRole doc dung claim cua access token")
    void getUsernameAndRole_readAccessTokenClaims() {
        // Arrange: token hop le voi tv=2
        String token = JwtUtil.generateAccessToken(EMAIL, ROLE, 2, SID);

        // Act & Assert: 2 helper tien ich phai doc dung claim tuong ung
        assertThat(JwtUtil.getUsername(token)).isEqualTo(EMAIL);
        assertThat(JwtUtil.getRole(token)).isEqualTo(ROLE);
    }

    @Test
    @DisplayName("getUsername tren refresh token bi tu choi vi sai secret")
    void getUsername_onRefreshToken_isRejected() {
        // Arrange: refresh token that
        String refreshToken = JwtUtil.generateRefreshToken(EMAIL, ROLE, 0, SID);

        // Act & Assert: getUsername dung access validator nen khong doc duoc refresh token
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
