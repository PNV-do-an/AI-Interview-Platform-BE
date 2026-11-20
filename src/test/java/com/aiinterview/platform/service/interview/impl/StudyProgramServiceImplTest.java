package com.aiinterview.platform.service.interview.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.model.dto.request.GenerateProgramRequest;
import com.aiinterview.platform.model.dto.request.SubmitStudyAnswerRequest;
import com.aiinterview.platform.model.dto.response.AnswerEvaluationResponse;
import com.aiinterview.platform.model.dto.response.CvParseResponse;
import com.aiinterview.platform.model.dto.response.StudyProgramResponse;
import com.aiinterview.platform.model.dto.response.TopicResponse;
import com.aiinterview.platform.model.entity.Question;
import com.aiinterview.platform.model.entity.StudyAnswer;
import com.aiinterview.platform.model.entity.StudyProgram;
import com.aiinterview.platform.model.entity.StudyTopic;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.entity.UserCv;
import com.aiinterview.platform.model.enums.DifficultyLevel;
import com.aiinterview.platform.model.enums.ProficiencyLevel;
import com.aiinterview.platform.model.enums.ProgramStatus;
import com.aiinterview.platform.model.enums.ProgramType;
import com.aiinterview.platform.model.enums.QuestionType;
import com.aiinterview.platform.model.repository.QuestionRepository;
import com.aiinterview.platform.model.repository.StudyAnswerRepository;
import com.aiinterview.platform.model.repository.StudyProgramRepository;
import com.aiinterview.platform.model.repository.StudyTopicRepository;
import com.aiinterview.platform.model.repository.UserCvRepository;
import com.aiinterview.platform.service.document.CvService;
import com.aiinterview.platform.service.interview.AIQuestionService;
import com.aiinterview.platform.service.interview.QuestionPoolService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * StudyProgram — biến CV đã phân tích thành chương trình ôn tập (topic + câu hỏi) và theo dõi tiến độ.
 * <p>
 * Ba nhóm hợp đồng được kiểm tra: cách chọn topic từ CV (thứ tự ưu tiên, dedupe, giới hạn cấu hình),
 * cách chặn truy cập chéo user, và cách tính tiến độ (không vượt totalQuestions, chỉ COMPLETED khi
 * mọi topic xong).
 */
class StudyProgramServiceImplTest {

    private final AtomicLong ids = new AtomicLong(100);

    private UserCvRepository userCvRepository;
    private StudyProgramRepository programRepository;
    private StudyTopicRepository topicRepository;
    private QuestionRepository questionRepository;
    private StudyAnswerRepository studyAnswerRepository;
    private CvService cvService;
    private AIQuestionService aiQuestionService;
    private QuestionPoolService questionPoolService;
    private StudyProgramServiceImpl service;

    private User user;

    @BeforeEach
    void setUp() {
        userCvRepository = mock(UserCvRepository.class);
        programRepository = mock(StudyProgramRepository.class);
        topicRepository = mock(StudyTopicRepository.class);
        questionRepository = mock(QuestionRepository.class);
        studyAnswerRepository = mock(StudyAnswerRepository.class);
        cvService = mock(CvService.class);
        aiQuestionService = mock(AIQuestionService.class);
        questionPoolService = mock(QuestionPoolService.class);
        service = new StudyProgramServiceImpl(userCvRepository, programRepository, topicRepository,
                questionRepository, studyAnswerRepository, cvService, aiQuestionService,
                questionPoolService, new ObjectMapper());

        // @Value chỉ được inject khi chạy trong Spring; test phải tự set để không dính số 0.
        ReflectionTestUtils.setField(service, "maxTopicsPerProgram", 10);
        ReflectionTestUtils.setField(service, "questionsPerTopic", 5);
        ReflectionTestUtils.setField(service, "questionLanguage", "en");

        user = User.builder().id(1L).email("candidate@aiinterview.vn").build();
        when(userCvRepository.findByUserId(1L)).thenReturn(Optional.of(UserCv.builder().id(9L).build()));
        when(programRepository.save(any())).thenAnswer(call -> withId(call.getArgument(0)));
        when(topicRepository.save(any())).thenAnswer(call -> withId(call.getArgument(0)));
        when(questionPoolService.acquire(anyString(), anyString(), any(), any(), anyString(), anyInt(), anyLong()))
                .thenAnswer(call -> List.of(question("Q1"), question("Q2")));
    }

    // ── generateProgram: điều kiện đầu vào ──────────────────────────────────

    @Test
    @DisplayName("Chưa có CV: 400 kèm hướng dẫn upload CV, không gọi AI")
    void generate_requiresCv() {
        when(userCvRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateProgram(user, request("Backend", null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Chưa có CV. Hãy upload hoặc phân tích CV trước khi tạo chương trình ôn tập");

        verify(programRepository, never()).save(any());
    }

    @Test
    @DisplayName("CV không có skill và cũng không có knowledge gap: 400 thay vì tạo chương trình rỗng")
    void generate_requiresAtLeastOneTopic() {
        when(cvService.getCv(1L)).thenReturn(CvParseResponse.builder().targetPosition("Backend").build());

        assertThatThrownBy(() -> service.generateProgram(user, request(null, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("CV chưa có kỹ năng hoặc khoảng trống kiến thức nào để tạo chương trình");
    }

    @Test
    @DisplayName("Không xác định được vị trí ở cả request, CV và gợi ý: 400 nêu endpoint cần gọi")
    void generate_requiresTargetPosition() {
        when(cvService.getCv(1L)).thenReturn(cv(null, List.of(), skill("Java", ProficiencyLevel.ADVANCED)));

        assertThatThrownBy(() -> service.generateProgram(user, request(null, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("PUT /api/v1/cv/position");
    }

    // ── generateProgram: dựng topic ────────────────────────────────────────

    @Test
    @DisplayName("Skill trước, knowledge gap sau; mỗi topic được đánh số 1..n và gắn câu hỏi")
    void generate_buildsTopicsAndAttachesQuestions() {
        when(cvService.getCv(1L)).thenReturn(cv(null, List.of("Docker"),
                skill("Java", ProficiencyLevel.ADVANCED),
                skill("Spring", ProficiencyLevel.BEGINNER)));

        StudyProgramResponse response = service.generateProgram(user, request("Backend", 2, null));

        assertThat(response.getTitle()).isEqualTo("Ôn tập - Backend");
        assertThat(response.getTargetPosition()).isEqualTo("Backend");
        assertThat(response.getTotalTopics()).isEqualTo(3);
        assertThat(response.getCompletedTopics()).isZero();
        assertThat(response.getStatus()).isEqualTo(ProgramStatus.ACTIVE);
        assertThat(response.getDifficultyLevel()).isEqualTo(DifficultyLevel.MIXED);
        // Không truyền programType -> mặc định STUDY.
        assertThat(response.getProgramType()).isEqualTo(ProgramType.STUDY);

        ArgumentCaptor<StudyTopic> topics = ArgumentCaptor.forClass(StudyTopic.class);
        verify(topicRepository, times(6)).save(topics.capture()); // 3 lần tạo + 3 lần set totalQuestions

        // Mỗi topic được save hai lần (tạo rồi cập nhật totalQuestions) nên chỉ giữ lần xuất hiện đầu.
        Map<String, StudyTopic> firstBySkill = new LinkedHashMap<>();
        topics.getAllValues().forEach(t -> firstBySkill.putIfAbsent(t.getSkillTag(), t));

        assertThat(firstBySkill.keySet()).containsExactly("Java", "Spring", "Docker");
        assertThat(firstBySkill.get("Java").getTopicOrder()).isEqualTo(1);
        assertThat(firstBySkill.get("Spring").getTopicOrder()).isEqualTo(2);
        assertThat(firstBySkill.get("Docker").getTopicOrder()).isEqualTo(3);
        assertThat(firstBySkill.get("Java").getDifficulty()).isEqualTo(DifficultyLevel.ADVANCED);
        assertThat(firstBySkill.get("Spring").getDifficulty()).isEqualTo(DifficultyLevel.BEGINNER);
        assertThat(firstBySkill.get("Docker").getDifficulty()).isEqualTo(DifficultyLevel.INTERMEDIATE);
        assertThat(topics.getAllValues()).allSatisfy(t -> assertThat(t.getTotalQuestions()).isEqualTo(2));

        verify(questionRepository, times(6)).save(any(Question.class));
    }

    @Test
    @DisplayName("Vị trí lấy theo thứ tự request -> CV -> suggestedPositions[0]")
    void generate_positionFallbackChain() {
        when(cvService.getCv(1L)).thenReturn(cv("  Data Engineer  ", List.of(),
                skill("SQL", ProficiencyLevel.INTERMEDIATE)));
        assertThat(service.generateProgram(user, request(null, 1, null)).getTargetPosition())
                .isEqualTo("Data Engineer");

        when(cvService.getCv(1L)).thenReturn(CvParseResponse.builder()
                .suggestedPositions(List.of("ML Engineer"))
                .skills(List.of(skill("Python", ProficiencyLevel.INTERMEDIATE)))
                .build());
        assertThat(service.generateProgram(user, request("   ", 1, null)).getTargetPosition())
                .isEqualTo("ML Engineer");
    }

    @Test
    @DisplayName("Skill trùng (không phân biệt hoa/thường) chỉ tạo một topic")
    void generate_deduplicatesTopics() {
        when(cvService.getCv(1L)).thenReturn(cv(null, List.of("JAVA"),
                skill("Java", ProficiencyLevel.ADVANCED),
                skill("java", ProficiencyLevel.ADVANCED)));

        assertThat(service.generateProgram(user, request("Backend", 1, null)).getTotalTopics())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("maxTopicsPerProgram chặn số topic: CV 3 skill, cấu hình 2 -> chỉ tạo 2")
    void generate_respectsMaxTopics() {
        ReflectionTestUtils.setField(service, "maxTopicsPerProgram", 2);
        when(cvService.getCv(1L)).thenReturn(cv(null, List.of(),
                skill("A", ProficiencyLevel.ADVANCED),
                skill("B", ProficiencyLevel.ADVANCED),
                skill("C", ProficiencyLevel.ADVANCED)));

        assertThat(service.generateProgram(user, request("Backend", 1, null)).getTotalTopics())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("questionsPerTopic bị kẹp trong 1..50; bỏ trống thì dùng cấu hình")
    void generate_clampsQuestionsPerTopic() {
        when(cvService.getCv(1L)).thenReturn(cv(null, List.of(), skill("Java", ProficiencyLevel.ADVANCED)));

        service.generateProgram(user, request("Backend", 0, null));
        service.generateProgram(user, request("Backend", 999, null));
        service.generateProgram(user, request("Backend", null, null));

        verify(questionPoolService).acquire(eq("Backend"), eq("Java"), any(), eq(QuestionType.SHORT_ANSWER),
                eq("en"), eq(1), eq(1L));
        verify(questionPoolService).acquire(eq("Backend"), eq("Java"), any(), eq(QuestionType.SHORT_ANSWER),
                eq("en"), eq(50), eq(1L));
        verify(questionPoolService).acquire(eq("Backend"), eq("Java"), any(), eq(QuestionType.SHORT_ANSWER),
                eq("en"), eq(5), eq(1L));
    }

    @Test
    @DisplayName("programType do client chọn được giữ nguyên")
    void generate_keepsRequestedProgramType() {
        when(cvService.getCv(1L)).thenReturn(cv(null, List.of(), skill("Java", ProficiencyLevel.EXPERT)));

        StudyProgramResponse response = service.generateProgram(user,
                request("Backend", 1, ProgramType.INTERVIEW_PREP));

        assertThat(response.getProgramType()).isEqualTo(ProgramType.INTERVIEW_PREP);
        // EXPERT được nâng lên ADVANCED, không phải INTERMEDIATE.
        verify(questionPoolService).acquire(anyString(), eq("Java"), eq(DifficultyLevel.ADVANCED),
                any(), anyString(), anyInt(), anyLong());
    }

    // ── đọc dữ liệu ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Chương trình của user khác bị coi là không tồn tại (404, không lộ id)")
    void getProgramDetail_notOwnedReturns404() {
        when(programRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProgramDetail(1L, 5L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Chương trình ôn tập không tồn tại");
    }

    @Test
    @DisplayName("Topic của user khác bị 404 (kiểm tra qua program.user.id)")
    void getTopicQuestions_notOwnedReturns404() {
        when(topicRepository.findById(7L)).thenReturn(Optional.of(topic(program(2L), 1, 3)));

        assertThatThrownBy(() -> service.getTopicQuestions(1L, 7L, org.springframework.data.domain.PageRequest.of(0, 10)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Topic không tồn tại");
        verify(questionRepository, never()).findByTopicId(anyLong(), any());
    }

    @Test
    @DisplayName("Options JSON của câu MCQ được parse; JSON hỏng thì trả list rỗng, không nổ")
    void getTopicQuestions_parsesOptionsSafely() {
        StudyTopic topic = topic(program(1L), 1, 3);
        Question valid = question("Q1");
        valid.setOptions("[{\"key\":\"A\",\"text\":\"yes\"}]");
        Question broken = question("Q2");
        broken.setOptions("{not-json");
        when(topicRepository.findById(7L)).thenReturn(Optional.of(topic));
        when(questionRepository.findByTopicId(eq(7L), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(valid, broken)));

        var page = service.getTopicQuestions(1L, 7L, org.springframework.data.domain.PageRequest.of(0, 10));

        assertThat(page.getContent().get(0).getOptions()).hasSize(1);
        assertThat(page.getContent().get(0).getOptions().get(0).getKey()).isEqualTo("A");
        assertThat(page.getContent().get(1).getOptions()).isEmpty();
    }

    // ── submitStudyAnswer ──────────────────────────────────────────────────

    @Test
    @DisplayName("Câu hỏi không thuộc topic: 404, không gọi AI")
    void submitAnswer_questionMustBelongToTopic() {
        when(topicRepository.findById(7L)).thenReturn(Optional.of(topic(program(1L), 1, 3)));
        when(questionRepository.findByIdAndTopicId(42L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitStudyAnswer(1L, 7L, answerRequest(42L, "x")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Câu hỏi không tồn tại trong topic này");
        verify(aiQuestionService, never()).evaluateAnswer(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Lần trả lời đầu: tạo StudyAnswer attemptCount=1, lưu điểm và cờ đúng/sai")
    void submitAnswer_firstAttemptCreatesLog() {
        StudyTopic topic = topic(program(1L), 1, 3);
        Question question = question("JVM là gì?");
        when(topicRepository.findById(7L)).thenReturn(Optional.of(topic));
        when(questionRepository.findByIdAndTopicId(42L, 7L)).thenReturn(Optional.of(question));
        when(aiQuestionService.evaluateAnswer(any(), any(), any(), any())).thenReturn(
                AnswerEvaluationResponse.builder().score(8.0).isCorrect(true).feedback("ok").build());
        when(studyAnswerRepository.findByTopicIdAndQuestionId(7L, 42L)).thenReturn(Optional.empty());
        when(studyAnswerRepository.countByTopicId(7L)).thenReturn(1L);
        when(topicRepository.findByProgramIdOrderByTopicOrderAsc(anyLong())).thenAnswer(
                call -> List.of(topic));

        AnswerEvaluationResponse result = service.submitStudyAnswer(1L, 7L, answerRequest(42L, "Máy ảo Java"));

        assertThat(result.getScore()).isEqualTo(8.0);
        ArgumentCaptor<StudyAnswer> saved = ArgumentCaptor.forClass(StudyAnswer.class);
        verify(studyAnswerRepository).save(saved.capture());
        StudyAnswer log = saved.getValue();
        assertThat(log.getAnswer()).isEqualTo("Máy ảo Java");
        assertThat(log.getScore()).isEqualTo(8.0);
        assertThat(log.getIsCorrect()).isTrue();
        assertThat(log.getAttemptCount()).isEqualTo(1);
        assertThat(log.getSubmittedAt()).isNotNull();
        assertThat(log.getQuestion()).isSameAs(question);
        assertThat(log.getUser().getId()).isEqualTo(1L);

        // 1/3 câu -> tiến độ topic = 1; program chỉ được save một lần trong updateProgress
        assertThat(topic.getCompletedQuestions()).isEqualTo(1);
        verify(programRepository).save(any(StudyProgram.class));
    }

    @Test
    @DisplayName("Trả lời lại cùng câu: dùng lại bản ghi và tăng attemptCount")
    void submitAnswer_secondAttemptIncrements() {
        StudyTopic topic = topic(program(1L), 1, 3);
        StudyAnswer existing = StudyAnswer.builder().id(55L).topic(topic).attemptCount(1).build();
        Question question = question("Q");
        question.setId(42L);
        when(topicRepository.findById(7L)).thenReturn(Optional.of(topic));
        when(questionRepository.findByIdAndTopicId(42L, 7L)).thenReturn(Optional.of(question));
        when(aiQuestionService.evaluateAnswer(any(), any(), any(), any())).thenReturn(
                AnswerEvaluationResponse.builder().score(5.0).isCorrect(false).build());
        when(studyAnswerRepository.findByTopicIdAndQuestionId(7L, 42L)).thenReturn(Optional.of(existing));
        when(studyAnswerRepository.countByTopicId(7L)).thenReturn(1L);
        when(topicRepository.findByProgramIdOrderByTopicOrderAsc(anyLong())).thenReturn(List.of(topic));

        service.submitStudyAnswer(1L, 7L, answerRequest(42L, "sai"));

        verify(studyAnswerRepository).save(existing);
        assertThat(existing.getAttemptCount()).isEqualTo(2);
        assertThat(existing.getIsCorrect()).isFalse();
    }

    @Test
    @DisplayName("completedQuestions không vượt totalQuestions dù count trong DB lớn hơn")
    void submitAnswer_progressIsCappedAtTotal() {
        StudyTopic topic = topic(program(1L), 1, 3);
        when(topicRepository.findById(7L)).thenReturn(Optional.of(topic));
        when(questionRepository.findByIdAndTopicId(42L, 7L)).thenReturn(Optional.of(question("Q")));
        when(aiQuestionService.evaluateAnswer(any(), any(), any(), any())).thenReturn(
                AnswerEvaluationResponse.builder().score(9.0).isCorrect(true).build());
        when(studyAnswerRepository.findByTopicIdAndQuestionId(7L, 42L)).thenReturn(Optional.empty());
        when(studyAnswerRepository.countByTopicId(7L)).thenReturn(99L);
        when(topicRepository.findByProgramIdOrderByTopicOrderAsc(anyLong())).thenReturn(List.of(topic));

        service.submitStudyAnswer(1L, 7L, answerRequest(42L, "đúng"));

        assertThat(topic.getCompletedQuestions()).isEqualTo(3);
    }

    @Test
    @DisplayName("Topic cuối cùng hoàn tất: chương trình chuyển COMPLETED và ghi completedAt")
    void submitAnswer_marksProgramCompletedWhenAllTopicsDone() {
        StudyProgram program = program(1L);
        program.setTotalTopics(1);
        StudyTopic topic = topic(program, 1, 1);
        when(topicRepository.findById(7L)).thenReturn(Optional.of(topic));
        when(questionRepository.findByIdAndTopicId(42L, 7L)).thenReturn(Optional.of(question("Q")));
        when(aiQuestionService.evaluateAnswer(any(), any(), any(), any())).thenReturn(
                AnswerEvaluationResponse.builder().score(10.0).isCorrect(true).build());
        when(studyAnswerRepository.findByTopicIdAndQuestionId(7L, 42L)).thenReturn(Optional.empty());
        when(studyAnswerRepository.countByTopicId(7L)).thenReturn(1L);
        when(topicRepository.findByProgramIdOrderByTopicOrderAsc(1L)).thenReturn(List.of(topic));

        service.submitStudyAnswer(1L, 7L, answerRequest(42L, "đúng"));

        assertThat(program.getStatus()).isEqualTo(ProgramStatus.COMPLETED);
        assertThat(program.getCompletedAt()).isNotNull();
        assertThat(program.getCompletedTopics()).isEqualTo(1);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private <T> T withId(T entity) {
        long id = ids.incrementAndGet();
        if (entity instanceof StudyProgram p) {
            p.setId(id);
        } else if (entity instanceof StudyTopic t) {
            t.setId(id);
        } else if (entity instanceof Question q) {
            q.setId(id);
        }
        return entity;
    }

    private StudyProgram program(Long ownerId) {
        return StudyProgram.builder()
                .id(1L)
                .user(User.builder().id(ownerId).build())
                .totalTopics(3)
                .completedTopics(0)
                .status(ProgramStatus.ACTIVE)
                .build();
    }

    private StudyTopic topic(StudyProgram program, int order, int totalQuestions) {
        return StudyTopic.builder()
                .id(7L)
                .program(program)
                .topicOrder(order)
                .totalQuestions(totalQuestions)
                .completedQuestions(0)
                .build();
    }

    private static Question question(String text) {
        return Question.builder().questionText(text).timesUsed(0).build();
    }

    private static CvParseResponse.SkillData skill(String name, ProficiencyLevel level) {
        return CvParseResponse.SkillData.builder().name(name).proficiency(level).build();
    }

    private static CvParseResponse cv(String targetPosition, List<String> gaps,
                                      CvParseResponse.SkillData... skills) {
        return CvParseResponse.builder()
                .targetPosition(targetPosition)
                .skills(List.of(skills))
                .knowledgeGaps(gaps)
                .build();
    }

    private static GenerateProgramRequest request(String position, Integer perTopic, ProgramType type) {
        GenerateProgramRequest request = new GenerateProgramRequest();
        request.setTargetPosition(position);
        request.setQuestionsPerTopic(perTopic);
        request.setProgramType(type);
        return request;
    }

    private static SubmitStudyAnswerRequest answerRequest(Long questionId, String answer) {
        SubmitStudyAnswerRequest request = new SubmitStudyAnswerRequest();
        request.setQuestionId(questionId);
        request.setAnswer(answer);
        return request;
    }
}
