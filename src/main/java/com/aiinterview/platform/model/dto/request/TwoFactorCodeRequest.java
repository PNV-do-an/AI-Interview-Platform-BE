package com.aiinterview.platform.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** OTP 6 số hoặc recovery code (XXXXX-XXXXX) — cùng một ô nhập phía FE. */
@Data
public class TwoFactorCodeRequest {

    @NotBlank(message = "Mã không được để trống")
    @Pattern(regexp = "^([0-9]{6}|[A-Za-z0-9]{5}-[A-Za-z0-9]{5})$",
            message = "Mã phải là 6 chữ số hoặc recovery code")
    private String code;
}
