package com.aiinterview.platform.model.dto.response;

import com.aiinterview.platform.model.entity.Application;
import com.aiinterview.platform.model.enums.ApplicationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ApplicationDetailResponse {

    private Long id;
    private Long jobId;
    private String jobTitle;
    private String companyName;
    private Integer score;
    private String feedback;
    private String coverLetter;
    private ApplicationStatus status;
    private SlotResponse slot;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Candidate info (cùng fields như ApplicationResponse)
    private Long candidateId;
    private String candidateName;
    private String candidateEmail;

    // Employer/Interviewer info
    private Long interviewerId;
    private String interviewerName;

    // Application history
    private List<ApplicationStatusTimelineEntry> statusHistory;

    /** Build from entity, optional params: timeline data & internal notes visibility */
    public static ApplicationDetailResponse from(Application application,
                                                 List<ApplicationStatusTimelineEntry> timeline) {
        return ApplicationDetailResponse.builder()
                .id(application.getId())
                .jobId(application.getJob().getId())
                .jobTitle(application.getJob().getTitle())
                .companyName(application.getJob().getCompanyName())
                .candidateId(application.getCandidate().getId())
                .candidateName(application.getCandidate().getFullName())
                .candidateEmail(application.getCandidate().getEmail())
                .status(application.getStatus())
                .score(application.getScore())
                .feedback(application.getFeedback())
                .coverLetter(application.getCoverLetter())
                .slot(application.getSlot() != null ? SlotResponse.from(application.getSlot()) : null)
                .createdAt(application.getCreatedAt())
                .updatedAt(application.getUpdatedAt())
                .interviewerId(application.getJob().getEmployer().getId())
                .interviewerName(application.getJob().getEmployer().getFullName())
                .statusHistory(timeline)
                .build();
    }
}
