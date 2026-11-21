package com.aiinterview.platform.model.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BookingRequest {
    @NotNull
    private Long slotId;
    @Min(1)
    private long amount;
    private String idempotencyKey;
}
