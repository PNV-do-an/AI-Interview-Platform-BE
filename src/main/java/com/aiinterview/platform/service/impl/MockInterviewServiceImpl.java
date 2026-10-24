package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.model.dto.request.StartInterviewRequest;
import com.aiinterview.platform.model.dto.request.SubmitAnswerRequest;
import com.aiinterview.platform.model.dto.response.InterviewAnswerResponse;
import com.aiinterview.platform.model.dto.response.InterviewResultResponse;
import com.aiinterview.platform.model.dto.response.InterviewSessionResponse;
import com.aiinterview.platform.model.entity.InterviewAnswer;
import com.aiinterview.platform.model.entity.InterviewSession;
import com.aiinterview.platform.model.entity.JobDescription;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.InterviewMode;
import com.aiinterview.platform.model.enums.InterviewSessionStatus;
import com.aiinterview.platform.model.repository.InterviewAnswerRepository;
import com.aiinterview.platform.model.repository.InterviewSessionRepository;
import com.aiinterview.platform.model.repository.JobDescriptionRepository;
import com.aiinterview.platform.service.MockInterviewService;
import com.aiinterview.platform.service.ai.InterviewAiEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MockInterviewServiceImpl implements MockInterviewService {

    private final InterviewSessionRepository sessionRepository;
    private final InterviewAnswerRepository answerRepository;
    private final JobDescriptionRepository jobRepository;
    private final InterviewAiEngine aiEngine;

    @Override
    @Transactional
    public InterviewSessionResponse start(User candidate, StartInterviewRequest request) {
        InterviewSession session = new InterviewSession();
        session.setCandidate(candidate);
        session.setMode(request.getMode());

        if (request.getMode() == InterviewMode.MOCK) {
            if (request.getJobId() == null) {
                throw new BadRequestException("Vui lòng chọn tin tuyển dụng để luyện phỏng vấn mô phỏng");
            }
            JobDescription job = jobRepository.findByIdAndDeletedAtIsNull(request.getJobId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tin tuyển dụng"));
            session.setJob(job);
            session.setTopic(job.getTitle());
            session.setQuestions(aiEngine.generateMockQuestions(job, request.getQuestionCount()));
        } else {
            if (request.getTopic() == null || request.getTopic().isBlank()) {
                throw new BadRequestException("Vui lòng nhập chủ đề luyện tập kiến thức");
            }
            session.setTopic(request.getTopic().trim());
            session.setQuestions(aiEngine.generateKnowledgeQuestions(session.getTopic(), request.getQuestionCount()));
        }

        InterviewSession saved = sessionRepository.save(session);
        return InterviewSessionResponse.from(saved, 0);
    }

    @Override
    @Transactional
    public InterviewAnswerResponse submitAnswer(User candidate, Long sessionId, SubmitAnswerRequest request) {
        InterviewSession session = ownSession(candidate, sessionId);
        if (session.getStatus() == InterviewSessionStatus.COMPLETED) {
            throw new BadRequestException("Phiên phỏng vấn đã kết thúc");
        }
        int index = session.getCurrentQuestionIndex();
        if (index >= session.getQuestions().size()) {
            throw new BadRequestException("Tất cả câu hỏi đã được trả lời");
        }

        String question = session.getQuestions().get(index);
        Set<String> keywords = session.getMode() == InterviewMode.MOCK
                ? aiEngine.extractKeywords(session.getJob(), question)
                : aiEngine.extractKeywords(session.getTopic(), question);
        InterviewAiEngine.ScoreResult result = aiEngine.evaluate(request.getAnswerText(), keywords);

        InterviewAnswer answer = answerRepository
                .findBySessionAndQuestionIndex(session, index)
                .orElseGet(() -> InterviewAnswer.builder()
                        .session(session)
                        .questionIndex(index)
                        .question(question)
                        .build());
        answer.setAnswerText(request.getAnswerText());
        answer.setDurationSec(request.getDurationSec());
        answer.setScore(result.score());
        answer.setFeedback(result.feedback());
        answerRepository.save(answer);

        session.setCurrentQuestionIndex(index + 1);
        sessionRepository.save(session);

        return InterviewAnswerResponse.from(answer);
    }

    @Override
    @Transactional
    public InterviewResultResponse finish(User candidate, Long sessionId) {
        InterviewSession session = ownSession(candidate, sessionId);
        if (session.getStatus() != InterviewSessionStatus.COMPLETED) {
            List<InterviewAnswer> answers = answerRepository.findBySessionOrderByQuestionIndexAsc(session);
            double avg = answers.stream()
                    .filter(a -> a.getScore() != null)
                    .mapToInt(InterviewAnswer::getScore)
                    .average()
                    .orElse(0);
            session.setOverallScore(answers.isEmpty() ? 0 : (int) Math.round(avg));
            session.setStatus(InterviewSessionStatus.COMPLETED);
            session.setCompletedAt(LocalDateTime.now());
            sessionRepository.save(session);
        }
        return getResult(candidate, sessionId);
    }

    @Override
    @Transactional(readOnly = true)
    public InterviewResultResponse getResult(User candidate, Long sessionId) {
        InterviewSession session = ownSession(candidate, sessionId);
        List<InterviewAnswer> answers = answerRepository.findBySessionOrderByQuestionIndexAsc(session);
        return InterviewResultResponse.builder()
                .session(InterviewSessionResponse.from(session, answers.size()))
                .answers(answers.stream().map(InterviewAnswerResponse::from).toList())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InterviewSessionResponse> getMySessions(User candidate, int page, int size) {
        return sessionRepository.findByCandidateOrderByCreatedAtDesc(candidate, PageRequest.of(page, size))
                .map(s -> InterviewSessionResponse.from(s,
                        answerRepository.findBySessionOrderByQuestionIndexAsc(s).size()));
    }

    @Override
    @Transactional
    public void deleteSession(User candidate, Long sessionId) {
        InterviewSession session = ownSession(candidate, sessionId);
        // interview_answers giữ FK session_id nên phải xóa trước, nếu không DB chặn.
        answerRepository.deleteAll(answerRepository.findBySessionOrderByQuestionIndexAsc(session));
        sessionRepository.delete(session);
    }

    private InterviewSession ownSession(User candidate, Long sessionId) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiên phỏng vấn"));
        if (!session.getCandidate().getId().equals(candidate.getId())) {
            throw new BadRequestException("Bạn không có quyền truy cập phiên phỏng vấn này");
        }
        return session;
    }
}
