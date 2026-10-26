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
}
