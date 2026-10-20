package com.aiinterview.platform.model.dto.response;

import com.aiinterview.platform.model.entity.JobModerationRecord;
import com.aiinterview.platform.model.enums.ModerationAction;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** One row of the moderation history table (spec 004 E3): the decision + the job it belongs to. */
@Data
@Builder
public class JobModerationHistoryResponse {

    private Long id;
    private Long jobId;
    private String jobTitle;
    private String companyName;
    private String employerName;
    private String employerEmail;
    private ModerationAction action;
    private Long moderatorId;
    private String moderatorEmail;
    private String note;
    private Long processingSeconds;
    private LocalDateTime createdAt;

    public static JobModerationHistoryResponse from(JobModerationRecord record) {
        return JobModerationHistoryResponse.builder()
                .id(record.getId())
                .jobId(record.getJob().getId())
                .jobTitle(record.getJob().getTitle())
                .companyName(record.getJob().getCompanyName())
                .employerName(record.getJob().getEmployer().getFullName())
                .employerEmail(record.getJob().getEmployer().getEmail())
                .action(record.getAction())
                .moderatorId(record.getActor().getId())
                .moderatorEmail(record.getActor().getEmail())
                .note(record.getNote())
                .processingSeconds(record.getProcessingSeconds())
                .createdAt(record.getCreatedAt())
                .build();
    }
}
