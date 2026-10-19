package com.aiinterview.platform.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserStatusRequest {

    @NotNull(message = "Thiếu trạng thái tài khoản (enabled)")
    private Boolean enabled;
}
