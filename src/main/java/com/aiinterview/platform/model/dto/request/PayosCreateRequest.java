package com.aiinterview.platform.model.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class PayosCreateRequest {
    @Min(1)
    private long amount;
    /** Tối đa 9 ký tự để tương thích mọi tài khoản ngân hàng. */
    private String description;
}
