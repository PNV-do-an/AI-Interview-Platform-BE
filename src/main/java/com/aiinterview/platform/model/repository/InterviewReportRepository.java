package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.InterviewReport;
import com.aiinterview.platform.model.entity.InterviewSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InterviewReportRepository extends JpaRepository<InterviewReport, Long> {

    Optional<InterviewReport> findBySession(InterviewSession session);

    /**
     * DELETE tập hợp con theo session để xóa phiên không phải đọc từng dòng report lên
     * persistence context; @Modifying là bắt buộc vì đây là @Query kiểu delete.
     */
    @Modifying(clearAutomatically = true)
    @Query("delete from InterviewReport r where r.session = :session")
    void deleteBySession(@Param("session") InterviewSession session);
}
