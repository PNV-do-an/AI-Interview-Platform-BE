package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.AiCreditUsage;
import com.aiinterview.platform.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface AiCreditUsageRepository extends JpaRepository<AiCreditUsage, Long> {
    Optional<AiCreditUsage> findByUserAndUsageDate(User user, LocalDate date);
}
