package com.aiinterview.platform.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Body của PATCH /api/v1/admin/users/{id}/2fa — reason bắt buộc vì đây là hành động hạ bảo mật từ xa. */
@Data
public class AdminDisable2faRequest {

    @NotBlank(message = "Lý do không được để trống")
    @Size(max = 500, message = "Lý do tối đa 500 ký tự")
    private String reason;
}
