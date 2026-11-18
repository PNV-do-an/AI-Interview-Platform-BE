package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.model.repository.EscrowBookingRepository;
import com.aiinterview.platform.model.repository.WalletTransactionRepository;
import com.aiinterview.platform.model.repository.WithdrawalRepository;
import com.aiinterview.platform.model.repository.AiCreditLedgerRepository;
import com.aiinterview.platform.model.enums.WalletTxType;
import com.aiinterview.platform.model.enums.AiCreditTxType;
import com.aiinterview.platform.service.PaymentMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentMetricsServiceImpl implements PaymentMetricsService {

    private final WalletTransactionRepository walletTxRepo;
    private final EscrowBookingRepository escrowRepo;
    private final WithdrawalRepository withdrawalRepo;
    private final AiCreditLedgerRepository aiLedgerRepo;

    @Override
    public Map<String, Object> getMetrics() {
        Map<String, Object> m = new HashMap<>();
        long totalTopup = walletTxRepo.findAll().stream()
                .filter(t -> t.getType() == WalletTxType.TOPUP)
                .mapToLong(t -> t.getAmount()).sum();
        long gmvMentor = escrowRepo.findAll().stream().mapToLong(e -> e.getAmount()).sum();
        long commission = escrowRepo.findAll().stream().mapToLong(e -> e.getCommissionAmount()).sum();
        long withdrawalsPending = withdrawalRepo.findAll().stream().filter(w -> w.getStatus().name().equals("PENDING")).count();
        long aiCreditsSold = aiLedgerRepo.findAll().stream()
                .filter(t -> t.getType() == AiCreditTxType.PURCHASE)
                .mapToInt(t -> t.getAmount()).sum();
        m.put("totalTopupVnd", totalTopup);
        m.put("mentorGmvVnd", gmvMentor);
        m.put("platformCommissionVnd", commission);
        m.put("takeRate", gmvMentor > 0 ? (double) commission / gmvMentor : 0.0);
        m.put("withdrawalsPending", withdrawalsPending);
        m.put("aiCreditsSold", aiCreditsSold);
        m.put("totalEscrowBookings", escrowRepo.count());
        return m;
    }
}
