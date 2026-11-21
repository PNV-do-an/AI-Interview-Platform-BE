package com.aiinterview.platform.service.payment;

import com.aiinterview.platform.model.entity.User;

public interface AiCreditService {
    int getBalance(User user);
    void purchasePackage(User user, Long packageId);
    boolean useCredit(User user, String difficulty);
    int getTodayUsage(User user);
}
