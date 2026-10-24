package com.aiinterview.platform.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Secret chỉ tồn tại ở bước setup — bị thay khi user gọi setup lại hoặc enable. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TwoFactorSetupResponse {
    private String secret;
    /** otpauth:// URI — FE tự render QR từ chuỗi này. */
    private String otpauthUri;
    private String accountName;
    private String issuer;
}
