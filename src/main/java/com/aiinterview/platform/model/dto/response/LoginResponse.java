package com.aiinterview.platform.model.dto.response;

/**
 * Kết quả bước một của đăng nhập: hoặc JWT thật (accessToken != null),
 * hoặc challenge 2FA (mfaRequired=true, accessToken/refreshToken null) khi
 * tài khoản đã bật yếu tố thứ hai — FE phải nộp OTP qua /auth/verify-otp.
 */
public class LoginResponse {

    private final String accessToken;
    private final String refreshToken;
    private final String role;
    private final boolean mfaRequired;
    private final String challengeToken;

    public LoginResponse(String accessToken, String refreshToken, String role) {
        this(accessToken, refreshToken, role, false, null);
    }

    public LoginResponse(String accessToken, String refreshToken, String role,
                         boolean mfaRequired, String challengeToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.role = role;
        this.mfaRequired = mfaRequired;
        this.challengeToken = challengeToken;
    }

    public static LoginResponse mfaChallenge(String challengeToken) {
        return new LoginResponse(null, null, null, true, challengeToken);
    }

    public String getAccessToken() {return accessToken;}
    public String getRefreshToken() {return refreshToken;}
    public String getRole() {return role;}
    public boolean isMfaRequired() {return mfaRequired;}
    public String getChallengeToken() {return challengeToken;}
}
