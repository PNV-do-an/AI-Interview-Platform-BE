package com.aiinterview.platform.model.dto.request;

import lombok.Data;

/**
 * Optional body of PATCH /api/v1/admin/users/{id}/lock. A call without a body keeps the
 * pre-008 behaviour (lock + notification email always sent).
 */
@Data
public class LockAccountRequest {

    @jakarta.validation.constraints.Size(max = 500, message = "Lý do tối đa 500 ký tự")
    private String reason;

    /** null means true, matching the always-checked checkbox in the lock modal. */
    private Boolean notifyUser;

    public boolean shouldNotify() {
        return !Boolean.FALSE.equals(notifyUser);
    }
}
