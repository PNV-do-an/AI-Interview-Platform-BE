package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.InterviewPracticeSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewPracticeSessionRepository extends JpaRepository<InterviewPracticeSession, Long> {

    Optional<InterviewPracticeSession> findByIdAndUserId(Long id, Long userId);

    List<InterviewPracticeSession> findByUserIdOrderByCreatedAtDesc(Long userId);
}
