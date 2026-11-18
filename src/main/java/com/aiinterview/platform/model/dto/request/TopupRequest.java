package com.aiinterview.platform.model.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TopupRequest {
    @Min(1)
    private long amount;
    @NotBlank
    private String idempotencyKey;
}
