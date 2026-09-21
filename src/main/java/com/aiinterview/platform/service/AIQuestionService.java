package com.aiinterview.platform.service;

import com.aiinterview.platform.model.dto.response.AnswerEvaluationResponse;
import com.aiinterview.platform.model.entity.AiQuestionCache;
import com.aiinterview.platform.model.entity.Question;
import com.aiinterview.platform.model.enums.DifficultyLevel;
import com.aiinterview.platform.model.enums.QuestionType;

import java.util.List;
import java.util.Map;

public interface AIQuestionService {

    List<Question> generateQuestions(String position, String skill,
                                     Map<DifficultyLevel, Integer> distribution,
                                     QuestionType type, int count);

    AnswerEvaluationResponse evaluateAnswer(String questionText, String expectedAnswer,
                                            String questionExplanation, String userAnswer);

    AiQuestionCache saveToCache(Question question, String position);

    String buildCacheKey(String position, String skill, DifficultyLevel difficulty, String questionText);
}