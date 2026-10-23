package com.aiinterview.platform.model.dto.response;

import com.aiinterview.platform.model.entity.ApplicationStatusHistory;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** Một mốc trên timeline trạng thái của đơn ứng tuyển. */
@Data
@Builder
public class ApplicationStatusTimelineEntry {

    private String fromStatus;
    private String toStatus;
    private Long actorId;
    private String actorEmail;
    private LocalDateTime actedAt;
    /** Ghi chú nội bộ: null khi người xem là candidate (BR-09). */
    private String note;

    public static ApplicationStatusTimelineEntry from(ApplicationStatusHistory history, boolean internalView) {
        return ApplicationStatusTimelineEntry.builder()
                .fromStatus(history.getFromStatus())
                .toStatus(history.getToStatus())
                .actorId(internalView && history.getActor() != null ? history.getActor().getId() : null)
                .actorEmail(internalView && history.getActor() != null ? history.getActor().getEmail() : null)
                .actedAt(history.getActedAt())
                .note(internalView ? history.getNote() : null)
                .build();
    }
}
