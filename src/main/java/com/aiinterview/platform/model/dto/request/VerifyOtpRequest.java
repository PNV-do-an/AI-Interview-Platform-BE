package com.aiinterview.platform.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Body của POST /api/v1/auth/verify-otp — bước hai luồng đăng nhập khi mfaRequired=true. */
@Data
public class VerifyOtpRequest {

    @NotBlank(message = "challengeToken không được để trống")
    private String challengeToken;

    @NotBlank(message = "Mã không được để trống")
    private String code;
}
