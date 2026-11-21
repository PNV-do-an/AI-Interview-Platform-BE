package com.aiinterview.platform.service.auth;

import com.aiinterview.platform.model.dto.response.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;

public interface LoginInterface {

    LoginResponse login(String email, String password, HttpServletRequest request);
}
