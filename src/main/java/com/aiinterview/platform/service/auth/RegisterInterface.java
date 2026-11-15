package com.aiinterview.platform.service.auth;

import com.aiinterview.platform.model.dto.response.RegisterResponse;
import jakarta.servlet.http.HttpServletRequest;

public interface RegisterInterface {

    RegisterResponse register(String email, String password, String fullName, String phone,
                              HttpServletRequest request);

    String verifyEmail(String token);

    void resendVerification(String email);
}
