package com.aiinterview.platform.service.interview.impl;

import com.aiinterview.platform.common.exception.AIServiceException;
import com.aiinterview.platform.model.dto.response.AnswerEvaluationResponse;
import com.aiinterview.platform.model.entity.Question;
import com.aiinterview.platform.model.enums.DifficultyLevel;
import com.aiinterview.platform.model.enums.QuestionType;
import com.aiinterview.platform.service.infra.OpenAiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AIQuestionService — lớp bọc quanh LLM, nên phần đáng test là "hàng rào" chứ không phải câu chữ
 * của prompt: chia số câu theo tỉ lệ, không tin dữ liệu AI trả về, và luôn trả về đúng số câu được yêu cầu.
 * <p>
 * OpenAiClient được mock hoàn toàn: test không gọi mạng, không tốn token.
 */
class AIQuestionServiceImplTest {

    private static final Map<DifficultyLevel, Integer> ALL_INTERMEDIATE =
            Map.of(DifficultyLevel.INTERMEDIATE, 100);

    private final ObjectMapper mapper = new ObjectMapper();
    private OpenAiClient openAiClient;
    private AIQuestionServiceImpl service;

    @BeforeEach
    void setUp() {
        openAiClient = mock(OpenAiClient.class);
        service = new AIQuestionServiceImpl(openAiClient, mapper);
    }

    // ── generateQuestions: chặn đầu vào ─────────────────────────────────────

    @Test
    @DisplayName("Skill rỗng: dừng trước khi gọi AI (không tốn token)")
    void generate_blankSkillRejected() {
        assertThatThrownBy(() -> service.generateQuestions("Backend", "  ",
                ALL_INTERMEDIATE, QuestionType.SHORT_ANSWER, 3))
                .isInstanceOf(AIServiceException.class)
                .hasMessage("Skill trống, không thể generate câu hỏi");

        verifyNoInteractions(openAiClient);
    }

    @Test
    @DisplayName("Tỉ lệ toàn 0 (hoặc null) cũng bị từ chối vì không biết chia câu thế nào")
    void generate_emptyDistributionRejected() {
        Map<DifficultyLevel, Integer> zeroed = new LinkedHashMap<>();
        zeroed.put(DifficultyLevel.BEGINNER, 0);
        zeroed.put(DifficultyLevel.ADVANCED, null);

        assertThatThrownBy(() -> service.generateQuestions("Backend", "Java",
                zeroed, QuestionType.SHORT_ANSWER, 3))
                .isInstanceOf(AIServiceException.class)
                .hasMessage("Difficulty distribution rỗng, không thể generate câu hỏi");
    }

    // ── generateQuestions: chia câu ─────────────────────────────────────────

    @Test
    @DisplayName("Một mức 100%: gọi AI đúng một lần và map đầy đủ field nghiệp vụ")
    void generate_singleDifficultyMapsQuestionFields() throws Exception {
        when(openAiClient.chatJson(anyString())).thenReturn(json("""
                {"questions":[
                  {"skill_tag":"Java 17","difficulty":"ADVANCED","question_text":"  Explain records  ",
                   "correct_answer":"Immutable carriers","explanation":"Less boilerplate"}
                ]}
                """));

        List<Question> questions = service.generateQuestions("Backend Dev", "Java",
                Map.of(DifficultyLevel.INTERMEDIATE, 100), QuestionType.SHORT_ANSWER, 1);

        assertThat(questions).hasSize(1);
        Question q = questions.get(0);
        assertThat(q.getQuestionText()).isEqualTo("Explain records");
        assertThat(q.getSkillTag()).isEqualTo("Java 17");
        // AI được quyền nâng độ khó so với bucket, và giá trị đó phải được tôn trọng.
        assertThat(q.getDifficulty()).isEqualTo(DifficultyLevel.ADVANCED);
        assertThat(q.getQuestionType()).isEqualTo(QuestionType.SHORT_ANSWER);
        assertThat(q.getCorrectAnswer()).isEqualTo("Immutable carriers");
        assertThat(q.getExplanation()).isEqualTo("Less boilerplate");
        assertThat(q.getAiGenerated()).isTrue();
        assertThat(q.getSource()).isEqualTo("AI");
        assertThat(q.getTimesUsed()).isZero();
        assertThat(q.getCacheKey()).hasSize(64).matches("[0-9a-f]+");
        assertThat(q.getOptions()).isNull();
    }

    @Test
    @DisplayName("Độ khó lạ hoặc thiếu: rơi về bucket đang xử lý, không nổ exception")
    void generate_unknownDifficultyFallsBackToBucket() throws Exception {
        when(openAiClient.chatJson(anyString())).thenReturn(json("""
                {"questions":[
                  {"question_text":"Q1","difficulty":"impossible"},
                  {"question_text":"Q2"}
                ]}
                """));

        List<Question> questions = service.generateQuestions("Backend Dev", "Java",
                Map.of(DifficultyLevel.BEGINNER, 100), QuestionType.SHORT_ANSWER, 2);

        assertThat(questions).extracting(Question::getDifficulty)
                .containsOnly(DifficultyLevel.BEGINNER);
        // Không có skill_tag trong câu trả lời -> lấy skill của request.
        assertThat(questions).extracting(Question::getSkillTag).containsOnly("Java");
    }

    @Test
    @DisplayName("Hai mức 50/50 với count=4: hai lần gọi AI, mỗi lần 2 câu")
    void generate_splitsCountAcrossDifficulties() throws Exception {
        when(openAiClient.chatJson(anyString())).thenReturn(json("""
                {"questions":[{"question_text":"Q1"},{"question_text":"Q2"}]}
                """));

        Map<DifficultyLevel, Integer> distribution = new LinkedHashMap<>();
        distribution.put(DifficultyLevel.BEGINNER, 50);
        distribution.put(DifficultyLevel.ADVANCED, 50);

        List<Question> questions = service.generateQuestions("Backend Dev", "Java",
                distribution, QuestionType.SHORT_ANSWER, 4);

        assertThat(questions).hasSize(4);
        assertThat(questions).extracting(Question::getDifficulty)
                .containsExactly(DifficultyLevel.BEGINNER, DifficultyLevel.BEGINNER,
                        DifficultyLevel.ADVANCED, DifficultyLevel.ADVANCED);
        verify(openAiClient, times(2)).chatJson(anyString());
    }

    @Test
    @DisplayName("Tỉ lệ làm tròn thiếu câu (30/30, count=4): phần dư được chia bù cho từng bucket")
    void generate_leftoverQuestionsAreSpread() throws Exception {
        when(openAiClient.chatJson(anyString())).thenReturn(json("""
                {"questions":[{"question_text":"Q1"},{"question_text":"Q2"}]}
                """));

        Map<DifficultyLevel, Integer> distribution = new LinkedHashMap<>();
        distribution.put(DifficultyLevel.BEGINNER, 30);
        distribution.put(DifficultyLevel.ADVANCED, 30);

        List<Question> questions = service.generateQuestions("Backend Dev", "Java",
                distribution, QuestionType.SHORT_ANSWER, 4);

        assertThat(questions).hasSize(4);
    }

    @Test
    @DisplayName("Tỉ lệ làm tròn dư câu (33/33/33, count=5): vẫn không vượt quá count")
    void generate_neverExceedsRequestedCount() throws Exception {
        when(openAiClient.chatJson(anyString())).thenReturn(json("""
                {"questions":[{"question_text":"A"},{"question_text":"B"},
                              {"question_text":"C"},{"question_text":"D"}]}
                """));

        Map<DifficultyLevel, Integer> distribution = new LinkedHashMap<>();
        distribution.put(DifficultyLevel.BEGINNER, 33);
        distribution.put(DifficultyLevel.INTERMEDIATE, 33);
        distribution.put(DifficultyLevel.ADVANCED, 33);

        List<Question> questions = service.generateQuestions("Backend Dev", "Java",
                distribution, QuestionType.SHORT_ANSWER, 5);

        assertThat(questions).hasSize(5);
    }

    @Test
    @DisplayName("AI trả nhiều hơn yêu cầu: cắt đúng count")
    void generate_extraQuestionsAreTruncated() throws Exception {
        when(openAiClient.chatJson(anyString())).thenReturn(json("""
                {"questions":[{"question_text":"A"},{"question_text":"B"},
                              {"question_text":"C"},{"question_text":"D"},{"question_text":"E"}]}
                """));

        assertThat(service.generateQuestions("Backend Dev", "Java", ALL_INTERMEDIATE,
                QuestionType.SHORT_ANSWER, 2)).hasSize(2);
    }

    @Test
    @DisplayName("Thiếu field 'questions' / trả về object: báo dữ liệu AI không hợp lệ")
    void generate_missingQuestionsArrayRejected() throws Exception {
        when(openAiClient.chatJson(anyString())).thenReturn(json("{\"data\":[]}"));

        assertThatThrownBy(() -> service.generateQuestions("Backend Dev", "Java",
                ALL_INTERMEDIATE, QuestionType.SHORT_ANSWER, 2))
                .isInstanceOf(AIServiceException.class)
                .hasMessage("AI trả dữ liệu không hợp lệ: thiếu field 'questions'");
    }

    @Test
    @DisplayName("Câu hỏi trống bị bỏ qua; nếu tất cả đều trống thì báo 'không generate được'")
    void generate_blankQuestionTextIsSkipped() throws Exception {
        when(openAiClient.chatJson(anyString())).thenReturn(json("""
                {"questions":[{"question_text":"   "},{"correct_answer":"x"}]}
                """));

        assertThatThrownBy(() -> service.generateQuestions("Backend Dev", "Java",
                ALL_INTERMEDIATE, QuestionType.SHORT_ANSWER, 2))
                .isInstanceOf(AIServiceException.class)
                .hasMessage("Không generate được câu hỏi nào");
    }

    @Test
    @DisplayName("Lẫn câu tốt và câu hỏng: giữ câu tốt, không ném lỗi")
    void generate_keepsValidItemsAndSkipsBrokenOnes() throws Exception {
        when(openAiClient.chatJson(anyString())).thenReturn(json("""
                {"questions":[{"question_text":"  "},{"question_text":"Good one"}]}
                """));

        List<Question> questions = service.generateQuestions("Backend Dev", "Java",
                ALL_INTERMEDIATE, QuestionType.SHORT_ANSWER, 2);

        assertThat(questions).extracting(Question::getQuestionText).containsExactly("Good one");
    }

    @Test
    @DisplayName("MCQ: options được serialize thành JSON string để lưu 1 cột")
    void generate_mcqSerializesOptions() throws Exception {
        when(openAiClient.chatJson(anyString())).thenReturn(json("""
                {"questions":[{"question_text":"Which is a record?",
                  "options":[{"key":"A","text":"class"},{"key":"B","text":"record"}],
                  "correct_answer":"B"}]}
                """));

        List<Question> questions = service.generateQuestions("Backend Dev", "Java",
                ALL_INTERMEDIATE, QuestionType.MCQ, 1);

        String options = questions.get(0).getOptions();
        assertThat(options).contains("\"key\":\"A\"").contains("\"text\":\"record\"");
        assertThat(questions.get(0).getCorrectAnswer()).isEqualTo("B");
    }

    @Test
    @DisplayName("Câu tự luận không lưu options dù AI có trả về")
    void generate_nonMcqDropsOptions() throws Exception {
        when(openAiClient.chatJson(anyString())).thenReturn(json("""
                {"questions":[{"question_text":"Explain JVM",
                  "options":[{"key":"A","text":"x"}]}]}
                """));

        List<Question> questions = service.generateQuestions("Backend Dev", "Java",
                ALL_INTERMEDIATE, QuestionType.SHORT_ANSWER, 1);

        assertThat(questions.get(0).getOptions()).isNull();
    }

    // ── evaluateAnswer ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Chấm điểm: map đủ score/is_correct/feedback/strengths, giữ nguyên model answer")
    void evaluate_mapsAiVerdict() throws Exception {
        when(openAiClient.chatJsonEval(anyString())).thenReturn(json("""
                {"score":8.5,"is_correct":true,"feedback":"  Tốt  ",
                 "strengths":[" Rõ ràng ","","Sâu sắc"],"improvements":["Cần ví dụ"]}
                """));

        AnswerEvaluationResponse result = service.evaluateAnswer(
                "JVM là gì?", "Máy ảo Java", "Giải thích của đề", "JVM chạy bytecode");

        assertThat(result.getScore()).isEqualTo(8.5);
        assertThat(result.getIsCorrect()).isTrue();
        assertThat(result.getFeedback()).isEqualTo("  Tốt  ");
        // Danh sách được trim và bỏ phần tử rỗng.
        assertThat(result.getStrengths()).containsExactly("Rõ ràng", "Sâu sắc");
        assertThat(result.getImprovements()).containsExactly("Cần ví dụ");
        // modelAnswer/explanation là dữ liệu của mình, không phải AI trả về.
        assertThat(result.getModelAnswer()).isEqualTo("Máy ảo Java");
        assertThat(result.getExplanation()).isEqualTo("Giải thích của đề");
    }

    @Test
    @DisplayName("is_correct dạng số (1/0) của một số model vẫn được hiểu đúng")
    void evaluate_acceptsNumericIsCorrect() throws Exception {
        when(openAiClient.chatJsonEval(anyString())).thenReturn(json("""
                {"score":3,"is_correct":0,"feedback":"Sai"}
                """));

        AnswerEvaluationResponse result = service.evaluateAnswer("Q", "A", null, "B");

        assertThat(result.getIsCorrect()).isFalse();
        assertThat(result.getScore()).isEqualTo(3.0);
        assertThat(result.getExplanation()).isNull();
    }

    @Test
    @DisplayName("AI trả thiếu field: score=0, is_correct=null, feedback rỗng, list rỗng")
    void evaluate_missingFieldsDegradeGracefully() throws Exception {
        when(openAiClient.chatJsonEval(anyString())).thenReturn(json("{}"));

        AnswerEvaluationResponse result = service.evaluateAnswer("Q", "A", null, null);

        assertThat(result.getScore()).isZero();
        assertThat(result.getIsCorrect()).isNull();
        assertThat(result.getFeedback()).isEmpty();
        assertThat(result.getStrengths()).isEmpty();
        assertThat(result.getImprovements()).isEmpty();
    }

    // ── buildCacheKey ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Cache key là SHA-256 của position|skill|difficulty|question, ổn định giữa các lần gọi")
    void cacheKey_isStableSha256() throws Exception {
        String key = service.buildCacheKey("Backend Dev", "Java", DifficultyLevel.ADVANCED, "Explain records");
        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest("Backend Dev|Java|ADVANCED|Explain records".getBytes(StandardCharsets.UTF_8)));

        assertThat(key).isEqualTo(expected);
        assertThat(service.buildCacheKey("Backend Dev", "Java", DifficultyLevel.ADVANCED, "Explain records"))
                .isEqualTo(key);
        assertThat(service.buildCacheKey("Backend Dev", "Java", DifficultyLevel.ADVANCED, "Explain records!"))
                .isNotEqualTo(key);
    }

    @Test
    @DisplayName("Thành phần null không làm hỏng cache key (dùng chuỗi rỗng thay thế)")
    void cacheKey_handlesNullParts() {
        assertThat(service.buildCacheKey(null, null, null, null))
                .isEqualTo(service.buildCacheKey("", "", null, ""));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private JsonNode json(String raw) throws Exception {
        return mapper.readTree(raw);
    }
}
