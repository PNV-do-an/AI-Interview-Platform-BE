package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.exception.ForbiddenException;
import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.model.dto.request.InterviewEvaluationRequest;
import com.aiinterview.platform.model.dto.request.StartInterviewRequest;
import com.aiinterview.platform.model.dto.request.SubmitAnswerRequest;
import com.aiinterview.platform.model.dto.response.InterviewAnswerResponse;
import com.aiinterview.platform.model.dto.response.InterviewReportResponse;
import com.aiinterview.platform.model.dto.response.InterviewResultResponse;
import com.aiinterview.platform.model.dto.response.InterviewSessionResponse;
import com.aiinterview.platform.model.dto.response.InterviewTranscriptEntry;
import com.aiinterview.platform.model.entity.InterviewAnswer;
import com.aiinterview.platform.model.entity.InterviewReport;
import com.aiinterview.platform.model.entity.InterviewSession;
import com.aiinterview.platform.model.entity.JobDescription;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.InterviewMode;
import com.aiinterview.platform.model.enums.InterviewSessionStatus;
import com.aiinterview.platform.model.repository.InterviewAnswerRepository;
import com.aiinterview.platform.model.repository.InterviewReportRepository;
import com.aiinterview.platform.model.repository.InterviewSessionRepository;
import com.aiinterview.platform.model.repository.JobDescriptionRepository;
import com.aiinterview.platform.service.MockInterviewService;
import com.aiinterview.platform.service.ai.InterviewAiEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MockInterviewServiceImpl implements MockInterviewService {

    /** Trần chống nhồi hội thoại: mỗi lượt ghi thêm một dòng interview_answers (spec 012). */
    static final int MAX_AVATAR_TURNS = 40;

    /** Thang chấm decision-support do BE đề xuất và anh Huy duyệt 26/10; đổi thang phải tăng rubricVersion. */
    static final List<String> RUBRIC_DIMENSIONS = List.of("chuyenMon", "duAn", "tuDuy", "truyenDat", "mucKhop");

    private final InterviewSessionRepository sessionRepository;
    private final InterviewAnswerRepository answerRepository;
    private final InterviewReportRepository reportRepository;
    private final JobDescriptionRepository jobRepository;
    private final InterviewAiEngine aiEngine;

    @Override
    @Transactional
    public InterviewSessionResponse start(User candidate, StartInterviewRequest request) {
        InterviewSession session = new InterviewSession();
        session.setCandidate(candidate);
        session.setMode(request.getMode());

        if (request.getMode() == InterviewMode.AVATAR) {
            // Câu hỏi do AI service sinh giữa lúc hội thoại nên BE không sinh trước, questionCount bị bỏ qua.
            session.setJob(requireJob(request.getJobId(),
                    "Vui lòng chọn tin tuyển dụng cho phiên phỏng vấn avatar"));
            session.setTopic(session.getJob().getTitle());
            session.setQuestions(new ArrayList<>());
        } else if (request.getMode() == InterviewMode.MOCK) {
            session.setJob(requireJob(request.getJobId(),
                    "Vui lòng chọn tin tuyển dụng để luyện phỏng vấn mô phỏng"));
            session.setTopic(session.getJob().getTitle());
            session.setQuestions(aiEngine.generateMockQuestions(session.getJob(), request.getQuestionCount()));
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
        if (session.getMode() == InterviewMode.AVATAR) {
            return submitAvatarTurn(session, request);
        }
        if (isPresent(request.getQuestionText())) {
            throw new BadRequestException("Chế độ này không nhận câu hỏi từ client");
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

    /**
     * Một lượt hội thoại AVATAR: upsert theo (session, questionIndex) nên FE dán lại transcript
     * sau khi ứng viên sửa lời chỉ ghi đè đúng lượt đó, không tạo bản sao.
     * Không chấm điểm từng lượt — điểm của luồng này nằm ở {@code interview_report}.
     */
    private InterviewAnswerResponse submitAvatarTurn(InterviewSession session, SubmitAnswerRequest request) {
        int current = session.getCurrentQuestionIndex();
        int index = request.getQuestionIndex() == null ? current : request.getQuestionIndex();
        // Chặn trước khi ghi: retry của một request confirm bị timeout không được phép thành lượt mới.
        if (index > current) {
            throw new BadRequestException("Không ghi được vào lượt chưa tới, hãy trả lời lượt hiện tại");
        }
        if (index >= MAX_AVATAR_TURNS) {
            throw new BadRequestException("Phiên đã đạt số lượt tối đa, hãy kết thúc");
        }
        if (!isPresent(request.getQuestionText())) {
            throw new BadRequestException("Câu hỏi của lượt này không được để trống");
        }

        InterviewAnswer answer = answerRepository.findBySessionAndQuestionIndex(session, index).orElse(null);
        if (answer != null && answer.getConfirmedAt() != null) {
            throw new BadRequestException("Lượt này đã được chốt");
        }
        if (answer == null) {
            answer = InterviewAnswer.builder().session(session).questionIndex(index).build();
        }
        boolean confirm = request.getConfirm() == null || request.getConfirm();

        answer.setQuestion(request.getQuestionText().trim());
        answer.setAnswerText(request.getAnswerText());
        answer.setDurationSec(request.getDurationSec());
        if (confirm) {
            answer.setConfirmedAt(LocalDateTime.now());
        }
        answerRepository.save(answer);

        if (confirm) {
            session.setCurrentQuestionIndex(index + 1);
            sessionRepository.save(session);
        }
        return InterviewAnswerResponse.from(answer);
    }

    @Override
    @Transactional
    public InterviewResultResponse finish(User candidate, Long sessionId) {
        InterviewSession session = ownSession(candidate, sessionId);
        if (session.getStatus() != InterviewSessionStatus.COMPLETED) {
            List<InterviewAnswer> answers = answerRepository.findBySessionOrderByQuestionIndexAsc(session);
            if (session.getMode() != InterviewMode.AVATAR) {
                double avg = answers.stream()
                        .filter(a -> a.getScore() != null)
                        .mapToInt(InterviewAnswer::getScore)
                        .average()
                        .orElse(0);
                session.setOverallScore(answers.isEmpty() ? 0 : (int) Math.round(avg));
            }
            // Phiên avatar để overallScore null: điểm thật do rubric nộp sau đó, không phải trung bình 0.
            session.setStatus(InterviewSessionStatus.COMPLETED);
            session.setCompletedAt(LocalDateTime.now());
            sessionRepository.save(session);
        }
        return getResult(candidate, sessionId);
    }

    @Override
    @Transactional
    public InterviewReportResponse submitEvaluation(User candidate, Long sessionId,
                                                    InterviewEvaluationRequest request) {
        InterviewSession session = ownSession(candidate, sessionId);
        if (session.getStatus() != InterviewSessionStatus.COMPLETED) {
            throw new BadRequestException("Phiên chưa kết thúc");
        }
        if (session.getMode() != InterviewMode.AVATAR) {
            throw new BadRequestException("Rubric chỉ dùng cho phiên phỏng vấn avatar");
        }
        if (reportRepository.findBySession(session).isPresent()) {
            throw new BadRequestException("Báo cáo đã được nộp cho phiên này");
        }

        int[] dims = validateRubric(request.getRubric());
        int total = (int) Math.round((dims[0] + dims[1] + dims[2] + dims[3] + dims[4]) / (double) dims.length);

        InterviewReport report = InterviewReport.builder()
                .session(session)
                .chuyenMon(dims[0])
                .duAn(dims[1])
                .tuDuy(dims[2])
                .truyenDat(dims[3])
                .mucKhop(dims[4])
                .overallScore(total)
                .strengths(request.getStrengths())
                .improvements(request.getImprovements())
                .rubricVersion(request.getRubricVersion().trim())
                .build();
        InterviewReport saved = reportRepository.save(report);
        log.info("Avatar evaluation stored: reportId={}, sessionId={}, overallScore={}",
                saved.getId(), sessionId, total);
        return InterviewReportResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public InterviewReportResponse getReport(User candidate, Long sessionId) {
        InterviewSession session = ownSession(candidate, sessionId);
        return reportRepository.findBySession(session)
                .map(InterviewReportResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Chưa có báo cáo cho phiên phỏng vấn này"));
    }

    /** Đúng 5 chiều, mỗi chiều integer 0..100; sai thì 400 kèm danh sách chiều, không lưu nửa rubric. */
    private int[] validateRubric(Map<String, Object> rubric) {
        if (rubric == null || rubric.isEmpty()) {
            throw new BadRequestException("Rubric phải có đủ " + RUBRIC_DIMENSIONS);
        }
        int[] values = new int[RUBRIC_DIMENSIONS.size()];
        List<String> missing = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        for (int i = 0; i < RUBRIC_DIMENSIONS.size(); i++) {
            String dim = RUBRIC_DIMENSIONS.get(i);
            if (!rubric.containsKey(dim)) {
                missing.add(dim);
                continue;
            }
            Integer score = toScore(rubric.get(dim));
            if (score == null) {
                invalid.add(dim);
            } else {
                values[i] = score;
            }
        }
        List<String> extra = rubric.keySet().stream().filter(k -> !RUBRIC_DIMENSIONS.contains(k)).toList();

        List<String> problems = new ArrayList<>();
        if (!missing.isEmpty()) {
            problems.add("thiếu chiều " + missing);
        }
        if (!extra.isEmpty()) {
            problems.add("chiều không có trong thang chấm " + extra);
        }
        if (!invalid.isEmpty()) {
            problems.add("không phải số nguyên 0..100 " + invalid);
        }
        if (!problems.isEmpty()) {
            throw new BadRequestException("Rubric không hợp lệ: " + String.join("; ", problems));
        }
        return values;
    }

    private static Integer toScore(Object value) {
        if (!(value instanceof Number number)) {
            return null;
        }
        double d = number.doubleValue();
        if (d != Math.floor(d) || d < 0 || d > 100) {
            return null;
        }
        return (int) d;
    }

    @Override
    @Transactional(readOnly = true)
    public InterviewResultResponse getResult(User candidate, Long sessionId) {
        InterviewSession session = ownSession(candidate, sessionId);
        List<InterviewAnswer> answers = answerRepository.findBySessionOrderByQuestionIndexAsc(session);
        return InterviewResultResponse.builder()
                .session(InterviewSessionResponse.from(session, answers.size()))
                .answers(answers.stream().map(InterviewAnswerResponse::from).toList())
                .transcript(answers.stream().map(InterviewTranscriptEntry::from).toList())
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
        // interview_answers và interview_report giữ FK session_id nên phải xóa trước, nếu không DB chặn.
        reportRepository.deleteBySession(session);
        answerRepository.deleteAll(answerRepository.findBySessionOrderByQuestionIndexAsc(session));
        sessionRepository.delete(session);
    }

    private JobDescription requireJob(Long jobId, String missingMessage) {
        if (jobId == null) {
            throw new BadRequestException(missingMessage);
        }
        return jobRepository.findByIdAndDeletedAtIsNull(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tin tuyển dụng"));
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private InterviewSession ownSession(User candidate, Long sessionId) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiên phỏng vấn"));
        if (!session.getCandidate().getId().equals(candidate.getId())) {
            // 403 chứ không 404: id người khác tồn tại hay không là thông tin không lộ ra.
            throw new ForbiddenException("Bạn không có quyền truy cập phiên phỏng vấn này");
        }
        return session;
    }
}
