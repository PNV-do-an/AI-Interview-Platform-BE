package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.AiCreditPackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiCreditPackageRepository extends JpaRepository<AiCreditPackage, Long> {
    List<AiCreditPackage> findByActiveTrueOrderByPriceAsc();
}
