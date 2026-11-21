package com.aiinterview.platform.model.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WithdrawalRequest {
    @Min(1)
    private long amount;
    @NotBlank
    private String bankName;
    @NotBlank
    private String accountNumber;
    @NotBlank
    private String accountHolder;
}
