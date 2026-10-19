package com.aiinterview.platform.model.dto.request;

import com.aiinterview.platform.model.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * "Add User" / "Invite Members". There is deliberately no {@code password} field: an invited
 * account gets an unusable random password and the person sets their own through the link in the
 * invitation email (Clarify C-4 — the backend never sees the member's password).
 */
@Data
public class AdminInviteUserRequest {

    @NotBlank(message = "Thiếu họ tên (fullName)")
    @Size(max = 200, message = "Họ tên tối đa 200 ký tự")
    private String fullName;

    @NotBlank(message = "Thiếu email")
    @Email(message = "Email không hợp lệ")
    @Size(max = 200, message = "Email tối đa 200 ký tự")
    private String email;

    @NotNull(message = "Thiếu vai trò (role)")
    private Role role;

    @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
    private String phone;
}
