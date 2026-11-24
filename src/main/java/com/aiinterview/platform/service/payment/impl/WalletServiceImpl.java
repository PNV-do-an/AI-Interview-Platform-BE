package com.aiinterview.platform.service.payment.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.entity.Wallet;
import com.aiinterview.platform.model.entity.WalletTransaction;
import com.aiinterview.platform.model.enums.WalletTxType;
import com.aiinterview.platform.model.repository.WalletRepository;
import com.aiinterview.platform.model.repository.WalletTransactionRepository;
import com.aiinterview.platform.service.payment.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository txRepository;

    @Override
    @Transactional
    public Wallet getOrCreateWallet(User user) {
        return walletRepository.findByUser(user).orElseGet(() -> {
            Wallet w = Wallet.builder().user(user).build();
            return walletRepository.save(w);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Wallet getWallet(User user) {
        return walletRepository.findByUser(user).orElseGet(() -> Wallet.builder().user(user).availableBalance(0L).pendingBalance(0L).escrowBalance(0L).build());
    }

    @Override
    @Transactional
    public WalletTransaction topUp(User user, long amount, String idempotencyKey, String description) {
        if (amount <= 0) throw new BadRequestException("Số tiền nạp phải lớn hơn 0");
        if (idempotencyKey != null) {
            var existing = txRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) return existing.get();
        }
        Wallet wallet = walletRepository.findByUserForUpdate(user).orElseGet(() -> walletRepository.save(Wallet.builder().user(user).build()));
        long newBalance = nz(wallet.getAvailableBalance()) + amount;
        wallet.setAvailableBalance(newBalance);
        walletRepository.save(wallet);
        WalletTransaction tx = WalletTransaction.builder()
                .user(user)
                .type(WalletTxType.TOPUP)
                .amount(amount)
                .balanceAfter(newBalance)
                .referenceType("TOPUP")
                .idempotencyKey(idempotencyKey)
                .description(description == null ? "Nạp tiền" : description)
                .build();
        return txRepository.save(tx);
    }

    @Override
    @Transactional
    public void debitAvailable(User user, long amount, String type, String refType, Long refId, String idempotencyKey, String description) {
        if (amount <= 0) throw new BadRequestException("Số tiền phải lớn hơn 0");
        if (idempotencyKey != null && txRepository.existsByIdempotencyKey(idempotencyKey)) return;
        Wallet wallet = walletRepository.findByUserForUpdate(user).orElseGet(() -> walletRepository.save(Wallet.builder().user(user).build()));
        long available = nz(wallet.getAvailableBalance());
        if (available < amount) throw new BadRequestException("Số dư không đủ");
        long newBalance = available - amount;
        wallet.setAvailableBalance(newBalance);
        walletRepository.save(wallet);
        WalletTransaction tx = WalletTransaction.builder()
                .user(user)
                .type(WalletTxType.valueOf(type))
                .amount(-amount)
                .balanceAfter(newBalance)
                .referenceType(refType)
                .referenceId(refId)
                .idempotencyKey(idempotencyKey)
                .description(description)
                .build();
        txRepository.save(tx);
    }

    @Override
    @Transactional
    public void creditAvailable(User user, long amount, String type, String refType, Long refId, String idempotencyKey, String description) {
        if (amount <= 0) return;
        if (idempotencyKey != null && txRepository.existsByIdempotencyKey(idempotencyKey)) return;
        Wallet wallet = walletRepository.findByUserForUpdate(user).orElseGet(() -> walletRepository.save(Wallet.builder().user(user).build()));
        // Bản ghi ví cũ có thể có available_balance = NULL dù @Column(nullable=false)
        // (ddl-auto=update không backfill). Unboxing trực tiếp ở đây từng gây NPE -> 500.
        long newBalance = nz(wallet.getAvailableBalance()) + amount;
        wallet.setAvailableBalance(newBalance);
        walletRepository.save(wallet);
        WalletTransaction tx = WalletTransaction.builder()
                .user(user)
                .type(WalletTxType.valueOf(type))
                .amount(amount)
                .balanceAfter(newBalance)
                .referenceType(refType)
                .referenceId(refId)
                .idempotencyKey(idempotencyKey)
                .description(description)
                .build();
        txRepository.save(tx);
    }

    @Override
    @Transactional
    public void holdEscrow(User user, long amount) {
        Wallet wallet = walletRepository.findByUserForUpdate(user).orElseGet(() -> walletRepository.save(Wallet.builder().user(user).build()));
        long available = nz(wallet.getAvailableBalance());
        if (available < amount) throw new BadRequestException("Số dư không đủ");
        long newAvailable = available - amount;
        long newEscrow = nz(wallet.getEscrowBalance()) + amount;
        wallet.setAvailableBalance(newAvailable);
        wallet.setEscrowBalance(newEscrow);
        walletRepository.save(wallet);
        WalletTransaction tx = WalletTransaction.builder()
                .user(user)
                .type(WalletTxType.BOOKING_HOLD)
                .amount(-amount)
                .balanceAfter(newAvailable)
                .referenceType("ESCROW_BOOKING")
                .description("Giu tien escrow dat lich mentor")
                .build();
        txRepository.save(tx);
    }

    @Override
    @Transactional
    public void releaseEscrowHold(User user, long amount) {
        Wallet wallet = walletRepository.findByUserForUpdate(user).orElseGet(() -> walletRepository.save(Wallet.builder().user(user).build()));
        wallet.setEscrowBalance(Math.max(0L, nz(wallet.getEscrowBalance()) - amount));
        walletRepository.save(wallet);
    }

    /**
     * ddl-auto=update không backfill dữ liệu cũ: một số ví trên staging có
     * available_balance/escrow_balance = NULL dù entity khai báo nullable=false.
     * Unboxing trực tiếp (wallet.getAvailableBalance() + amount) ném NPE và lan lên
     * /api/v1/payment/payos/return thành 500 ngay sau khi payOS báo PAID.
     */
    private static long nz(Long value) {
        return value == null ? 0L : value;
    }
}
