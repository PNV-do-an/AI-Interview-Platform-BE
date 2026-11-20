package com.aiinterview.platform.service.interview.impl;

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
import com.aiinterview.platform.model.entity.InterviewAnswer;
import com.aiinterview.platform.model.entity.InterviewReport;
import com.aiinterview.platform.model.entity.InterviewSession;
import com.aiinterview.platform.model.entity.JobDescription;
import com.aiinterview.platform.model.entity.QuizQuestion;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.InterviewMode;
import com.aiinterview.platform.model.enums.InterviewSessionStatus;
import com.aiinterview.platform.model.repository.InterviewAnswerRepository;
import com.aiinterview.platform.model.repository.InterviewReportRepository;
import com.aiinterview.platform.model.repository.InterviewSessionRepository;
import com.aiinterview.platform.model.repository.JobDescriptionRepository;
import com.aiinterview.platform.service.ai.AvatarReportEvaluator;
import com.aiinterview.platform.service.ai.InterviewAiEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * MockInterviewService — phiên luyện phỏng vấn (MOCK/KNOWLEDGE) và phiên avatar (AVATAR, spec 012).
 * <p>
 * Trọng tâm là các rule dễ vỡ khi refactor: chặn truy cập chéo user (403 chứ không 404), chấm trắc
 * nghiệm kể cả khi client gửi "B"/"2"/nội dung đáp án, upsert lượt hội thoại avatar, và rubric 5 chiều
 * phải hợp lệ TRƯỚC khi ghi đè báo cáo tự động.
 */
class MockInterviewServiceImplTest {

    private static final String AUTO = AvatarReportEvaluator.AUTO_RUBRIC_VERSION;

    private InterviewSessionRepository sessionRepository;
    private InterviewAnswerRepository answerRepository;
    private InterviewReportRepository reportRepository;
    private JobDescriptionRepository jobRepository;
    private InterviewAiEngine aiEngine;
    private AvatarReportEvaluator avatarReportEvaluator;
    private MockInterviewServiceImpl service;

    private User candidate;
    /** Báo cáo duy nhất trong "bảng" giả: đủ để getResult() thấy báo cáo vừa ghi. */
    private InterviewReport storedReport;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(InterviewSessionRepository.class);
        answerRepository = mock(InterviewAnswerRepository.class);
        reportRepository = mock(InterviewReportRepository.class);
        jobRepository = mock(JobDescriptionRepository.class);
        aiEngine = mock(InterviewAiEngine.class);
        avatarReportEvaluator = mock(AvatarReportEvaluator.class);
        service = new MockInterviewServiceImpl(sessionRepository, answerRepository, reportRepository,
                jobRepository, aiEngine, avatarReportEvaluator);

        candidate = User.builder().id(1L).email("candidate@aiinterview.vn").build();
        when(sessionRepository.save(any())).thenAnswer(call -> {
            InterviewSession s = call.getArgument(0);
            if (s.getId() == null) {
                s.setId(10L);
            }
            return s;
        });
        when(reportRepository.save(any())).thenAnswer(call -> {
            InterviewReport r = call.getArgument(0);
            if (r.getId() == null) {
                r.setId(20L);
            }
            storedReport = r;
            return r;
        });
        when(answerRepository.findBySessionOrderByQuestionIndexAsc(any())).thenReturn(List.of());
        when(reportRepository.findBySession(any())).thenAnswer(call -> Optional.ofNullable(storedReport));
    }

    // ── start ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AVATAR không chọn tin tuyển dụng: 400 kèm đúng thông điệp cho mode đó")
    void start_avatarRequiresJob() {
        assertThatThrownBy(() -> service.start(candidate, startRequest(InterviewMode.AVATAR, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Vui lòng chọn tin tuyển dụng cho phiên phỏng vấn avatar");
    }

    @Test
    @DisplayName("AVATAR: không sinh câu hỏi trước, topic lấy từ tin tuyển dụng")
    void start_avatarCreatesEmptyQuestionList() {
        when(jobRepository.findByIdAndDeletedAtIsNull(3L)).thenReturn(Optional.of(job()));

        InterviewSessionResponse response = service.start(candidate,
                startRequest(InterviewMode.AVATAR, 3L, null));

        assertThat(response.getMode()).isEqualTo(InterviewMode.AVATAR);
        assertThat(response.getTopic()).isEqualTo("Backend Engineer");
        assertThat(response.getQuestions()).isEmpty();
        assertThat(response.getQuestionCount()).isEqualTo(5);
        verifyNoInteractions(aiEngine);
    }

    @Test
    @DisplayName("MOCK không chọn tin tuyển dụng: 400 với thông điệp riêng của mode")
    void start_mockRequiresJob() {
        assertThatThrownBy(() -> service.start(candidate, startRequest(InterviewMode.MOCK, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Vui lòng chọn tin tuyển dụng để luyện phỏng vấn mô phỏng");
    }

    @Test
    @DisplayName("MOCK: câu hỏi do AI engine sinh theo tin tuyển dụng và số câu")
    void start_mockGeneratesQuestionsFromJob() {
        JobDescription job = job();
        when(jobRepository.findByIdAndDeletedAtIsNull(3L)).thenReturn(Optional.of(job));
        when(aiEngine.generateMockQuestions(job, 4)).thenReturn(List.of(quiz("Q1", List.of("a", "b"), 0)));

        InterviewSessionResponse response = service.start(candidate,
                startRequest(InterviewMode.MOCK, 3L, null, 4));

        assertThat(response.getQuestions()).hasSize(1);
        assertThat(response.getTopic()).isEqualTo("Backend Engineer");
        verify(aiEngine).generateMockQuestions(job, 4);
    }

    @Test
    @DisplayName("KNOWLEDGE: chủ đề bắt buộc, được trim rồi mới đưa cho AI")
    void start_knowledgeRequiresTopic() {
        assertThatThrownBy(() -> service.start(candidate,
                startRequest(InterviewMode.KNOWLEDGE, null, "   ")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Vui lòng nhập chủ đề luyện tập kiến thức");

        service.start(candidate, startRequest(InterviewMode.KNOWLEDGE, null, "  Collections  "));

        verify(aiEngine).generateKnowledgeQuestions("Collections", 5);
    }

    @Test
    @DisplayName("Tin tuyển dụng không tồn tại/đã xóa: 404")
    void start_missingJobThrows() {
        when(jobRepository.findByIdAndDeletedAtIsNull(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(candidate, startRequest(InterviewMode.MOCK, 3L, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy tin tuyển dụng");
    }

    // ── submitAnswer: chặn truy cập / trạng thái ───────────────────────────

    @Test
    @DisplayName("Phiên của người khác: 403 (không phải 404) để không lộ sự tồn tại")
    void submitAnswer_otherUsersSessionIsForbidden() {
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session(InterviewMode.MOCK, 2L)));

        assertThatThrownBy(() -> service.submitAnswer(candidate, 10L, answerRequest()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Bạn không có quyền truy cập phiên phỏng vấn này");
    }

    @Test
    @DisplayName("Phiên đã kết thúc: 400, không ghi thêm câu trả lời")
    void submitAnswer_completedSessionRejected() {
        InterviewSession session = session(InterviewMode.MOCK, 1L);
        session.setStatus(InterviewSessionStatus.COMPLETED);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.submitAnswer(candidate, 10L, answerRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Phiên phỏng vấn đã kết thúc");
        verify(answerRepository, never()).save(any());
    }

    @Test
    @DisplayName("MOCK/KNOWLEDGE không nhận câu hỏi do client tự gửi")
    void submitAnswer_rejectsClientSuppliedQuestion() {
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session(InterviewMode.MOCK, 1L)));
        SubmitAnswerRequest request = answerRequest();
        request.setQuestionText("Câu hỏi tự chế");

        assertThatThrownBy(() -> service.submitAnswer(candidate, 10L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Chế độ này không nhận câu hỏi từ client");
    }

    @Test
    @DisplayName("Đã trả lời hết câu hỏi: 400 thay vì IndexOutOfBounds")
    void submitAnswer_allQuestionsAnswered() {
        InterviewSession session = session(InterviewMode.MOCK, 1L);
        session.setCurrentQuestionIndex(2);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.submitAnswer(candidate, 10L, answerRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Tất cả câu hỏi đã được trả lời");
    }

    // ── submitAnswer: chấm trắc nghiệm ─────────────────────────────────────

    @Test
    @DisplayName("Chọn đúng option: 100 điểm, tiến sang câu tiếp theo")
    void submitAnswer_correctOptionScoresFull() {
        InterviewSession session = session(InterviewMode.MOCK, 1L);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        SubmitAnswerRequest request = answerRequest();
        request.setSelectedOptionIndex(1);

        InterviewAnswerResponse response = service.submitAnswer(candidate, 10L, request);

        assertThat(response.getScore()).isEqualTo(100);
        assertThat(response.getFeedback()).isEqualTo("Chính xác.");
        assertThat(session.getCurrentQuestionIndex()).isEqualTo(1);
        verify(sessionRepository).save(session);
    }

    @Test
    @DisplayName("Chọn sai: 0 điểm nhưng feedback vẫn nêu đáp án đúng")
    void submitAnswer_wrongOptionExplainsCorrectAnswer() {
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session(InterviewMode.MOCK, 1L)));
        SubmitAnswerRequest request = answerRequest();
        request.setSelectedOptionIndex(0);

        InterviewAnswerResponse response = service.submitAnswer(candidate, 10L, request);

        assertThat(response.getScore()).isZero();
        assertThat(response.getFeedback()).isEqualTo("Sai. Đáp án đúng là: ArrayList.");
    }

    @Test
    @DisplayName("Index option ngoài phạm vi: 400 'Vui lòng chọn một đáp án'")
    void submitAnswer_optionIndexOutOfRangeRejected() {
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session(InterviewMode.MOCK, 1L)));
        SubmitAnswerRequest request = answerRequest();
        request.setSelectedOptionIndex(9);

        assertThatThrownBy(() -> service.submitAnswer(candidate, 10L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Vui lòng chọn một đáp án");
    }

    @Test
    @DisplayName("Không gửi option cũng không gửi text: 400 thay vì chấm 0 điểm im lặng")
    void submitAnswer_requiresSomeChoice() {
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session(InterviewMode.MOCK, 1L)));

        assertThatThrownBy(() -> service.submitAnswer(candidate, 10L, answerRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Vui lòng chọn một đáp án");
    }

    @Test
    @DisplayName("Client gửi chữ 'B' / 'b)' / '2' thay vì index vẫn được hiểu đúng")
    void submitAnswer_mapsLetterAndDigitToOptionIndex() {
        for (String raw : List.of("B", "b)", "2", "arraylist")) {
            InterviewSession session = session(InterviewMode.MOCK, 1L);
            when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
            SubmitAnswerRequest request = answerRequest();
            request.setAnswerText(raw);

            InterviewAnswerResponse response = service.submitAnswer(candidate, 10L, request);

            assertThat(response.getScore())
                    .as("raw=%s", raw)
                    .isEqualTo(100);
        }
    }

    @Test
    @DisplayName("Chữ không khớp đáp án nào: 0 điểm và selectedOptionIndex null")
    void submitAnswer_unmatchedTextScoresZero() {
        InterviewSession session = session(InterviewMode.MOCK, 1L);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        SubmitAnswerRequest request = answerRequest();
        request.setAnswerText("không biết");

        InterviewAnswerResponse response = service.submitAnswer(candidate, 10L, request);

        assertThat(response.getScore()).isZero();
        assertThat(response.getFeedback()).startsWith("Sai. Đáp án đúng là:");
    }

    // ── submitAnswer: chế độ avatar ────────────────────────────────────────

    @Test
    @DisplayName("Avatar: câu trả lời trống bị chặn trước khi ghi")
    void avatarTurn_requiresAnswerText() {
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session(InterviewMode.AVATAR, 1L)));
        SubmitAnswerRequest request = new SubmitAnswerRequest();
        request.setQuestionText("Tell me about yourself");

        assertThatThrownBy(() -> service.submitAnswer(candidate, 10L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Câu trả lời không được để trống");
    }

    @Test
    @DisplayName("Avatar: không ghi trước được lượt chưa tới (retry request timeout không tạo lượt mới)")
    void avatarTurn_cannotWriteAhead() {
        InterviewSession session = session(InterviewMode.AVATAR, 1L);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        SubmitAnswerRequest request = avatarRequest(3, true);

        assertThatThrownBy(() -> service.submitAnswer(candidate, 10L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Không ghi được vào lượt chưa tới, hãy trả lời lượt hiện tại");
    }

    @Test
    @DisplayName("Avatar: chặn ở trần 40 lượt")
    void avatarTurn_maxTurnsEnforced() {
        InterviewSession session = session(InterviewMode.AVATAR, 1L);
        session.setCurrentQuestionIndex(MockInterviewServiceImpl.MAX_AVATAR_TURNS);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.submitAnswer(candidate, 10L, avatarRequest(40, true)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Phiên đã đạt số lượt tối đa, hãy kết thúc");
    }

    @Test
    @DisplayName("Avatar: lượt đã chốt không sửa được nữa")
    void avatarTurn_confirmedTurnIsLocked() {
        InterviewSession session = session(InterviewMode.AVATAR, 1L);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        InterviewAnswer confirmed = InterviewAnswer.builder()
                .session(session).questionIndex(0).confirmedAt(java.time.LocalDateTime.now()).build();
        when(answerRepository.findBySessionAndQuestionIndex(session, 0)).thenReturn(Optional.of(confirmed));

        assertThatThrownBy(() -> service.submitAnswer(candidate, 10L, avatarRequest(0, true)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Lượt này đã được chốt");
    }

    @Test
    @DisplayName("Avatar: confirm=false chỉ lưu nháp, không tăng con trỏ lượt")
    void avatarTurn_withoutConfirmDoesNotAdvance() {
        InterviewSession session = session(InterviewMode.AVATAR, 1L);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(answerRepository.findBySessionAndQuestionIndex(session, 0)).thenReturn(Optional.empty());

        service.submitAnswer(candidate, 10L, avatarRequest(0, false));

        ArgumentCaptor<InterviewAnswer> saved = ArgumentCaptor.forClass(InterviewAnswer.class);
        verify(answerRepository).save(saved.capture());
        assertThat(saved.getValue().getConfirmedAt()).isNull();
        assertThat(session.getCurrentQuestionIndex()).isZero();
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Avatar: confirm mặc định (null) = chốt lượt và tăng con trỏ")
    void avatarTurn_defaultConfirmAdvances() {
        InterviewSession session = session(InterviewMode.AVATAR, 1L);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(answerRepository.findBySessionAndQuestionIndex(session, 0)).thenReturn(Optional.empty());

        service.submitAnswer(candidate, 10L, avatarRequest(0, null));

        ArgumentCaptor<InterviewAnswer> saved = ArgumentCaptor.forClass(InterviewAnswer.class);
        verify(answerRepository).save(saved.capture());
        assertThat(saved.getValue().getConfirmedAt()).isNotNull();
        assertThat(session.getCurrentQuestionIndex()).isEqualTo(1);
        verify(sessionRepository).save(session);
    }

    // ── finish / result ────────────────────────────────────────────────────

    @Test
    @DisplayName("finish MOCK: overallScore = trung bình điểm các câu, phiên chuyển COMPLETED")
    void finish_mockAveragesScores() {
        InterviewSession session = session(InterviewMode.MOCK, 1L);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(answerRepository.findBySessionOrderByQuestionIndexAsc(session)).thenReturn(List.of(
                answer(0, 100), answer(1, 50)));

        InterviewResultResponse result = service.finish(candidate, 10L);

        assertThat(session.getStatus()).isEqualTo(InterviewSessionStatus.COMPLETED);
        assertThat(session.getCompletedAt()).isNotNull();
        assertThat(result.getScore()).isEqualTo(75);
        assertThat(result.getCorrectCount()).isEqualTo(1);
        assertThat(result.getTotalQuestions()).isEqualTo(2);
        verifyNoInteractions(avatarReportEvaluator);
    }

    @Test
    @DisplayName("finish phiên đã COMPLETED: không ghi lại, chỉ trả kết quả")
    void finish_alreadyCompletedIsIdempotent() {
        InterviewSession session = session(InterviewMode.MOCK, 1L);
        session.setStatus(InterviewSessionStatus.COMPLETED);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));

        service.finish(candidate, 10L);

        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("finish AVATAR: overallScore giữ null và báo cáo tự động được tạo ngay")
    void finish_avatarCreatesAutoReport() {
        InterviewSession session = session(InterviewMode.AVATAR, 1L);
        session.setCurrentQuestionIndex(1);
        List<InterviewAnswer> answers = List.of(answer(0, null));
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(answerRepository.findBySessionOrderByQuestionIndexAsc(session)).thenReturn(answers);
        when(avatarReportEvaluator.evaluate(answers)).thenReturn(
                new AvatarReportEvaluator.Evaluation(70, 60, 80, 50, 90, "mạnh", "yếu", AUTO));
        when(avatarReportEvaluator.evaluateAnswers(answers)).thenReturn(
                Map.of(0, new AvatarReportEvaluator.AnswerFeedback(75, "ổn")));

        InterviewResultResponse result = service.finish(candidate, 10L);

        // Điểm thật nằm ở interview_report, không phải trung bình 0 của answers.
        assertThat(session.getOverallScore()).isNull();
        ArgumentCaptor<InterviewReport> saved = ArgumentCaptor.forClass(InterviewReport.class);
        verify(reportRepository).save(saved.capture());
        assertThat(saved.getValue().getOverallScore()).isEqualTo(70);
        assertThat(saved.getValue().getRubricVersion()).isEqualTo(AUTO);
        assertThat(saved.getValue().getStrengths()).isEqualTo("mạnh");
        assertThat(result.getReport()).isNotNull();
        // Nhận xét từng câu được ghi vào các dòng answer hiện có.
        verify(answerRepository).saveAll(any());
    }

    @Test
    @DisplayName("finish AVATAR khi đã có báo cáo AI nộp: không tạo mới, không ghi đè")
    void finish_avatarKeepsSubmittedReport() {
        InterviewSession session = session(InterviewMode.AVATAR, 1L);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(reportRepository.findBySession(session)).thenReturn(Optional.of(
                InterviewReport.builder().id(5L).session(session).rubricVersion("v1-manual").build()));

        service.finish(candidate, 10L);

        verify(reportRepository, never()).save(any());
        verify(avatarReportEvaluator, never()).evaluate(any());
    }

    @Test
    @DisplayName("getReport chưa có báo cáo: 404 với thông điệp rõ ràng")
    void getReport_missingReturns404() {
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session(InterviewMode.AVATAR, 1L)));

        assertThatThrownBy(() -> service.getReport(candidate, 10L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Chưa có báo cáo cho phiên phỏng vấn này");
    }

    // ── submitEvaluation (rubric) ─────────────────────────────────────────

    @Test
    @DisplayName("Rubric: phiên chưa kết thúc hoặc không phải avatar bị chặn")
    void submitEvaluation_requiresCompletedAvatarSession() {
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session(InterviewMode.AVATAR, 1L)));
        assertThatThrownBy(() -> service.submitEvaluation(candidate, 10L, evaluation()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Phiên chưa kết thúc");

        InterviewSession mockSession = session(InterviewMode.MOCK, 1L);
        mockSession.setStatus(InterviewSessionStatus.COMPLETED);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(mockSession));
        assertThatThrownBy(() -> service.submitEvaluation(candidate, 10L, evaluation()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Rubric chỉ dùng cho phiên phỏng vấn avatar");
    }

    @Test
    @DisplayName("Rubric: báo cáo do người dùng nộp trước đó không được thay lần hai")
    void submitEvaluation_rejectsSecondSubmission() {
        InterviewSession session = completedAvatar();
        when(reportRepository.findBySession(session)).thenReturn(Optional.of(
                InterviewReport.builder().session(session).rubricVersion("v1-manual").build()));

        assertThatThrownBy(() -> service.submitEvaluation(candidate, 10L, evaluation()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Báo cáo đã được nộp cho phiên này");
    }

    @Test
    @DisplayName("Rubric: client không được mạo danh version của báo cáo tự động")
    void submitEvaluation_rejectsAutoRubricVersion() {
        InterviewSession session = completedAvatar();
        InterviewEvaluationRequest request = evaluation();
        request.setRubricVersion(AUTO);

        assertThatThrownBy(() -> service.submitEvaluation(candidate, 10L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("rubricVersion không được dùng version của báo cáo tự động");
        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rubric thiếu chiều: 400 nêu tên chiều thiếu, không ghi đè báo cáo cũ")
    void submitEvaluation_reportsMissingDimensions() {
        InterviewSession session = completedAvatar();
        Map<String, Object> rubric = new LinkedHashMap<>();
        rubric.put("chuyenMon", 80);
        rubric.put("duAn", 70);
        InterviewEvaluationRequest request = evaluation();
        request.setRubric(rubric);

        assertThatThrownBy(() -> service.submitEvaluation(candidate, 10L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("thiếu chiều")
                .hasMessageContaining("tuDuy");
        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rubric sai kiểu (chuỗi, số lẻ, ngoài 0..100) bị liệt kê là không hợp lệ")
    void submitEvaluation_rejectsNonIntegerOrOutOfRangeScores() {
        for (Object bad : List.of("80", 80.5, -1, 101)) {
            InterviewSession session = completedAvatar();
            Map<String, Object> rubric = fullRubric();
            rubric.put("tuDuy", bad);
            InterviewEvaluationRequest request = evaluation();
            request.setRubric(rubric);

            assertThatThrownBy(() -> service.submitEvaluation(candidate, 10L, request))
                    .as("value=%s", bad)
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("không phải số nguyên 0..100");
        }
    }

    @Test
    @DisplayName("Rubric hợp lệ: overallScore = trung bình 5 chiều, ghi đè báo cáo tự động")
    void submitEvaluation_storesManualRubric() {
        InterviewSession session = completedAvatar();
        when(reportRepository.findBySession(session)).thenReturn(Optional.of(
                InterviewReport.builder().id(5L).session(session).rubricVersion(AUTO).build()));
        InterviewAnswer answer = answer(0, 40);
        when(answerRepository.findBySessionOrderByQuestionIndexAsc(session)).thenReturn(List.of(answer));

        InterviewEvaluationRequest request = evaluation();
        InterviewEvaluationRequest.AnswerEvaluation evaluation =
                new InterviewEvaluationRequest.AnswerEvaluation();
        evaluation.setIndex(0);
        evaluation.setScore(95);
        evaluation.setFeedback("x".repeat(2100));
        request.setAnswers(List.of(evaluation));

        InterviewReportResponse response = service.submitEvaluation(candidate, 10L, request);

        assertThat(response.getOverallScore()).isEqualTo(70); // (80+70+60+50+90)/5
        assertThat(response.getRubricVersion()).isEqualTo("v1-manual");
        assertThat(response.getStrengths()).isEqualTo("mạnh");
        // Rubric ghi đè báo cáo tự động (cùng bản ghi), không tạo báo cáo thứ hai.
        verify(reportRepository).save(org.mockito.ArgumentMatchers.argThat(
                r -> r.getId().equals(5L) && r.getOverallScore() == 70));
        // Feedback từng câu được cắt về 2000 ký tự.
        assertThat(answer.getScore()).isEqualTo(95);
        assertThat(answer.getFeedback()).hasSize(2000);
    }

    @Test
    @DisplayName("Index câu trả lời lạ trong rubric bị bỏ qua, không tạo dòng mới")
    void submitEvaluation_ignoresUnknownAnswerIndex() {
        completedAvatar();
        InterviewEvaluationRequest request = evaluation();
        InterviewEvaluationRequest.AnswerEvaluation evaluation =
                new InterviewEvaluationRequest.AnswerEvaluation();
        evaluation.setIndex(99);
        evaluation.setScore(90);
        request.setAnswers(List.of(evaluation));

        service.submitEvaluation(candidate, 10L, request);

        verify(answerRepository, never()).saveAll(any());
    }

    // ── deleteSession ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Xóa phiên: báo cáo và câu trả lời phải xóa trước để không vướng FK")
    void deleteSession_removesChildrenFirst() {
        InterviewSession session = session(InterviewMode.MOCK, 1L);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        List<InterviewAnswer> answers = List.of(answer(0, 100));
        when(answerRepository.findBySessionOrderByQuestionIndexAsc(session)).thenReturn(answers);

        service.deleteSession(candidate, 10L);

        verify(reportRepository).deleteBySession(session);
        verify(answerRepository).deleteAll(answers);
        verify(sessionRepository).delete(session);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private InterviewSession completedAvatar() {
        InterviewSession session = session(InterviewMode.AVATAR, 1L);
        session.setStatus(InterviewSessionStatus.COMPLETED);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        return session;
    }

    private InterviewSession session(InterviewMode mode, Long ownerId) {
        InterviewSession session = new InterviewSession();
        session.setId(10L);
        session.setCandidate(User.builder().id(ownerId).build());
        session.setMode(mode);
        session.setStatus(InterviewSessionStatus.IN_PROGRESS);
        session.setQuestionCount(5);
        List<QuizQuestion> questions = new ArrayList<>();
        questions.add(quiz("Which is a List?", List.of("HashMap", "ArrayList"), 1));
        questions.add(quiz("Second?", List.of("x", "y"), 0));
        session.setQuestions(questions);
        if (mode == InterviewMode.AVATAR || mode == InterviewMode.MOCK) {
            session.setJob(job());
            session.setTopic("Backend Engineer");
        }
        return session;
    }

    private static JobDescription job() {
        return JobDescription.builder().id(3L).title("Backend Engineer").build();
    }

    private static QuizQuestion quiz(String question, List<String> options, int correct) {
        return QuizQuestion.of(question, options, correct);
    }

    private static InterviewAnswer answer(int index, Integer score) {
        return InterviewAnswer.builder()
                .session(new InterviewSession())
                .questionIndex(index)
                .question("Q" + index)
                .answerText("A" + index)
                .score(score)
                .build();
    }

    private static StartInterviewRequest startRequest(InterviewMode mode, Long jobId, String topic) {
        return startRequest(mode, jobId, topic, 5);
    }

    private static StartInterviewRequest startRequest(InterviewMode mode, Long jobId, String topic, int count) {
        StartInterviewRequest request = new StartInterviewRequest();
        request.setMode(mode);
        request.setJobId(jobId);
        request.setTopic(topic);
        request.setQuestionCount(count);
        return request;
    }

    private static SubmitAnswerRequest answerRequest() {
        return new SubmitAnswerRequest();
    }

    private static SubmitAnswerRequest avatarRequest(Integer questionIndex, Boolean confirm) {
        SubmitAnswerRequest request = new SubmitAnswerRequest();
        request.setQuestionText("Tell me about a project");
        request.setAnswerText("I built X");
        request.setQuestionIndex(questionIndex);
        request.setConfirm(confirm);
        return request;
    }

    private static InterviewEvaluationRequest evaluation() {
        InterviewEvaluationRequest request = new InterviewEvaluationRequest();
        request.setRubric(fullRubric());
        request.setRubricVersion("v1-manual");
        request.setStrengths("mạnh");
        request.setImprovements("yếu");
        return request;
    }

    private static Map<String, Object> fullRubric() {
        Map<String, Object> rubric = new LinkedHashMap<>();
        rubric.put("chuyenMon", 80);
        rubric.put("duAn", 70);
        rubric.put("tuDuy", 60);
        rubric.put("truyenDat", 50);
        rubric.put("mucKhop", 90);
        return rubric;
    }
}
