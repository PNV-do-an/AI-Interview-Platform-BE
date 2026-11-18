package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.entity.Wallet;
import com.aiinterview.platform.model.entity.Withdrawal;
import com.aiinterview.platform.model.enums.WithdrawalStatus;
import com.aiinterview.platform.model.repository.WithdrawalRepository;
import com.aiinterview.platform.service.WalletService;
import com.aiinterview.platform.service.WithdrawalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WithdrawalServiceImpl implements WithdrawalService {

    private static final long MIN_WITHDRAWAL = 50000L;
    private static final long FEE = 5000L;

    private final WithdrawalRepository withdrawalRepository;
    private final WalletService walletService;

    @Override
    @Transactional
    public Withdrawal requestWithdrawal(User mentor, long amount, String bankName, String accountNumber, String accountHolder) {
        if (amount < MIN_WITHDRAWAL) throw new BadRequestException("So tien rut toi thieu " + MIN_WITHDRAWAL);
        long total = amount + FEE;
        Wallet w = walletService.getOrCreateWallet(mentor);
        if (w.getAvailableBalance() < total) throw new BadRequestException("So du khong du");
        // deduct immediately
        walletService.debitAvailable(mentor, total, "WITHDRAWAL", "WITHDRAWAL", null, "WD-REQ-" + System.nanoTime(),
                "Yeu cau rut tien");
        Withdrawal wd = Withdrawal.builder()
                .mentor(mentor)
                .amount(amount)
                .status(WithdrawalStatus.PENDING)
                .bankName(bankName)
                .accountNumber(accountNumber)
                .accountHolder(accountHolder)
                .build();
        return withdrawalRepository.save(wd);
    }

    @Override
    public List<Withdrawal> getMyWithdrawals(User mentor) {
        return withdrawalRepository.findByMentorOrderByCreatedAtDesc(mentor);
    }

    @Override
    public List<Withdrawal> getPending() {
        return withdrawalRepository.findByStatusOrderByCreatedAtDesc(WithdrawalStatus.PENDING);
    }

    @Override
    @Transactional
    public Withdrawal approve(Long id, String note) {
        Withdrawal wd = withdrawalRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Khong tim thay withdrawal"));
        if (wd.getStatus() != WithdrawalStatus.PENDING) throw new BadRequestException("Trang thai khong hop le");
        wd.setStatus(WithdrawalStatus.PAID);
        wd.setAdminNote(note);
        wd.setProcessedAt(java.time.LocalDateTime.now());
        return withdrawalRepository.save(wd);
    }

    @Override
    @Transactional
    public Withdrawal reject(Long id, String note) {
        Withdrawal wd = withdrawalRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Khong tim thay withdrawal"));
        if (wd.getStatus() != WithdrawalStatus.PENDING) throw new BadRequestException("Trang thai khong hop le");
        wd.setStatus(WithdrawalStatus.REJECTED);
        wd.setAdminNote(note);
        wd.setProcessedAt(java.time.LocalDateTime.now());
        // restore amount + fee
        walletService.creditAvailable(wd.getMentor(), wd.getAmount() + FEE, "WITHDRAWAL_REVERSAL", "WITHDRAWAL", wd.getId(),
                "WD-REV-" + wd.getId(), "Hoan tien rut bi tu choi");
        return withdrawalRepository.save(wd);
    }
}
