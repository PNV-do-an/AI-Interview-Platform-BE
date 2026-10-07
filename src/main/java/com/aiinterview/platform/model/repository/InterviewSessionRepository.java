package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.InterviewSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {

    Optional<InterviewSession> findByIdAndUserId(Long id, Long userId);

    List<InterviewSession> findByUserIdOrderByCreatedAtDesc(Long userId);
}