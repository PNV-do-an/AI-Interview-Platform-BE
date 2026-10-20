package com.aiinterview.platform.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class JobReviewRequest {

    @NotNull(message = "Thiếu quyết định duyệt")
    private Boolean approved;

    /** Required when approved = false (rejection reason, stored as the job's reviewNote). */
    @Size(max = 500, message = "Lý do từ chối tối đa 500 ký tự")
    private String reason;
}
