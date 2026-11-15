package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.common.util.SkillKeys;
import com.aiinterview.platform.model.entity.Question;
import com.aiinterview.platform.model.enums.DifficultyLevel;
import com.aiinterview.platform.model.enums.QuestionType;
import com.aiinterview.platform.model.repository.QuestionRepository;
import com.aiinterview.platform.model.specification.QuestionSpecifications;
import com.aiinterview.platform.service.AIQuestionService;
import com.aiinterview.platform.service.QuestionPoolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionPoolServiceImpl implements QuestionPoolService {

    private static final Sort LEAST_USED_FIRST = Sort.by(Sort.Direction.ASC, "timesUsed", "id");

    private final QuestionRepository questionRepository;
    private final AIQuestionService aiQuestionService;

    @Value("${study.question-pool.refill-batch:20}")
    private int refillBatch;

    @Value("${study.question-pool.ceiling:200}")
    private int ceiling;

    @Override
    public List<Question> acquire(String position, String skillTag, DifficultyLevel difficulty,
                                  QuestionType type, String language, int needed, Long userId) {
        if (needed <= 0) {
            return List.of();
        }
        String skillKey = SkillKeys.normalize(skillTag);
        String cellLanguage = normalizeLanguage(language);
        Specification<Question> cell = QuestionSpecifications.cell(skillKey, difficulty, type, cellLanguage);

        List<Question> picked = new ArrayList<>(questionRepository.findBy(
                QuestionSpecifications.pool().and(cell).and(QuestionSpecifications.unseenBy(userId)),
                query -> query.sortBy(LEAST_USED_FIRST).limit(needed).all()));
        int reused = picked.size();

        int poolRowsSaved = 0;
        int shortfall = needed - reused;
        if (shortfall > 0) {
            long poolSize = questionRepository.count(cell);
            int toGenerate = computeGenerationCount(shortfall, poolSize);
            if (toGenerate > 0) {
                List<Question> fresh = aiQuestionService.generateQuestions(
                        position, skillTag, Map.of(difficulty, 100), type, toGenerate);
                List<Question> saved = persistPoolRows(fresh, skillKey, cellLanguage);
                poolRowsSaved = saved.size();
                for (Question question : saved) {
                    if (picked.size() >= needed) {
                        break;
                    }
                    picked.add(question);
                }
            }
        }

        picked.forEach(this::markUsed);
        List<Question> assigned = picked.stream().map(this::copyForAssignment).toList();

        log.info("study pool cell={}|{}|{}|{} reused={} need={} generated={} assigned={}",
                skillKey, difficulty, type, cellLanguage, reused, needed, poolRowsSaved, assigned.size());
        return assigned;
    }

    // Chi sinh them khi con cho trong pool; batch lon hon shortfall de giam so lan goi AI
    int computeGenerationCount(int shortfall, long poolSize) {
        int room = (int) Math.max(0L, (long) ceiling - poolSize);
        return Math.min(Math.max(shortfall, refillBatch), room);
    }

    private List<Question> persistPoolRows(List<Question> candidates, String skillKey, String language) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        Map<String, Question> byFingerprint = new LinkedHashMap<>();
        for (Question candidate : candidates) {
            if (candidate.getQuestionText() == null || candidate.getQuestionText().isBlank()) {
                continue;
            }
            String fingerprint = SkillKeys.fingerprint(skillKey, candidate.getDifficulty(),
                    candidate.getQuestionType(), language, candidate.getQuestionText());
            candidate.setTopic(null);
            candidate.setSkillKey(skillKey);
            candidate.setLanguage(language);
            candidate.setFingerprint(fingerprint);
            candidate.setCacheKey(null);
            candidate.setPoolQuestionId(null);
            candidate.setTimesUsed(0);
            byFingerprint.putIfAbsent(fingerprint, candidate);
        }
        if (byFingerprint.isEmpty()) {
            return List.of();
        }

        Set<String> existing = new HashSet<>();
        for (Question row : questionRepository.findByFingerprintIn(byFingerprint.keySet())) {
            existing.add(row.getFingerprint());
        }

        List<Question> fresh = byFingerprint.entrySet().stream()
                .filter(entry -> !existing.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        if (fresh.isEmpty()) {
            return List.of();
        }
        return questionRepository.saveAll(fresh);
    }

    private void markUsed(Question question) {
        int used = question.getTimesUsed() == null ? 0 : question.getTimesUsed();
        question.setTimesUsed(used + 1);
    }

    // Pool row giu nguyen topic_id = null de con tai su dung; topic nhan mot ban sao rieng
    private Question copyForAssignment(Question source) {
        return Question.builder()
                .topic(null)
                .skillTag(source.getSkillTag())
                .skillKey(source.getSkillKey())
                .language(source.getLanguage())
                .difficulty(source.getDifficulty())
                .questionType(source.getQuestionType())
                .questionText(source.getQuestionText())
                .options(source.getOptions())
                .correctAnswer(source.getCorrectAnswer())
                .explanation(source.getExplanation())
                .aiGenerated(source.getAiGenerated())
                .source(source.getSource())
                .cacheKey(null)
                .fingerprint(null)
                .poolQuestionId(source.getId())
                .timesUsed(0)
                .build();
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "en";
        }
        return language.trim().toLowerCase(Locale.ROOT);
    }
}
