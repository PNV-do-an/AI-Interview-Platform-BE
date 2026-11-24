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
                .availableBalance(nz(w.getAvailableBalance()))
                .pendingBalance(nz(w.getPendingBalance()))
                .escrowBalance(nz(w.getEscrowBalance()))
                .build();
    }

    /** Ví cũ trên staging có thể có balance = NULL; FE luôn nhận số cho dễ xử lý. */
    private static long nz(Long value) {
        return value == null ? 0L : value;
    }
}
