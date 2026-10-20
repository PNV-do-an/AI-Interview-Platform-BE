package com.aiinterview.platform.service;

import com.aiinterview.platform.model.dto.request.JobRequest;
import com.aiinterview.platform.model.dto.request.JobReviewRequest;
import com.aiinterview.platform.model.dto.response.JobDetailResponse;
import com.aiinterview.platform.model.dto.response.JobSummaryResponse;
import com.aiinterview.platform.model.dto.response.MatchResponse;
import com.aiinterview.platform.model.enums.JobStatus;
import org.springframework.data.domain.Page;

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

    Page<JobSummaryResponse> adminListJobs(int page, int size, String search);

    java.util.List<MatchResponse> getMatchingCandidates(Long jobId);
}
