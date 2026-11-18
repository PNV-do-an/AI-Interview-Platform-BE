package com.aiinterview.platform.model.dto.response;

import com.aiinterview.platform.model.entity.Withdrawal;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class WithdrawalResponse {
    private Long id;
    private Long mentorId;
    private Long amount;
    private String status;
    private String bankName;
    private String accountNumber;
    private String accountHolder;
    private String adminNote;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;

    public static WithdrawalResponse from(Withdrawal w) {
        return WithdrawalResponse.builder()
                .id(w.getId())
                .mentorId(w.getMentor().getId())
                .amount(w.getAmount())
                .status(w.getStatus().name())
                .bankName(w.getBankName())
                .accountNumber(w.getAccountNumber())
                .accountHolder(w.getAccountHolder())
                .adminNote(w.getAdminNote())
                .createdAt(w.getCreatedAt())
                .processedAt(w.getProcessedAt())
                .build();
    }
}
