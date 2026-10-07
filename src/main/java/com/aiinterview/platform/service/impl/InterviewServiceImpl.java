package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.common.exception.AIServiceException;
import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.model.dto.request.StartInterviewRequest;
import com.aiinterview.platform.model.dto.request.SubmitInterviewAnswerRequest;
import com.aiinterview.platform.model.dto.response.AnswerEvaluationResponse;
import com.aiinterview.platform.model.dto.response.AudioAnalysisResult;
import com.aiinterview.platform.model.dto.response.CvParseResponse;
import com.aiinterview.platform.model.dto.response.EvaluationReportResponse;
import com.aiinterview.platform.model.dto.response.InterviewSessionResponse;
import com.aiinterview.platform.model.dto.response.QuestionResponse;
import com.aiinterview.platform.model.entity.InterviewAnswer;
import com.aiinterview.platform.model.entity.InterviewEvaluation;
import com.aiinterview.platform.model.entity.InterviewSession;
import com.aiinterview.platform.model.entity.Question;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.entity.UserCv;
import com.aiinterview.platform.model.enums.DifficultyLevel;
import com.aiinterview.platform.model.enums.QuestionType;
import com.aiinterview.platform.model.enums.SessionStatus;
import com.aiinterview.platform.model.enums.SessionType;
import com.aiinterview.platform.model.repository.InterviewAnswerRepository;
import com.aiinterview.platform.model.repository.InterviewEvaluationRepository;
import com.aiinterview.platform.model.repository.InterviewSessionRepository;
import com.aiinterview.platform.model.repository.QuestionRepository;
import com.aiinterview.platform.model.repository.UserCvRepository;
import com.aiinterview.platform.service.AIQuestionService;
import com.aiinterview.platform.service.AudioAnalysisService;
import com.aiinterview.platform.service.CvService;
import com.aiinterview.platform.service.InterviewService;
import com.aiinterview.platform.service.OpenAiClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewServiceImpl implements InterviewService {

    private static final int MAX_POOL_SKILLS = 6;
    private static final int MAX_ANSWER_LEN_IN_REPORT = 800;

    private final UserCvRepository userCvRepository;
    private final InterviewSessionRepository sessionRepository;
    private final InterviewAnswerRepository answerRepository;
    private final InterviewEvaluationRepository evaluationRepository;
    private final QuestionRepository questionRepository;
    private final CvService cvService;
    private final AIQuestionService aiQuestionService;
    private final AudioAnalysisService audioAnalysisService;
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    @Value("${interview.default-question-count:20}")
    private int defaultQuestionCount;

    @Value("${interview.min-question-count:5}")
    private int minQuestionCount;

    @Value("${interview.max-question-count:100}")
    private int maxQuestionCount;

    @Value("${interview.default-time-limit-minutes:60}")
    private int defaultTimeLimitMinutes;

    @Value("${interview.difficulty-distribution.beginner:20}")
    private int beginnerPercent;

    @Value("${interview.difficulty-distribution.intermediate:50}")
    private int intermediatePercent;

    @Value("${interview.difficulty-distribution.advanced:30}")
    private int advancedPercent;

    @Override
    @Transactional
    public InterviewSessionResponse startSession(User user, StartInterviewRequest request) {
        UserCv cv = userCvRepository.findByUserId(user.getId())
                .orElseThrow(() -> new BadRequestException(
                        "Chưa có CV. Hãy upload hoặc phân tích CV trước khi bắt đầu phiên phỏng vấn"));

        CvParseResponse cvData = cvService.getCv(user.getId());
        String position = resolvePosition(cvData);
        List<String> skills = resolveSkills(cvData, request.getFocusSkills());
        Map<DifficultyLevel, Integer> distribution = resolveDistribution(request.getQuestionDistribution());
        List<QuestionType> types = resolveTypes(request.getQuestionTypes());
        int total = resolveTotalQuestions(request.getTotalQuestions());

        List<Long> pool = buildQuestionPool(position, skills, distribution, types, total);

        SessionType sessionType = request.getSessionType() == null
                ? SessionType.FULL_SIMULATION : request.getSessionType();
        int timeLimit = request.getTimeLimitMinutes() == null
                ? defaultTimeLimitMinutes : Math.max(1, Math.min(240, request.getTimeLimitMinutes()));

        InterviewSession session = InterviewSession.builder()
                .user(user)
                .cv(cv)
                .sessionType(sessionType)
                .targetPosition(position)
                .totalQuestions(pool.size())
                .questionDistribution(toJson(distribution))
                .timeLimitMinutes(timeLimit)
                .status(SessionStatus.NOT_STARTED)
                .currentQuestionIndex(0)
                .questionPool(toJson(pool))
                .build();
        return toSessionResponse(sessionRepository.save(session));
    }

    @Override
    @Transactional
    public QuestionResponse getNextQuestion(Long userId, Long sessionId) {
        InterviewSession session = findOwnedSession(userId, sessionId);
        ensureNotFinished(session);

        List<Long> pool = readIds(session.getQuestionPool());
        int index = session.getCurrentQuestionIndex() == null ? 0 : session.getCurrentQuestionIndex();
        if (index >= pool.size()) {
            throw new ResourceNotFoundException(
                    "Đã hết câu hỏi. Hãy gọi POST /api/v1/interview/{id}/finish để kết thúc phiên");
        }

        session.setCurrentQuestionIndex(index + 1);
        if (session.getStatus() == SessionStatus.NOT_STARTED) {
            session.setStatus(SessionStatus.IN_PROGRESS);
            session.setStartedAt(LocalDateTime.now());
        }
        sessionRepository.save(session);

        Question question = questionRepository.findById(pool.get(index))
                .orElseThrow(() -> new ResourceNotFoundException("Câu hỏi không tồn tại"));
        return toQuestionResponse(question);
    }

    @Override
    @Transactional
    public AnswerEvaluationResponse submitAnswer(Long userId, Long sessionId, SubmitInterviewAnswerRequest request) {
        InterviewSession session = findOwnedSession(userId, sessionId);
        ensureNotFinished(session);

        List<Long> pool = readIds(session.getQuestionPool());
        if (request.getQuestionId() == null || !pool.contains(request.getQuestionId())) {
            throw new ResourceNotFoundException("Câu hỏi không thuộc phiên phỏng vấn này");
        }

        Question question = questionRepository.findById(request.getQuestionId())
                .orElseThrow(() -> new ResourceNotFoundException("Câu hỏi không tồn tại"));

        if (session.getStatus() == SessionStatus.NOT_STARTED) {
            session.setStatus(SessionStatus.IN_PROGRESS);
            session.setStartedAt(LocalDateTime.now());
        }
        sessionRepository.save(session);

        AnswerEvaluationResponse evaluation = aiQuestionService.evaluateAnswer(
                question.getQuestionText(), question.getCorrectAnswer(),
                question.getExplanation(), request.getTextAnswer());

        String audioFilePath = null;
        String transcription = null;
        Double eloquence = null;
        Double coherence = null;
        if (request.getAudioBase64() != null && !request.getAudioBase64().isBlank()) {
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(request.getAudioBase64());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Audio base64 không hợp lệ");
            }
            AudioAnalysisResult audio = audioAnalysisService.analyzeAudio(
                    bytes, request.getAudioFileName(), question.getQuestionText(), null);
            audioFilePath = audio.getAudioFilePath();
            transcription = audio.getTranscription();
            eloquence = audio.getEloquenceScore();
            coherence = audio.getCoherenceScore();
        }

        int order = (int) answerRepository.countBySessionId(session.getId()) + 1;
        InterviewAnswer answer = InterviewAnswer.builder()
                .session(session)
                .question(question)
                .answerOrder(order)
                .userTextAnswer(request.getTextAnswer())
                .audioFilePath(audioFilePath)
                .audioTranscription(transcription)
                .isCorrect(evaluation.getIsCorrect())
                .score(evaluation.getScore())
                .aiFeedback(evaluation.getFeedback())
                .eloquenceScore(eloquence)
                .coherenceScore(coherence)
                .build();
        answerRepository.save(answer);

        return evaluation;
    }

    @Override
    @Transactional
    public EvaluationReportResponse finishSession(Long userId, Long sessionId) {
        InterviewSession session = findOwnedSession(userId, sessionId);
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new BadRequestException("Phiên phỏng vấn chưa ở trạng thái IN_PROGRESS để kết thúc");
        }

        List<InterviewAnswer> answers = answerRepository.findBySessionIdOrderByAnswerOrderAsc(session.getId());
        if (answers.isEmpty()) {
            throw new BadRequestException("Chưa có câu trả lời nào để đánh giá tổng quan");
        }

        JsonNode node = openAiClient.chatJsonEval(buildOverallPrompt(session, answers));
        EvaluationReportResponse report = fromReportNode(node, answers);
        report.setSessionId(session.getId());

        InterviewEvaluation evaluation = evaluationRepository.findBySessionId(session.getId())
                .orElseGet(() -> InterviewEvaluation.builder().session(session).build());
        evaluation.setTotalScore(report.getTotalScore());
        evaluation.setAccuracyScore(report.getAccuracyScore());
        evaluation.setEloquenceScore(report.getEloquenceScore());
        evaluation.setTechnicalDepthScore(report.getTechnicalDepthScore());
        evaluation.setCommunicationScore(report.getCommunicationScore());
        evaluation.setStrongSkills(toJson(report.getStrongSkills()));
        evaluation.setWeakSkills(toJson(report.getWeakSkills()));
        evaluation.setImprovementSuggestions(report.getImprovementSuggestions());
        evaluation.setDetailedFeedback(report.getDetailedFeedback());
        evaluationRepository.save(evaluation);

        session.setStatus(SessionStatus.COMPLETED);
        session.setCompletedAt(LocalDateTime.now());
        session.setOverallScore(report.getTotalScore());
        session.setOverallFeedback(report.getDetailedFeedback());
        sessionRepository.save(session);

        return report;
    }

    @Override
    @Transactional(readOnly = true)
    public List<InterviewSessionResponse> getUserHistory(Long userId) {
        return sessionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toSessionResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EvaluationReportResponse getSessionReport(Long userId, Long sessionId) {
        InterviewSession session = findOwnedSession(userId, sessionId);
        InterviewEvaluation evaluation = evaluationRepository.findBySessionId(session.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Phiên phỏng vấn chưa có bảng đánh giá tổng quan"));
        List<InterviewAnswer> answers = answerRepository.findBySessionIdOrderByAnswerOrderAsc(session.getId());
        return fromEvaluation(evaluation, answers);
    }

    private List<Long> buildQuestionPool(String position, List<String> skills,
                                         Map<DifficultyLevel, Integer> distribution,
                                         List<QuestionType> types, int total) {
        if (skills.isEmpty()) {
            throw new BadRequestException(
                    "CV chưa có kỹ năng nào để xây dựng phiên phỏng vấn. Hãy chọn kỹ năng tập trung qua focusSkills");
        }
        List<QuestionType> effectiveTypes = (types == null || types.isEmpty())
                ? List.of(QuestionType.SHORT_ANSWER) : types;

        // Gom nhóm (skill × questionType) theo round-robin để chỉ gọi AI 1 lần/nhóm
        Map<String, Long> groupCounts = new LinkedHashMap<>();
        for (int i = 0; i < total; i++) {
            String skill = skills.get(i % skills.size());
            QuestionType type = effectiveTypes.get(i % effectiveTypes.size());
            String groupKey = skill + '\u0000' + type.name();
            groupCounts.merge(groupKey, 1L, Long::sum);
        }

        List<Long> ids = new ArrayList<>();
        for (Map.Entry<String, Long> entry : groupCounts.entrySet()) {
            String groupKey = entry.getKey();
            int sep = groupKey.indexOf('\u0000');
            String skill = groupKey.substring(0, sep);
            QuestionType type = QuestionType.valueOf(groupKey.substring(sep + 1));
            int count = Math.toIntExact(entry.getValue());

            List<Question> generated = aiQuestionService.generateQuestions(position, skill, distribution, type, count);
            for (Question q : generated) {
                Question saved = questionRepository.save(q);
                ids.add(saved.getId());
            }
        }
        if (ids.isEmpty()) {
            throw new AIServiceException("Không tạo được câu hỏi nào cho phiên phỏng vấn");
        }
        return ids.size() > total ? ids.subList(0, total) : ids;
    }

    private InterviewSession findOwnedSession(Long userId, Long sessionId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Phiên phỏng vấn không tồn tại"));
    }

    private void ensureNotFinished(InterviewSession session) {
        if (session.getStatus() == SessionStatus.COMPLETED) {
            throw new BadRequestException("Phiên phỏng vấn đã kết thúc");
        }
        if (session.getStatus() == SessionStatus.CANCELLED) {
            throw new BadRequestException("Phiên phỏng vấn đã bị huỷ");
        }
    }

    private String resolvePosition(CvParseResponse cvData) {
        if (cvData.getTargetPosition() != null && !cvData.getTargetPosition().isBlank()) {
            return cvData.getTargetPosition().trim();
        }
        if (cvData.getSuggestedPositions() != null && !cvData.getSuggestedPositions().isEmpty()) {
            return cvData.getSuggestedPositions().get(0);
        }
        throw new BadRequestException(
                "Chưa xác định được vị trí mục tiêu. Hãy chọn vị trí qua PUT /api/v1/cv/position");
    }

    private List<String> resolveSkills(CvParseResponse cvData, List<String> focusSkills) {
        List<String> skills = new ArrayList<>();
        if (focusSkills != null && !focusSkills.isEmpty()) {
            for (String skill : focusSkills) {
                if (skill == null || skill.isBlank()) {
                    continue;
                }
                String trimmed = skill.trim();
                if (!skills.contains(trimmed)) {
                    skills.add(trimmed);
                }
            }
            return skills;
        }
        if (cvData.getSkills() != null) {
            for (CvParseResponse.SkillData skill : cvData.getSkills()) {
                if (skill.getName() == null || skill.getName().isBlank()) {
                    continue;
                }
                String name = skill.getName().trim();
                if (!skills.contains(name)) {
                    skills.add(name);
                }
                if (skills.size() >= MAX_POOL_SKILLS) {
                    return skills;
                }
            }
        }
        if (cvData.getKnowledgeGaps() != null) {
            for (String gap : cvData.getKnowledgeGaps()) {
                if (gap == null || gap.isBlank()) {
                    continue;
                }
                String trimmed = gap.trim();
                if (!skills.contains(trimmed)) {
                    skills.add(trimmed);
                }
                if (skills.size() >= MAX_POOL_SKILLS) {
                    break;
                }
            }
        }
        return skills;
    }

    private Map<DifficultyLevel, Integer> resolveDistribution(Map<String, Integer> raw) {
        if (raw != null && !raw.isEmpty()) {
            Map<DifficultyLevel, Integer> result = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> entry : raw.entrySet()) {
                if (entry.getValue() == null || entry.getValue() <= 0) {
                    continue;
                }
                try {
                    DifficultyLevel difficulty = DifficultyLevel.valueOf(entry.getKey().trim().toUpperCase());
                    if (difficulty != DifficultyLevel.MIXED) {
                        result.put(difficulty, entry.getValue());
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("Bỏ qua difficulty không hợp lệ '{}'", entry.getKey());
                }
            }
            if (!result.isEmpty()) {
                return result;
            }
        }
        Map<DifficultyLevel, Integer> defaults = new LinkedHashMap<>();
        defaults.put(DifficultyLevel.BEGINNER, beginnerPercent);
        defaults.put(DifficultyLevel.INTERMEDIATE, intermediatePercent);
        defaults.put(DifficultyLevel.ADVANCED, advancedPercent);
        return defaults;
    }

    private List<QuestionType> resolveTypes(List<QuestionType> types) {
        if (types == null || types.isEmpty()) {
            return List.of(QuestionType.SHORT_ANSWER);
        }
        LinkedHashSet<QuestionType> result = new LinkedHashSet<>();
        for (QuestionType type : types) {
            if (type != null) {
                result.add(type);
            }
        }
        return result.isEmpty() ? List.of(QuestionType.SHORT_ANSWER) : List.copyOf(result);
    }

    private int resolveTotalQuestions(Integer requested) {
        if (requested == null) {
            return defaultQuestionCount;
        }
        return Math.max(minQuestionCount, Math.min(maxQuestionCount, requested));
    }

    private EvaluationReportResponse fromReportNode(JsonNode node, List<InterviewAnswer> answers) {
        List<EvaluationReportResponse.AnswerReport> reports = answers.stream()
                .map(a -> EvaluationReportResponse.AnswerReport.builder()
                        .questionId(a.getQuestion() == null ? null : a.getQuestion().getId())
                        .questionText(a.getQuestion() == null ? null : a.getQuestion().getQuestionText())
                        .score(a.getScore())
                        .feedback(a.getAiFeedback())
                        .isCorrect(a.getIsCorrect())
                        .eloquenceScore(a.getEloquenceScore())
                        .coherenceScore(a.getCoherenceScore())
                        .build())
                .toList();

        return EvaluationReportResponse.builder()
                .sessionId(null)
                .totalScore(numberValue(node, "total_score"))
                .accuracyScore(numberValue(node, "accuracy_score"))
                .eloquenceScore(numberValue(node, "eloquence_score"))
                .technicalDepthScore(numberValue(node, "technical_depth_score"))
                .communicationScore(numberValue(node, "communication_score"))
                .strongSkills(stringList(node.path("strong_skills")))
                .weakSkills(stringList(node.path("weak_skills")))
                .improvementSuggestions(textValue(node.path("improvement_suggestions")))
                .detailedFeedback(textValue(node.path("detailed_feedback")))
                .answers(reports)
                .build();
    }

    private EvaluationReportResponse fromEvaluation(InterviewEvaluation evaluation, List<InterviewAnswer> answers) {
        List<EvaluationReportResponse.AnswerReport> reports = answers.stream()
                .map(a -> EvaluationReportResponse.AnswerReport.builder()
                        .questionId(a.getQuestion() == null ? null : a.getQuestion().getId())
                        .questionText(a.getQuestion() == null ? null : a.getQuestion().getQuestionText())
                        .score(a.getScore())
                        .feedback(a.getAiFeedback())
                        .isCorrect(a.getIsCorrect())
                        .eloquenceScore(a.getEloquenceScore())
                        .coherenceScore(a.getCoherenceScore())
                        .build())
                .toList();

        return EvaluationReportResponse.builder()
                .sessionId(evaluation.getSession() == null ? null : evaluation.getSession().getId())
                .totalScore(evaluation.getTotalScore())
                .accuracyScore(evaluation.getAccuracyScore())
                .eloquenceScore(evaluation.getEloquenceScore())
                .technicalDepthScore(evaluation.getTechnicalDepthScore())
                .communicationScore(evaluation.getCommunicationScore())
                .strongSkills(readStringList(evaluation.getStrongSkills()))
                .weakSkills(readStringList(evaluation.getWeakSkills()))
                .improvementSuggestions(evaluation.getImprovementSuggestions())
                .detailedFeedback(evaluation.getDetailedFeedback())
                .answers(reports)
                .build();
    }

    private String buildOverallPrompt(InterviewSession session, List<InterviewAnswer> answers) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < answers.size(); i++) {
            InterviewAnswer answer = answers.get(i);
            String questionText = answer.getQuestion() == null ? "" : answer.getQuestion().getQuestionText();
            String userAnswer = answer.getUserTextAnswer() == null ? "" : answer.getUserTextAnswer();
            sb.append("\n").append(i + 1).append(". Question: ").append(truncate(questionText))
                    .append("\n   User's answer: ").append(truncate(userAnswer))
                    .append("\n   Score: ").append(answer.getScore() == null ? "N/A" : answer.getScore())
                    .append("/10");
            if (answer.getEloquenceScore() != null) {
                sb.append("\n   Eloquence: ").append(answer.getEloquenceScore())
                        .append(", Coherence: ").append(answer.getCoherenceScore());
            }
        }

        return """
                You are a senior technical interviewer. Review this mock interview for the position "{{position}}".

                Interview answers (in order):
                {{answers}}

                Evaluate the candidate overall and return a JSON object with exactly these fields:
                {
                  "total_score": 8.2,
                  "accuracy_score": 8.5,
                  "eloquence_score": 7.8,
                  "technical_depth_score": 8.0,
                  "communication_score": 8.2,
                  "strong_skills": ["Java", "Spring Boot"],
                  "weak_skills": ["System Design"],
                  "improvement_suggestions": "Specific advice to improve weak areas",
                  "detailed_feedback": "Overall feedback in the same language as the answers"
                }

                STRICT: Only return this JSON object. No markdown, no extra text.
                """
                .replace("{{position}}", session.getTargetPosition() == null ? "" : session.getTargetPosition())
                .replace("{{answers}}", sb.toString());
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > MAX_ANSWER_LEN_IN_REPORT
                ? text.substring(0, MAX_ANSWER_LEN_IN_REPORT) + "..."
                : text;
    }

    private List<Long> readIds(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {
            });
        } catch (Exception e) {
            log.warn("Không parse được question pool: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("Không parse được danh sách kỹ năng: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new AIServiceException("Không serialize được dữ liệu", e);
        }
    }

    private Double numberValue(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asDouble() : null;
    }

    private String textValue(JsonNode value) {
        return value.isMissingNode() || value.isNull() ? null : value.asText();
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

    private InterviewSessionResponse toSessionResponse(InterviewSession session) {
        return InterviewSessionResponse.builder()
                .id(session.getId())
                .sessionType(session.getSessionType())
                .targetPosition(session.getTargetPosition())
                .totalQuestions(session.getTotalQuestions())
                .timeLimitMinutes(session.getTimeLimitMinutes())
                .status(session.getStatus())
                .currentQuestionIndex(session.getCurrentQuestionIndex())
                .startedAt(session.getStartedAt())
                .completedAt(session.getCompletedAt())
                .overallScore(session.getOverallScore())
                .overallFeedback(session.getOverallFeedback())
                .questionDistribution(readStringMap(session.getQuestionDistribution()))
                .build();
    }

    private Map<String, Integer> readStringMap(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Integer>>() {
            });
        } catch (Exception e) {
            log.warn("Không parse được question distribution: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private QuestionResponse toQuestionResponse(Question question) {
        return QuestionResponse.builder()
                .id(question.getId())
                .skillTag(question.getSkillTag())
                .difficulty(question.getDifficulty())
                .questionType(question.getQuestionType())
                .questionText(question.getQuestionText())
                .options(parseOptions(question.getOptions()))
                .build();
    }

    private List<QuestionResponse.Option> parseOptions(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<QuestionResponse.Option>>() {
            });
        } catch (Exception e) {
            log.warn("Không parse được options của câu hỏi: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}