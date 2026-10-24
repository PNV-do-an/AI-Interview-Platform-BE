package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.Application;
import com.aiinterview.platform.model.entity.JobDescription;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    boolean existsByCandidateAndJobAndStatusNot(User candidate, JobDescription job, com.aiinterview.platform.model.enums.ApplicationStatus status);

    boolean existsByCandidateAndJobEmployerAndStatusIn(User candidate, User employer, Collection<com.aiinterview.platform.model.enums.ApplicationStatus> statuses);

    Page<Application> findByCandidateOrderByCreatedAtDesc(User candidate, Pageable pageable);

    Page<Application> findByJobOrderByCreatedAtDesc(JobDescription job, Pageable pageable);

    Optional<Application> findByIdAndCandidate(Long id, User candidate);

    long countByJobAndStatus(JobDescription job, com.aiinterview.platform.model.enums.ApplicationStatus status);

    long countByCandidate(User candidate);

    /** id + trạng thái hiện tại của mọi đơn thuộc tin, cho phễu ứng tuyển. */
    @Query("SELECT a.id, a.status FROM Application a WHERE a.job.id = :jobId")
    java.util.List<Object[]> findStatusesByJob(@Param("jobId") Long jobId);

    /** Đơn còn ở vòng hẹn lịch trên các khung giờ đang xét — nhánh join của job nhắc lịch. */
    List<Application> findBySlotIdInAndStatus(Collection<Long> slotIds, ApplicationStatus status);
}
