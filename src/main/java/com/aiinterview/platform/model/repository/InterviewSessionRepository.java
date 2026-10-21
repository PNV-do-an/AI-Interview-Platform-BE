package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.InterviewSession;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.InterviewSessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {

    Page<InterviewSession> findByCandidateOrderByCreatedAtDesc(User candidate, Pageable pageable);

    long countByCreatedAtAfter(LocalDateTime since);

    long countByCompletedAtAfter(LocalDateTime since);

    /** NULL when no session has a score yet (spec 005: avg is null, never 0). */
    @Query("SELECT AVG(s.overallScore) FROM InterviewSession s")
    Double avgOverallScore();

    long countByStatus(InterviewSessionStatus status);

    long countByCandidate(User candidate);

    /** Readiness of one account only — 009 FR-002. Null when that user has no scored session. */
    @Query("SELECT AVG(s.overallScore) FROM InterviewSession s "
            + "WHERE s.candidate.id = :userId AND s.overallScore IS NOT NULL AND s.completedAt IS NOT NULL")
    Double avgOverallScoreOf(@Param("userId") Long userId);

    /** MySQL-only: the JVM would have to load every row to subtract two timestamps. */
    @Query(value = "SELECT AVG(TIMESTAMPDIFF(SECOND, created_at, completed_at)) FROM interview_sessions "
            + "WHERE completed_at IS NOT NULL", nativeQuery = true)
    BigDecimal avgDurationSeconds();

    /** Rows are {@code [bucket, count]} with bucket = "yyyy-MM" (009 FR-001). */
    @Query(value = "SELECT DATE_FORMAT(created_at, '%Y-%m'), COUNT(*) FROM interview_sessions "
            + "WHERE created_at >= :from GROUP BY DATE_FORMAT(created_at, '%Y-%m')", nativeQuery = true)
    List<Object[]> countByMonthSince(@Param("from") LocalDateTime from);
}
