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

class QuestionPoolServiceImplTest {

    private QuestionPoolServiceImpl service;
    private QuestionRepository questionRepository;
    private AIQuestionService aiQuestionService;

    @BeforeEach
    void setUp() {
        questionRepository = mock(QuestionRepository.class);
        aiQuestionService = mock(AIQuestionService.class);
        service = new QuestionPoolServiceImpl(questionRepository, aiQuestionService);
        ReflectionTestUtils.setField(service, "refillBatch", 20);
        ReflectionTestUtils.setField(service, "ceiling", 200);
    }

    @Test
    void refillsOnlyTheShortfallWhenItExceedsTheBatch() {
        assertThat(service.computeGenerationCount(30, 0)).isEqualTo(30);
    }

    @Test
    void refillsAtLeastOneBatchForASmallShortfall() {
        assertThat(service.computeGenerationCount(10, 0)).isEqualTo(20);
    }

    @Test
    void neverGeneratesPastTheCeiling() {
        assertThat(service.computeGenerationCount(5, 199)).isEqualTo(1);
    }

    @Test
    void generatesNothingWhenTheCellIsFull() {
        assertThat(service.computeGenerationCount(10, 200)).isZero();
    }

    @Test
    void acquireSkipsThePoolWhenNothingIsNeeded() {
        assertThat(service.acquire("Backend", "Docker", DifficultyLevel.BEGINNER,
                QuestionType.SHORT_ANSWER, "en", 0, 1L)).isEmpty();

        verifyNoInteractions(questionRepository, aiQuestionService);
    }
}
