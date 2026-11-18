package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.AiCreditLedger;
import com.aiinterview.platform.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiCreditLedgerRepository extends JpaRepository<AiCreditLedger, Long> {
    List<AiCreditLedger> findByUserOrderByCreatedAtDesc(User user);
}
