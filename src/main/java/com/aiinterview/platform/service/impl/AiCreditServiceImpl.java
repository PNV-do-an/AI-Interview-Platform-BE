package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.model.entity.AiCreditLedger;
import com.aiinterview.platform.model.entity.AiCreditPackage;
import com.aiinterview.platform.model.entity.AiCreditUsage;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.AiCreditTxType;
import com.aiinterview.platform.model.repository.AiCreditLedgerRepository;
import com.aiinterview.platform.model.repository.AiCreditPackageRepository;
import com.aiinterview.platform.model.repository.AiCreditUsageRepository;
import com.aiinterview.platform.service.AiCreditService;
import com.aiinterview.platform.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiCreditServiceImpl implements AiCreditService {

    private final AiCreditPackageRepository packageRepository;
    private final AiCreditLedgerRepository ledgerRepository;
    private final AiCreditUsageRepository usageRepository;
    private final WalletService walletService;

    @Override
    public int getBalance(User user) {
        List<AiCreditLedger> list = ledgerRepository.findByUserOrderByCreatedAtDesc(user);
        return list.stream().mapToInt(AiCreditLedger::getAmount).sum();
    }

    @Override
    @Transactional
    public void purchasePackage(User user, Long packageId) {
        AiCreditPackage pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay goi AI"));
        if (!Boolean.TRUE.equals(pkg.getActive())) throw new BadRequestException("Goi khong con hoat dong");
        int discount = pkg.getDiscountPercent() == null ? 0 : Math.min(pkg.getDiscountPercent(), 5);
        long finalPrice = pkg.getPrice() - (pkg.getPrice() * discount / 100);
        walletService.debitAvailable(user, finalPrice, "PACKAGE_PURCHASE", "AI_PACKAGE", pkg.getId(),
                "AI-PKG-" + packageId + "-" + System.nanoTime(), "Mua goi AI " + pkg.getName());
        int balance = getBalance(user) + pkg.getCredits();
        AiCreditLedger tx = AiCreditLedger.builder()
                .user(user)
                .type(AiCreditTxType.PURCHASE)
                .amount(pkg.getCredits())
                .balanceAfter(balance)
                .referenceType("AI_PACKAGE")
                .referenceId(pkg.getId())
                .description("Mua goi " + pkg.getName())
                .build();
        ledgerRepository.save(tx);
    }

    @Override
    @Transactional
    public boolean useCredit(User user, String difficulty) {
        int balance = getBalance(user);
        if (balance <= 0) {
            // free tier: only EASY allowed without credit
            if (!"EASY".equalsIgnoreCase(difficulty)) {
                throw new BadRequestException("Can mua credit de mo cau hoi kho");
            }
            // allow free easy without deducting
            return true;
        }
        // daily limit
        LocalDate today = LocalDate.now();
        AiCreditUsage usage = usageRepository.findByUserAndUsageDate(user, today)
                .orElseGet(() -> usageRepository.save(AiCreditUsage.builder().user(user).usageDate(today).count(0).dailyLimit(20).build()));
        if (usage.getCount() >= usage.getDailyLimit()) throw new BadRequestException("Da dat han muc luot moi ngay");
        // deduct one credit
        int newBalance = balance - 1;
        AiCreditLedger tx = AiCreditLedger.builder()
                .user(user)
                .type(AiCreditTxType.USAGE)
                .amount(-1)
                .balanceAfter(newBalance)
                .referenceType("AI_INTERVIEW")
                .description("Su dung 1 luot phong van AI")
                .build();
        ledgerRepository.save(tx);
        usage.setCount(usage.getCount() + 1);
        usageRepository.save(usage);
        return true;
    }

    @Override
    public int getTodayUsage(User user) {
        return usageRepository.findByUserAndUsageDate(user, LocalDate.now())
                .map(AiCreditUsage::getCount).orElse(0);
    }
}
