package com.aiinterview.platform.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private UserInfo user;
    /** true = mật khẩu đúng nhưng còn chặn bước hai; accessToken/refreshToken sẽ null. */
    private boolean mfaRequired;
    /** Token sống 5 phút, chỉ đổi được lấy JWT qua POST /auth/verify-otp. */
    private String challengeToken;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private Long id;
        private String email;
        private String fullName;
        private String phone;
        private String role;
        private boolean twoFactorEnabled;
    }
}
