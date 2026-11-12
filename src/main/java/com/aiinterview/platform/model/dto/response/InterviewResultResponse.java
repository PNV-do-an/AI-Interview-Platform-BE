package com.aiinterview.platform.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class InterviewResultResponse {

    private InterviewSessionResponse session;
    private List<InterviewAnswerResponse> answers;
    /** Mỗi lượt một dòng, cùng thứ tự {@code answers}; phiên AVATAR dùng mảng này làm transcript. */
    private List<InterviewTranscriptEntry> transcript;
    private Integer totalQuestions;
    private Integer correctCount;
    private Integer score;
    /** Báo cáo rubric của phiên AVATAR; null với MOCK/KNOWLEDGE hoặc khi báo cáo chưa được tạo. */
    private InterviewReportResponse report;
}
