package com.aiinterview.platform.model.dto.response;

import com.aiinterview.platform.model.entity.InterviewAnswer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** Một lượt hội thoại của phiên AVATAR, đọc từ {@code interview_answers}. */
@Data
@Builder
public class InterviewTranscriptEntry {

    private int index;
    private String questionText;
    private String answerText;
    /** false = bản nháp STT còn sửa được, true = ứng viên đã chốt lượt này. */
    private boolean confirmed;
    private Integer durationSec;
    private LocalDateTime answeredAt;

    public static InterviewTranscriptEntry from(InterviewAnswer a) {
        return InterviewTranscriptEntry.builder()
                .index(a.getQuestionIndex())
                .questionText(a.getQuestion())
                .answerText(a.getAnswerText())
                .confirmed(a.getConfirmedAt() != null)
                .durationSec(a.getDurationSec())
                .answeredAt(a.getAnsweredAt())
                .build();
    }
}
