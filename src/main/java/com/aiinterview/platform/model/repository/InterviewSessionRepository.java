package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.InterviewSession;
import com.aiinterview.platform.model.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {

    Page<InterviewSession> findByCandidateOrderByCreatedAtDesc(User candidate, Pageable pageable);

    long countByCreatedAtAfter(LocalDateTime since);

    long countByCompletedAtAfter(LocalDateTime since);

    /** NULL when no session has a score yet (spec 005: avg is null, never 0). */
    @Query("SELECT AVG(s.overallScore) FROM InterviewSession s")
    Double avgOverallScore();
}
