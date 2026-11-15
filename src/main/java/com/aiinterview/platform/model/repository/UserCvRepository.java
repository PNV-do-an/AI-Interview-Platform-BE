package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.UserCv;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserCvRepository extends JpaRepository<UserCv, Long> {

    Optional<UserCv> findByUserId(Long userId);

    Optional<UserCv> findTopByUserIdOrderByUpdatedAtDesc(Long userId);

    boolean existsByUserId(Long userId);
}