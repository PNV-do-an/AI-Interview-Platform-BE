package com.aiinterview.platform.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    // Rule độ mạnh mật khẩu đặt ở RegisterInterfaceImpl: nếu chặn ở đây thì request không xuống
    // service, mọi thất bại đều vô hình nên register_attempts không bao giờ tích lũy đủ ngưỡng.
    @NotBlank(message = "Password is required")
    private String password;

    private String phone;
}
