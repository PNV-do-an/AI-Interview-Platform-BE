package com.aiinterview.platform.model.dto.response;

import com.aiinterview.platform.model.entity.JobDescription;
import com.aiinterview.platform.model.enums.JobStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** One row of the admin moderation queue (spec 004 E1). */
@Data
@Builder
public class ModerationJobResponse {

    private Long id;
    private String title;
    private String companyName;
    private String employerName;
    private String employerEmail;
    private JobStatus status;
    private String reviewNote;
    private LocalDateTime submittedAt;
    private LocalDateTime decidedAt;
    private Integer aiSafetyScore;
    private LocalDateTime createdAt;

    public static ModerationJobResponse from(JobDescription job) {
        return ModerationJobResponse.builder()
                .id(job.getId())
                .title(job.getTitle())
                .companyName(job.getCompanyName())
                .employerName(job.getEmployer().getFullName())
                .employerEmail(job.getEmployer().getEmail())
                .status(job.getStatus())
                .reviewNote(job.getReviewNote())
                .submittedAt(job.getSubmittedAt())
                .decidedAt(job.getDecidedAt())
                .aiSafetyScore(job.getAiSafetyScore())
                .createdAt(job.getCreatedAt())
                .build();
    }
}
