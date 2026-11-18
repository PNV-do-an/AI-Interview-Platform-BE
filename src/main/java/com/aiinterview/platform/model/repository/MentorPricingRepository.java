package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.MentorPricing;
import com.aiinterview.platform.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MentorPricingRepository extends JpaRepository<MentorPricing, Long> {
    Optional<MentorPricing> findByMentor(User mentor);
}
