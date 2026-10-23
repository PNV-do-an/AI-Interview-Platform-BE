package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.Application;
import com.aiinterview.platform.model.entity.ApplicationStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ApplicationStatusHistoryRepository extends JpaRepository<ApplicationStatusHistory, Long> {

    List<ApplicationStatusHistory> findByApplicationOrderByActedAtAsc(Application application);

    /** (applicationId, toStatus) của mọi dòng lịch sử thuộc một tin, dùng cho phễu ứng tuyển. */
    @Query("SELECT h.application.id, h.toStatus FROM ApplicationStatusHistory h WHERE h.application.job.id = :jobId")
    List<Object[]> findReachedStatusesByJob(@Param("jobId") Long jobId);
}
