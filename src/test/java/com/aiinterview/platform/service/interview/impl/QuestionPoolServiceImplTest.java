package com.aiinterview.platform.service.interview.impl;

import com.aiinterview.platform.model.enums.DifficultyLevel;
import com.aiinterview.platform.model.enums.QuestionType;
import com.aiinterview.platform.model.repository.QuestionRepository;
import com.aiinterview.platform.service.interview.AIQuestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Cách tính số câu cần sinh thêm cho một "ô" (vị trí × kỹ năng × độ khó × loại câu × ngôn ngữ).
 * Đây là hàm thuần nên test không cần repository: chỉ cần chốt đúng sàn (một lô) và trần
 * (ceiling) để job nền không sinh câu vô hạn hoặc sinh thiếu.
 *
 * <p>Mỗi test chia 3 khối comment: <b>Arrange</b> → <b>Act</b> → <b>Assert</b>.</p>
 */
class QuestionPoolServiceImplTest {

    private QuestionPoolServiceImpl service;
    private QuestionRepository questionRepository;
    private AIQuestionService aiQuestionService;

    @BeforeEach
    void setUp() {
        questionRepository = mock(QuestionRepository.class);
        aiQuestionService = mock(AIQuestionService.class);
        service = new QuestionPoolServiceImpl(questionRepository, aiQuestionService);
        // refillBatch / ceiling là @Value nên phải tiêm tay: lô 20 câu, trần 200 câu mỗi ô
        ReflectionTestUtils.setField(service, "refillBatch", 20);
        ReflectionTestUtils.setField(service, "ceiling", 200);
    }

    @Test
    void refillsOnlyTheShortfallWhenItExceedsTheBatch() {
        // Arrange: thiếu 30 câu, nhiều hơn một lô (20)

        // Act: shortfall 30 > batch 20 => sinh đúng phần thiếu
        int count = service.computeGenerationCount(30, 0);

        // Assert: 30 chứ không làm tròn lên 40 — tránh đốt token AI thừa
        assertThat(count).isEqualTo(30);
    }

    @Test
    void refillsAtLeastOneBatchForASmallShortfall() {
        // Arrange: chỉ thiếu 10 câu, ít hơn một lô

        // Act
        int count = service.computeGenerationCount(10, 0);

        // Assert: vẫn sinh tròn 1 lô (20) để lần sau khỏi gọi AI tiếp
        assertThat(count).isEqualTo(20);
    }

    @Test
    void neverGeneratesPastTheCeiling() {
        // Arrange: ô đã có 199 câu, chỉ còn chỗ cho 1 câu

        // Act
        int count = service.computeGenerationCount(5, 199);

        // Assert: bị cắt theo trần (200 - 199 = 1), không sinh tròn lô 20
        assertThat(count).isEqualTo(1);
    }

    @Test
    void generatesNothingWhenTheCellIsFull() {
        // Arrange: ô đã đầy (200 = ceiling)

        // Act
        int count = service.computeGenerationCount(10, 200);

        // Assert: 0 để job nền dừng, không gọi AI khi không còn chỗ
        assertThat(count).isZero();
    }

    @Test
    void acquireSkipsThePoolWhenNothingIsNeeded() {
        // Arrange: requested = 0, tức buổi phỏng vấn không cần câu nào

        // Act
        var acquired = service.acquire("Backend", "Docker", DifficultyLevel.BEGINNER,
                QuestionType.ESSAY, "en", 0, 1L);

        // Assert: trả rỗng và không chạm DB/AI — nhánh thoát sớm
        assertThat(acquired).isEmpty();
        verifyNoInteractions(questionRepository, aiQuestionService);
    }
}
