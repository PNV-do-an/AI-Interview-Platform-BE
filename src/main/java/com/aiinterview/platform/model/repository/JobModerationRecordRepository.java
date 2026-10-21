package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.JobModerationRecord;
import com.aiinterview.platform.model.enums.ModerationAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface JobModerationRecordRepository extends JpaRepository<JobModerationRecord, Long> {

    List<JobModerationRecord> findByJobIdOrderByCreatedAtDesc(Long jobId);

    boolean existsByJobIdAndAction(Long jobId, ModerationAction action);

    long countByAction(ModerationAction action);

    /** Distinct jobs carrying this decision — a job resubmitted after a rejection counts once. */
    @Query("SELECT COUNT(DISTINCT r.job.id) FROM JobModerationRecord r WHERE r.action = :action")
    long countDistinctJobsByAction(@Param("action") ModerationAction action);

    @Query("""
            SELECT COUNT(DISTINCT r.job.id) FROM JobModerationRecord r
            WHERE r.action = :action AND r.job.employer.id = :employerId AND r.job.deletedAt IS NULL
            """)
    long countDistinctJobsOfEmployerByAction(@Param("action") ModerationAction action,
                                             @Param("employerId") Long employerId);

    @Query("""
            SELECT AVG(r.processingSeconds)
            FROM JobModerationRecord r
            WHERE r.action IN (com.aiinterview.platform.model.enums.ModerationAction.APPROVED,
                               com.aiinterview.platform.model.enums.ModerationAction.REJECTED)
              AND r.processingSeconds IS NOT NULL
            """)
    Double avgProcessingSeconds();

    @Query("""
            SELECT r FROM JobModerationRecord r
            JOIN r.job j
            JOIN j.employer e
            WHERE r.action IN (com.aiinterview.platform.model.enums.ModerationAction.APPROVED,
                               com.aiinterview.platform.model.enums.ModerationAction.REJECTED)
              AND (:decision IS NULL OR r.action = :decision)
              AND (:from IS NULL OR r.createdAt >= :from)
              AND (:to IS NULL OR r.createdAt <= :to)
              AND (:moderatorId IS NULL OR r.actor.id = :moderatorId)
              AND (:search IS NULL OR :search = ''
                   OR LOWER(j.title) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(j.companyName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(CAST(j.id AS string)) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<JobModerationRecord> searchDecisions(@Param("decision") ModerationAction decision,
                                              @Param("from") LocalDateTime from,
                                              @Param("to") LocalDateTime to,
                                              @Param("moderatorId") Long moderatorId,
                                              @Param("search") String search,
                                              Pageable pageable);
}
