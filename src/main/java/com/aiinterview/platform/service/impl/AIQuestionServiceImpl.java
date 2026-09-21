package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.common.exception.AIServiceException;
import com.aiinterview.platform.model.dto.response.AnswerEvaluationResponse;
import com.aiinterview.platform.model.entity.AiQuestionCache;
import com.aiinterview.platform.model.entity.Question;
import com.aiinterview.platform.model.enums.DifficultyLevel;
import com.aiinterview.platform.model.enums.QuestionType;
import com.aiinterview.platform.model.repository.AiQuestionCacheRepository;
import com.aiinterview.platform.service.AIQuestionService;
import com.aiinterview.platform.service.OpenAiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIQuestionServiceImpl implements AIQuestionService {

    private final OpenAiClient openAiClient;
    private final AiQuestionCacheRepository cacheRepository;
    private final ObjectMapper objectMapper;

    @Override
    public List<Question> generateQuestions(String position, String skill,
                                            Map<DifficultyLevel, Integer> distribution,
                                            QuestionType type, int count) {
        if (skill == null || skill.isBlank()) {
            throw new AIServiceException("Skill trống, không thể generate câu hỏi");
        }
        List<Question> questions = new ArrayList<>();
        int allocated = 0;

        for (Map.Entry<DifficultyLevel, Integer> entry : distribution.entrySet()) {
            DifficultyLevel difficulty = entry.getKey();
            int percent = entry.getValue() == null ? 0 : entry.getValue();
            int needed = (int) Math.round(count * percent / 100.0);
            if (needed <= 0) {
                continue;
            }

            List<AiQuestionCache> cached = getFromCache(position, skill, difficulty, type, needed);
            for (AiQuestionCache item : cached) {
                questions.add(toQuestion(item));
                item.setTimesUsed(item.getTimesUsed() + 1);
                cacheRepository.save(item);
            }
            allocated += cached.size();

            int remaining = needed - cached.size();
            if (remaining > 0) {
                JsonNode root = openAiClient.chatJson(buildQuestionPrompt(position, skill, difficulty, type, remaining));
                JsonNode arr = root.path("questions");
                if (!arr.isArray()) {
                    throw new AIServiceException("AI trả dữ liệu không hợp lệ: thiếu field 'questions'");
                }
                for (JsonNode item : arr) {
                    if (allocated >= count) {
                        break;
                    }
                    try {
                        Question q = parseQuestion(item, position, skill, difficulty, type);
                        AiQuestionCache saved = saveToCache(q, position);
                        q.setCacheKey(saved.getCacheKey());
                        questions.add(q);
                        allocated++;
                    } catch (Exception e) {
                        log.warn("Bỏ qua câu hỏi không hợp lệ từ AI: {}", e.getMessage());
                    }
                }
            }

            if (allocated >= count) {
                break;
            }
        }

        if (questions.isEmpty()) {
            throw new AIServiceException("Không generate được câu hỏi nào");
        }
        return questions;
    }

    @Override
    public AnswerEvaluationResponse evaluateAnswer(String questionText, String expectedAnswer,
                                                   String questionExplanation, String userAnswer) {
        String prompt = buildEvaluationPrompt(questionText, expectedAnswer, userAnswer);
        JsonNode node = openAiClient.chatJson(prompt);
        return mapEvaluation(node, expectedAnswer, questionExplanation);
    }

    @Override
    public AiQuestionCache saveToCache(Question question, String position) {
        String key = question.getCacheKey() != null
                ? question.getCacheKey()
                : buildCacheKey(position, question.getSkillTag(), question.getDifficulty(), question.getQuestionText());

        AiQuestionCache existing = cacheRepository.findByCacheKey(key).orElse(null);
        if (existing != null) {
            return existing;
        }

        AiQuestionCache cache = AiQuestionCache.builder()
                .cacheKey(key)
                .position(position)
                .skillTag(question.getSkillTag())
                .difficulty(question.getDifficulty())
                .questionType(question.getQuestionType())
                .questionText(question.getQuestionText())
                .options(question.getOptions())
                .correctAnswer(question.getCorrectAnswer())
                .explanation(question.getExplanation())
                .timesUsed(1)
                .build();
        return cacheRepository.save(cache);
    }

    @Override
    public String buildCacheKey(String position, String skill, DifficultyLevel difficulty, String questionText) {
        String base = (position == null ? "" : position) + "|"
                + (skill == null ? "" : skill) + "|"
                + (difficulty == null ? "" : difficulty) + "|"
                + (questionText == null ? "" : questionText);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(base.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private List<AiQuestionCache> getFromCache(String position, String skill,
                                               DifficultyLevel difficulty, QuestionType type, int limit) {
        List<AiQuestionCache> all = cacheRepository
                .findTop10ByPositionAndSkillTagAndDifficultyAndQuestionTypeOrderByTimesUsedDesc(
                        position, skill, difficulty, type);
        return all.size() > limit ? all.subList(0, limit) : all;
    }

    private Question toQuestion(AiQuestionCache cache) {
        return Question.builder()
                .skillTag(cache.getSkillTag())
                .difficulty(cache.getDifficulty())
                .questionType(cache.getQuestionType())
                .questionText(cache.getQuestionText())
                .options(cache.getOptions())
                .correctAnswer(cache.getCorrectAnswer())
                .explanation(cache.getExplanation())
                .aiGenerated(true)
                .source("AI")
                .cacheKey(cache.getCacheKey())
                .timesUsed(0)
                .build();
    }

    private Question parseQuestion(JsonNode item, String position, String skill,
                                   DifficultyLevel difficulty, QuestionType type) {
        String questionText = item.path("question_text").asText().trim();
        if (questionText.isEmpty()) {
            throw new IllegalArgumentException("Câu hỏi trống");
        }

        return Question.builder()
                .skillTag(item.hasNonNull("skill_tag") ? item.path("skill_tag").asText().trim() : skill)
                .difficulty(parseDifficulty(item.path("difficulty").asText(), difficulty))
                .questionType(type)
                .questionText(questionText)
                .options(serializeOptions(item, type))
                .correctAnswer(item.path("correct_answer").isMissingNode() ? null : item.path("correct_answer").asText())
                .explanation(item.path("explanation").isMissingNode() ? null : item.path("explanation").asText())
                .aiGenerated(true)
                .source("AI")
                .cacheKey(buildCacheKey(position, skill, difficulty, questionText))
                .timesUsed(0)
                .build();
    }

    private String serializeOptions(JsonNode item, QuestionType type) {
        JsonNode options = item.path("options");
        if (!options.isArray() || options.isEmpty() || type != QuestionType.MCQ) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(options);
        } catch (Exception e) {
            log.warn("Không serialize được options: {}", e.getMessage());
            return null;
        }
    }

    private DifficultyLevel parseDifficulty(String raw, DifficultyLevel fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return DifficultyLevel.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private AnswerEvaluationResponse mapEvaluation(JsonNode node, String modelAnswer, String explanation) {
        double score = node.path("score").isNumber() ? node.path("score").asDouble() : 0.0;

        Boolean isCorrect = null;
        if (node.path("is_correct").isBoolean()) {
            isCorrect = node.path("is_correct").asBoolean();
        } else if (node.path("is_correct").isNumber()) {
            isCorrect = node.path("is_correct").asInt() != 0;
        }

        return AnswerEvaluationResponse.builder()
                .score(score)
                .isCorrect(isCorrect)
                .feedback(requiredText(node, "feedback"))
                .modelAnswer(modelAnswer)
                .explanation(explanation)
                .strengths(stringList(node.path("strengths")))
                .improvements(stringList(node.path("improvements")))
                .build();
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText();
    }

    private List<String> stringList(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String text = item.asText().trim();
                if (!text.isEmpty()) {
                    result.add(text);
                }
            }
        }
        return result;
    }

    private String buildQuestionPrompt(String position, String skill,
                                       DifficultyLevel difficulty, QuestionType type, int count) {
        return """
                Generate {{count}} interview questions for the position: "{{position}}", covering the skill: "{{skill}}".
                Question type: {{type}}. Difficulty: {{difficulty}}.

                Rules:
                - Each question must be practical and specific to the skill "{{skill}}".
                - The question must fit an interview for the position "{{position}}".
                - Answer in the same language as the question content.
                - Provide a concise correct answer and a short explanation.

                Return a JSON object with exactly this structure:
                {
                  "questions": [
                    {
                      "skill_tag": "example skill",
                      "difficulty": "{{difficulty}}",
                      "question_text": "Your question here",
                      "correct_answer": "Expected answer",
                      "explanation": "Why this answer is correct",
                      "options": null
                    }
                  ]
                }

                STRICT: "questions" must be an array with exactly {{count}} items. No markdown, no extra text.
                """
                .replace("{{count}}", String.valueOf(count))
                .replace("{{position}}", position)
                .replace("{{skill}}", skill)
                .replace("{{type}}", type.name())
                .replace("{{difficulty}}", difficulty.name());
    }

    private String buildEvaluationPrompt(String questionText, String expectedAnswer, String userAnswer) {
        return """
                Evaluate the following study answer.
                IMPORTANT: Use the same language as the question and answers below.

                Question: {{question}}
                Expected Answer: {{expected}}
                User's Answer: {{answer}}

                Score the answer from 0 to 10 based on:
                1. Accuracy (correctness of the answer)
                2. Completeness (did they cover all aspects of the expected answer)
                3. Technical depth (understanding of concepts)

                Return a JSON object with exactly these fields:
                {
                  "score": 8.5,
                  "is_correct": true,
                  "feedback": "Clear, specific feedback in the same language as the answer",
                  "strengths": ["Strength 1", "Strength 2"],
                  "improvements": ["Improvement 1", "Improvement 2"]
                }

                STRICT: Only return this JSON object. No markdown, no extra text.
                """
                .replace("{{question}}", questionText == null ? "" : questionText)
                .replace("{{expected}}", expectedAnswer == null ? "" : expectedAnswer)
                .replace("{{answer}}", userAnswer == null ? "" : userAnswer);
    }
}