package com.aiinterview.platform.service.payment;

import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.entity.Wallet;
import com.aiinterview.platform.model.entity.WalletTransaction;

public interface WalletService {
    Wallet getOrCreateWallet(User user);
    Wallet getWallet(User user);
    WalletTransaction topUp(User user, long amount, String idempotencyKey, String description);
    void debitAvailable(User user, long amount, String type, String refType, Long refId, String idempotencyKey, String description);
    void creditAvailable(User user, long amount, String type, String refType, Long refId, String idempotencyKey, String description);
    void holdEscrow(User user, long amount);
    void releaseEscrowHold(User user, long amount);
}
