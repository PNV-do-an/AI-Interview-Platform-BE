package com.aiinterview.platform.service;

import com.aiinterview.platform.model.dto.request.StartInterviewRequest;
import com.aiinterview.platform.model.dto.request.SubmitInterviewAnswerRequest;
import com.aiinterview.platform.model.dto.response.AnswerEvaluationResponse;
import com.aiinterview.platform.model.dto.response.EvaluationReportResponse;
import com.aiinterview.platform.model.dto.response.InterviewSessionResponse;
import com.aiinterview.platform.model.dto.response.QuestionResponse;
import com.aiinterview.platform.model.entity.User;

import java.util.List;

public interface InterviewService {

    InterviewSessionResponse startSession(User user, StartInterviewRequest request);

    QuestionResponse getNextQuestion(Long userId, Long sessionId);

    AnswerEvaluationResponse submitAnswer(Long userId, Long sessionId, SubmitInterviewAnswerRequest request);

    EvaluationReportResponse finishSession(Long userId, Long sessionId);

    List<InterviewSessionResponse> getUserHistory(Long userId);

    EvaluationReportResponse getSessionReport(Long userId, Long sessionId);
}