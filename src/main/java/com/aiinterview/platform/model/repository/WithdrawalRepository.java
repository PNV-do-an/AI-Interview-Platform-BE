package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.Withdrawal;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.WithdrawalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WithdrawalRepository extends JpaRepository<Withdrawal, Long> {
    List<Withdrawal> findByMentorOrderByCreatedAtDesc(User mentor);
    List<Withdrawal> findByStatusOrderByCreatedAtDesc(WithdrawalStatus status);
}
