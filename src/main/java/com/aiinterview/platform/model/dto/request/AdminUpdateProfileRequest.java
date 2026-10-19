package com.aiinterview.platform.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * "Edit" on a member row. Every field is optional and a null value means "keep as is";
 * at least one field has to be present, which is checked in the service.
 */
@Data
public class AdminUpdateProfileRequest {

    @Size(max = 200, message = "Họ tên tối đa 200 ký tự")
    private String fullName;

    /** Changing the email invalidates the verified state — the service re-issues the invitation. */
    @Email(message = "Email không hợp lệ")
    @Size(max = 200, message = "Email tối đa 200 ký tự")
    private String email;

    @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
    private String phone;
}
