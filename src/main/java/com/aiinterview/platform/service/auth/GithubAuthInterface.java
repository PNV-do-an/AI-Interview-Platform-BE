package com.aiinterview.platform.service.auth;

import com.aiinterview.platform.model.entity.User;
import jakarta.servlet.http.HttpServletRequest;

public interface GithubAuthInterface {

    /** Exchange OAuth code at GitHub, fetch profile, find-or-create user, return the user. */
    User loginWithGithub(String code, String redirectUri, HttpServletRequest request);

    /** OAuth client ID hiện cấu hình (rỗng nếu chưa bật đăng nhập GitHub). */
    String getClientId();
}
