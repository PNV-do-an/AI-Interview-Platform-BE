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
import com.aiinterview.platform.model.entity.QuizQuestion;
import com.aiinterview.platform.model.entity.JobDescription;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.InterviewMode;
import com.aiinterview.platform.model.enums.InterviewSessionStatus;
import com.aiinterview.platform.model.repository.InterviewAnswerRepository;
import com.aiinterview.platform.model.repository.InterviewReportRepository;
import com.aiinterview.platform.model.repository.InterviewSessionRepository;
import com.aiinterview.platform.model.repository.JobDescriptionRepository;
import com.aiinterview.platform.service.MockInterviewService;
import com.aiinterview.platform.service.ai.AvatarReportEvaluator;
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
    private final AvatarReportEvaluator avatarReportEvaluator;

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
            if (!isPresent(request.getAnswerText())) {
                throw new BadRequestException("Câu trả lời không được để trống");
            }
            return submitAvatarTurn(session, request);
        }
        if (isPresent(request.getQuestionText())) {
            throw new BadRequestException("Chế độ này không nhận câu hỏi từ client");
        }

        int index = session.getCurrentQuestionIndex();
        if (index >= session.getQuestions().size()) {
            throw new BadRequestException("Tất cả câu hỏi đã được trả lời");
        }

        QuizQuestion quizQuestion = session.getQuestions().get(index);
        String question = quizQuestion.getQuestion();
        List<String> options = quizQuestion.getOptions();

        Integer selectedIndex = request.getSelectedOptionIndex();
        if (selectedIndex == null) {
            selectedIndex = mapAnswerToOptionIndex(request.getAnswerText(), options);
        }

        boolean hasValidIndex = selectedIndex != null && selectedIndex >= 0 && selectedIndex < options.size();
        boolean providedIndex = request.getSelectedOptionIndex() != null;
        if (providedIndex && !hasValidIndex) {
            throw new BadRequestException("Vui lòng chọn một đáp án");
        }
        if (!providedIndex && selectedIndex == null && !isPresent(request.getAnswerText())) {
            throw new BadRequestException("Vui lòng chọn một đáp án");
        }

        int score;
        String selectedText;
        String feedback;
        if (hasValidIndex) {
            boolean correct = selectedIndex == quizQuestion.getCorrectOptionIndex();
            score = correct ? 100 : 0;
            selectedText = options.get(selectedIndex);
            feedback = correct
                    ? "Chính xác."
                    : "Sai. Đáp án đúng là: " + options.get(quizQuestion.getCorrectOptionIndex()) + ".";
        } else {
            // Giữ tương thích với luồng tự luận cũ: không chọn đáp án thì chấm 0 điểm.
            score = 0;
            selectedText = isPresent(request.getAnswerText()) ? request.getAnswerText().trim() : "";
            feedback = "Sai. Đáp án đúng là: " + options.get(quizQuestion.getCorrectOptionIndex()) + ".";
        }

        InterviewAnswer answer = answerRepository
                .findBySessionAndQuestionIndex(session, index)
                .orElseGet(() -> InterviewAnswer.builder()
                        .session(session)
                        .questionIndex(index)
                        .question(question)
                        .build());
        answer.setAnswerText(selectedText);
        answer.setOptionsJson(quizQuestion.getOptionsJson());
        answer.setCorrectAnswer(options.get(quizQuestion.getCorrectOptionIndex()));
        answer.setSelectedOptionIndex(hasValidIndex ? selectedIndex : null);
        answer.setDurationSec(request.getDurationSec());
        answer.setScore(score);
        answer.setFeedback(feedback);
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
        List<InterviewAnswer> answers = answerRepository.findBySessionOrderByQuestionIndexAsc(session);
        if (session.getStatus() != InterviewSessionStatus.COMPLETED) {
            if (session.getMode() != InterviewMode.AVATAR) {
                double avg = answers.stream()
                        .filter(a -> a.getScore() != null)
                        .mapToInt(InterviewAnswer::getScore)
                        .average()
                        .orElse(0);
                session.setOverallScore(answers.isEmpty() ? 0 : (int) Math.round(avg));
            }
            // Phiên avatar để overallScore null: điểm thật nằm ở interview_report, không phải trung bình 0.
            session.setStatus(InterviewSessionStatus.COMPLETED);
            session.setCompletedAt(LocalDateTime.now());
            sessionRepository.save(session);
        }
        if (session.getMode() == InterviewMode.AVATAR) {
            // Kết thúc là có báo cáo ngay; ứng viên không phải bấm nộp rubric.
            ensureAvatarReport(session, answers);
        }
        return getResult(candidate, sessionId);
    }

    /**
     * Tạo báo cáo tự động từ transcript đã chốt nếu phiên AVATAR chưa có báo cáo.
     * Báo cáo do AI service gửi lên sau đó có thể thay thế bản tự động này một lần.
     */
    private void ensureAvatarReport(InterviewSession session, List<InterviewAnswer> answers) {
        if (reportRepository.findBySession(session).isPresent()) {
            return;
        }
        AvatarReportEvaluator.Evaluation evaluation = avatarReportEvaluator.evaluate(answers);
        InterviewReport report = InterviewReport.builder()
                .session(session)
                .chuyenMon(evaluation.chuyenMon())
                .duAn(evaluation.duAn())
                .tuDuy(evaluation.tuDuy())
                .truyenDat(evaluation.truyenDat())
                .mucKhop(evaluation.mucKhop())
                .overallScore(evaluation.overallScore())
                .strengths(evaluation.strengths())
                .improvements(evaluation.improvements())
                .rubricVersion(evaluation.rubricVersion())
                .build();
        InterviewReport saved = reportRepository.save(report);
        log.info("Avatar auto evaluation stored: reportId={}, sessionId={}, overallScore={}",
                saved.getId(), session.getId(), saved.getOverallScore());
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
        InterviewReport existing = reportRepository.findBySession(session).orElse(null);
        if (existing != null && !AvatarReportEvaluator.AUTO_RUBRIC_VERSION.equals(existing.getRubricVersion())) {
            throw new BadRequestException("Báo cáo đã được nộp cho phiên này");
        }

        // Version tự động là cờ nội bộ, không phải giá trị client được phép nộp; nếu không chặn,
        // client có thể gửi lại đúng version này để thay báo cáo nhiều lần.
        String rubricVersion = request.getRubricVersion().trim();
        if (AvatarReportEvaluator.AUTO_RUBRIC_VERSION.equals(rubricVersion)) {
            throw new BadRequestException("rubricVersion không được dùng version của báo cáo tự động");
        }

        // Validate trước khi sửa báo cáo tự động: rubric sai không được làm mất báo cáo đang có.
        int[] dims = validateRubric(request.getRubric());
        int total = (int) Math.round((dims[0] + dims[1] + dims[2] + dims[3] + dims[4]) / (double) dims.length);

        InterviewReport report = existing != null
                ? existing
                : InterviewReport.builder().session(session).build();
        report.setChuyenMon(dims[0]);
        report.setDuAn(dims[1]);
        report.setTuDuy(dims[2]);
        report.setTruyenDat(dims[3]);
        report.setMucKhop(dims[4]);
        report.setOverallScore(total);
        report.setStrengths(request.getStrengths());
        report.setImprovements(request.getImprovements());
        report.setRubricVersion(rubricVersion);
        InterviewReport saved = reportRepository.save(report);
        log.info("Avatar evaluation stored: reportId={}, sessionId={}, overallScore={}, replacesAuto={}",
                saved.getId(), sessionId, total, existing != null);
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
        int correctCount = (int) answers.stream().filter(a -> a.getScore() != null && a.getScore() >= 100).count();
        return InterviewResultResponse.builder()
                .session(InterviewSessionResponse.from(session, answers.size()))
                .answers(answers.stream().map(InterviewAnswerResponse::from).toList())
                .transcript(answers.stream().map(InterviewTranscriptEntry::from).toList())
                .totalQuestions(session.getQuestions().size())
                .correctCount(correctCount)
                .score(session.getOverallScore())
                .report(reportRepository.findBySession(session)
                        .map(InterviewReportResponse::from)
                        .orElse(null))
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

    private Integer mapAnswerToOptionIndex(String answerText, List<String> options) {
        if (!isPresent(answerText) || options == null || options.isEmpty()) {
            return null;
        }
        String a = answerText.trim();
        // Ký tự A/B/C/D
        if (a.length() == 1) {
            char c = Character.toUpperCase(a.charAt(0));
            if (c >= 'A' && c < 'A' + options.size()) {
                return c - 'A';
            }
            if (Character.isDigit(c)) {
                int n = c - '1';
                if (n >= 0 && n < options.size()) {
                    return n;
                }
            }
        }
        // "A." hoặc "A)" ...
        if (a.length() >= 2 && (a.charAt(1) == '.' || a.charAt(1) == ')' || a.charAt(1) == ':')) {
            char c = Character.toUpperCase(a.charAt(0));
            if (c >= 'A' && c < 'A' + options.size()) {
                return c - 'A';
            }
        }
        // Khớp nội dung đáp án
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).equalsIgnoreCase(a)) {
                return i;
            }
        }
        return null;
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
