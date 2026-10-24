package com.aiinterview.platform.service;

import com.aiinterview.platform.model.dto.request.StartInterviewRequest;
import com.aiinterview.platform.model.dto.request.SubmitAnswerRequest;
import com.aiinterview.platform.model.dto.response.InterviewAnswerResponse;
import com.aiinterview.platform.model.dto.response.InterviewResultResponse;
import com.aiinterview.platform.model.dto.response.InterviewSessionResponse;
import com.aiinterview.platform.model.entity.User;
import org.springframework.data.domain.Page;

public interface MockInterviewService {

    InterviewSessionResponse start(User candidate, StartInterviewRequest request);

    InterviewAnswerResponse submitAnswer(User candidate, Long sessionId, SubmitAnswerRequest request);

    InterviewResultResponse finish(User candidate, Long sessionId);

    InterviewResultResponse getResult(User candidate, Long sessionId);

    Page<InterviewSessionResponse> getMySessions(User candidate, int page, int size);

    /** Xóa hẳn một phiên luyện của chính ứng viên kèm câu trả lời của nó. */
    void deleteSession(User candidate, Long sessionId);
}
