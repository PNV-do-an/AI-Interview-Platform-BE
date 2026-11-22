package com.aiinterview.platform.model.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class MomoCreateRequest {
    @Min(1)
    private long amount;
    private String orderInfo;
}
