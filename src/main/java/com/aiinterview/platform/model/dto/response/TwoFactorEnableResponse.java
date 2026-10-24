package com.aiinterview.platform.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TwoFactorEnableResponse {
    /** Cấp duy nhất lúc bật 2FA — hiển thị một lần, BE chỉ giữ hash. */
    private List<String> recoveryCodes;
}
