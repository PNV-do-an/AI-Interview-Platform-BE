package com.aiinterview.platform.model.dto.request;

import com.aiinterview.platform.model.enums.Role;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChangeRoleRequest {

    @NotNull(message = "Thiếu vai trò (role)")
    private Role role;
}
