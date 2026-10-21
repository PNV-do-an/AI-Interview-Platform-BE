package com.aiinterview.platform.service;

import com.aiinterview.platform.model.dto.request.JobRequest;
import com.aiinterview.platform.model.dto.request.JobReviewRequest;
import com.aiinterview.platform.model.dto.response.JobDetailResponse;
import com.aiinterview.platform.model.dto.response.JobModerationHistoryResponse;
import com.aiinterview.platform.model.dto.response.JobSummaryResponse;
import com.aiinterview.platform.model.dto.response.MatchResponse;
import com.aiinterview.platform.model.dto.response.ModerationHistoryStatsResponse;
import com.aiinterview.platform.model.dto.response.ModerationJobDetailResponse;
import com.aiinterview.platform.model.dto.response.ModerationJobResponse;
import com.aiinterview.platform.model.dto.response.ModerationQueueStatsResponse;
import com.aiinterview.platform.model.enums.JobStatus;
import com.aiinterview.platform.model.enums.ModerationAction;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;

public interface JobService {

    Page<JobSummaryResponse> searchJobs(int page, int size, String search);

    JobDetailResponse getJobDetail(Long jobId);

    Page<JobSummaryResponse> getEmployerJobs(int page, int size);

    JobDetailResponse createJob(JobRequest request);

    JobDetailResponse updateJob(Long jobId, JobRequest request);

    JobDetailResponse changeStatus(Long jobId, JobStatus status);

    JobDetailResponse submitForReview(Long jobId);

    JobDetailResponse reviewJob(Long jobId, JobReviewRequest request);

    void deleteJob(Long jobId);

    List<MatchResponse> getMatchingCandidates(Long jobId);

    // ── moderation console (spec 004) ────────────────────────────────────────

    Page<ModerationJobResponse> moderationQueue(int page, int size, JobStatus status, String search);

    ModerationJobDetailResponse moderationDetail(Long jobId);

    Page<JobModerationHistoryResponse> moderationHistory(int page, int size, ModerationAction decision,
                                                         LocalDate from, LocalDate to,
                                                         Long moderatorId, String search);

    ModerationHistoryStatsResponse moderationHistoryStats();

    /** The four chips above the queue (spec 009 FR-004). */
    ModerationQueueStatsResponse moderationQueueStats();
}
