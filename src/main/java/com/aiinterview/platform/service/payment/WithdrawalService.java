package com.aiinterview.platform.service.payment;

import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.entity.Withdrawal;

import java.util.List;

public interface WithdrawalService {
    Withdrawal requestWithdrawal(User mentor, long amount, String bankName, String accountNumber, String accountHolder);
    List<Withdrawal> getMyWithdrawals(User mentor);
    List<Withdrawal> getPending();
    Withdrawal approve(Long id, String note);
    Withdrawal reject(Long id, String note);
}
