package com.aiinterview.platform.service.auth;

import com.aiinterview.platform.model.entity.User;
import jakarta.servlet.http.HttpServletRequest;

public interface GoogleAuthInterface {

    /** Verify Google idToken, find-or-create user, return the user. */
    User loginWithGoogle(String idToken, HttpServletRequest request);

    /** OAuth client ID hiện cấu hình (rỗng nếu chưa bật đăng nhập Google). */
    String getClientId();
}
