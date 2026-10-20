package com.aiinterview.platform.model.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BulkUserActionRequest {

    @NotEmpty(message = "Danh sách id không được để trống")
    @Size(max = 100, message = "Mỗi lần chỉ xử lý tối đa 100 tài khoản")
    private List<Long> ids;

    @Size(max = 500, message = "Lý do tối đa 500 ký tự")
    private String reason;

    /** null means true — same default as the single-account lock modal. */
    private Boolean notifyUser;

    public boolean shouldNotify() {
        return !Boolean.FALSE.equals(notifyUser);
    }
}
