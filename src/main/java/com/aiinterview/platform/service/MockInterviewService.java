package com.aiinterview.platform.service;

import com.aiinterview.platform.model.dto.request.InterviewEvaluationRequest;
import com.aiinterview.platform.model.dto.request.StartInterviewRequest;
import com.aiinterview.platform.model.dto.request.SubmitAnswerRequest;
import com.aiinterview.platform.model.dto.response.InterviewAnswerResponse;
import com.aiinterview.platform.model.dto.response.InterviewReportResponse;
import com.aiinterview.platform.model.dto.response.InterviewResultResponse;
import com.aiinterview.platform.model.dto.response.InterviewSessionResponse;
import com.aiinterview.platform.model.entity.User;
import org.springframework.data.domain.Page;

public interface MockInterviewService {

    InterviewSessionResponse start(User candidate, StartInterviewRequest request);

    InterviewAnswerResponse submitAnswer(User candidate, Long sessionId, SubmitAnswerRequest request);

    InterviewResultResponse finish(User candidate, Long sessionId);

    /** Nộp rubric 5 chiều cho phiên AVATAR đã COMPLETED; BE tự tính điểm tổng. Chỉ nộp một lần. */
    InterviewReportResponse submitEvaluation(User candidate, Long sessionId, InterviewEvaluationRequest request);

    /** Báo cáo avatar — chỉ chính chủ phiên đọc được (Phạm vi 6 spec 012). */
    InterviewReportResponse getReport(User candidate, Long sessionId);

    InterviewResultResponse getResult(User candidate, Long sessionId);

    Page<InterviewSessionResponse> getMySessions(User candidate, int page, int size);

    /** Xóa hẳn một phiên luyện của chính ứng viên kèm câu trả lời và báo cáo của nó. */
    void deleteSession(User candidate, Long sessionId);
}
