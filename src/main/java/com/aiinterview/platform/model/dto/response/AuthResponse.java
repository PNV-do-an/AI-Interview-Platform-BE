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
}
}
