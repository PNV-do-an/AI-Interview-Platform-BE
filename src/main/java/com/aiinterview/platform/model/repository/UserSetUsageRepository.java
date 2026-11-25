package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.UserSetUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSetUsageRepository extends JpaRepository<UserSetUsage, Long> {

    Optional<UserSetUsage> findByUserIdAndSetId(Long userId, Long setId);

    List<UserSetUsage> findByUserId(Long userId);

    List<UserSetUsage> findBySetId(Long setId);
}
