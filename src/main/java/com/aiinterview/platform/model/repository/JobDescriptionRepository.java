package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.JobDescription;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface JobDescriptionRepository extends JpaRepository<JobDescription, Long> {

    Optional<JobDescription> findByIdAndDeletedAtIsNull(Long id);

    Page<JobDescription> findByEmployerAndDeletedAtIsNull(User employer, Pageable pageable);

    @Query("""
            SELECT j FROM JobDescription j
            WHERE j.deletedAt IS NULL
              AND j.status = :status
              AND (:search IS NULL OR :search = ''
                   OR LOWER(j.title) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(j.companyName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(j.position) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(j.location) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<JobDescription> searchOpenJobs(@Param("search") String search,
                                        @Param("status") JobStatus status,
                                        Pageable pageable);

    /** Console queue (spec 004 E1): one fixed status filter + keyword over title/company/employer email. */
    @Query("""
            SELECT j FROM JobDescription j
            JOIN j.employer e
            WHERE j.deletedAt IS NULL
              AND j.status = :status
              AND (:search IS NULL OR :search = ''
                   OR LOWER(j.title) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(j.companyName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(e.email) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<JobDescription> searchModerationQueue(@Param("status") JobStatus status,
                                               @Param("search") String search,
                                               Pageable pageable);

    /** Dashboard: rows are {@code [JobStatus, Long]} over live jobs — one pass. */
    @Query("SELECT j.status, COUNT(j.id) FROM JobDescription j WHERE j.deletedAt IS NULL GROUP BY j.status")
    List<Object[]> countLiveByStatus();

    long countByCreatedAtAfterAndDeletedAtIsNull(LocalDateTime since);
}
