package com.aiinterview.platform.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpgradeAccountRequest {
    @NotBlank
    private String plan; // NORMAL / ADVANCED

    @NotBlank
    private String billingCycle; // MONTHLY / YEARLY
}
