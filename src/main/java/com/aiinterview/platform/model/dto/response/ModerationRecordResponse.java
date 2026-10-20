package com.aiinterview.platform.model.dto.response;

import com.aiinterview.platform.model.entity.JobModerationRecord;
import com.aiinterview.platform.model.enums.ModerationAction;
import com.aiinterview.platform.model.enums.ModerationActorRole;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** One timeline/history entry of JD moderation (spec 004 E2/E3). */
@Data
@Builder
public class ModerationRecordResponse {

    private Long id;
    private Long actorId;
    private String actorName;
    private String actorEmail;
    private ModerationActorRole actorRole;
    private ModerationAction action;
    private String note;
    private Long processingSeconds;
    private LocalDateTime createdAt;

    public static ModerationRecordResponse from(JobModerationRecord record) {
        return ModerationRecordResponse.builder()
                .id(record.getId())
                .actorId(record.getActor().getId())
                .actorName(record.getActor().getFullName())
                .actorEmail(record.getActor().getEmail())
                .actorRole(record.getActorRole())
                .action(record.getAction())
                .note(record.getNote())
                .processingSeconds(record.getProcessingSeconds())
                .createdAt(record.getCreatedAt())
                .build();
    }
}
