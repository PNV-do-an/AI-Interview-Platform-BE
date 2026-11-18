package com.aiinterview.platform.model.dto.response;

import com.aiinterview.platform.model.entity.Wallet;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WalletResponse {
    private Long availableBalance;
    private Long pendingBalance;
    private Long escrowBalance;

    public static WalletResponse from(Wallet w) {
        return WalletResponse.builder()
                .availableBalance(w.getAvailableBalance())
                .pendingBalance(w.getPendingBalance())
                .escrowBalance(w.getEscrowBalance())
                .build();
    }
}
