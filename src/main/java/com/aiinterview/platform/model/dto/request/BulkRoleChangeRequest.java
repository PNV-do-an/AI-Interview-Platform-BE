package com.aiinterview.platform.model.dto.request;

import com.aiinterview.platform.model.enums.Role;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BulkRoleChangeRequest {

    @NotEmpty(message = "Danh sách id không được để trống")
    @Size(max = 100, message = "Mỗi lần chỉ xử lý tối đa 100 tài khoản")
    private List<Long> ids;

    @NotNull(message = "Vai trò mới không được để trống")
    private Role role;
}
