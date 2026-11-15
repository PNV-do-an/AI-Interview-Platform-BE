package com.aiinterview.platform.service.auth;

import com.aiinterview.platform.model.dto.response.SsoConfigResponse;
import com.aiinterview.platform.model.entity.User;

public interface SsoAuthInterface {

    /** Config OIDC công khai (enabled=false khi chưa cấu hình IdP). */
    SsoConfigResponse getPublicConfig();

    /** Đổi OAuth code lấy id_token, verify chữ ký qua JWKS của IdP, find-or-create user. */
    User loginWithSso(String code, String redirectUri, String nonce);
}
