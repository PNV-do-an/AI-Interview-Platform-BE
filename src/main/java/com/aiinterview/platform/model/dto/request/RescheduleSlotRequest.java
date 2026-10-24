package com.aiinterview.platform.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RescheduleSlotRequest {

    @NotNull(message = "Khung giờ mới không được để trống")
    private Long slotId;
}
