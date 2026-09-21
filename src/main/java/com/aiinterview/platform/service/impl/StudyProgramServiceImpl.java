package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.model.dto.request.GenerateProgramRequest;
import com.aiinterview.platform.model.dto.request.SubmitStudyAnswerRequest;
import com.aiinterview.platform.model.dto.response.AnswerEvaluationResponse;
import com.aiinterview.platform.model.dto.response.CvParseResponse;
import com.aiinterview.platform.model.dto.response.QuestionResponse;
import com.aiinterview.platform.model.dto.response.StudyProgramResponse;
import com.aiinterview.platform.model.dto.response.TopicResponse;
import com.aiinterview.platform.model.entity.Question;
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
import com.aiinterview.platform.model.repository.StudyProgramRepository;
import com.aiinterview.platform.model.repository.StudyTopicRepository;
import com.aiinterview.platform.model.repository.UserCvRepository;
import com.aiinterview.platform.service.AIQuestionService;
import com.aiinterview.platform.service.CvService;
import com.aiinterview.platform.service.StudyProgramService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudyProgramServiceImpl implements StudyProgramService {

    private final UserCvRepository userCvRepository;
    private final StudyProgramRepository programRepository;
    private final StudyTopicRepository topicRepository;
    private final QuestionRepository questionRepository;
    private final CvService cvService;
    private final AIQuestionService aiQuestionService;
    private final ObjectMapper objectMapper;

    @Value("${study.max-topics-per-program:10}")
    private int maxTopicsPerProgram;

    @Value("${study.questions-per-topic:10}")
    private int questionsPerTopic;

    @Override
    @Transactional
    public StudyProgramResponse generateProgram(User user, GenerateProgramRequest request) {
        UserCv cv = userCvRepository.findByUserId(user.getId())
                .orElseThrow(() -> new BadRequestException(
                        "Chưa có CV. Hãy upload hoặc phân tích CV trước khi tạo chương trình ôn tập"));

        CvParseResponse cvData = cvService.getCv(user.getId());
        String position = resolvePosition(request, cvData);

        List<TopicPlan> topics = buildTopics(cvData, maxTopicsPerProgram);
        if (topics.isEmpty()) {
            throw new BadRequestException(
                    "CV chưa có kỹ năng hoặc khoảng trống kiến thức nào để tạo chương trình");
        }

        int perTopic = resolveQuestionsPerTopic(request);

        ProgramType programType = request.getProgramType() == null ? ProgramType.STUDY : request.getProgramType();

        StudyProgram program = StudyProgram.builder()
                .user(user)
                .cv(cv)
                .title("Ôn tập - " + position)
                .description("Chương trình ôn tập cá nhân hóa cho vị trí " + position)
                .programType(programType)
                .targetPosition(position)
                .difficultyLevel(DifficultyLevel.MIXED)
                .status(ProgramStatus.ACTIVE)
                .totalTopics(0)
                .completedTopics(0)
                .build();
        program = programRepository.save(program);

        int order = 1;
        for (TopicPlan topicPlan : topics) {
            StudyTopic topic = StudyTopic.builder()
                    .program(program)
                    .title(topicPlan.title)
                    .description(topicPlan.description)
                    .topicOrder(order++)
                    .difficulty(topicPlan.difficulty)
                    .skillTag(topicPlan.skillTag)
                    .totalQuestions(0)
                    .completedQuestions(0)
                    .build();
            topic = topicRepository.save(topic);

            List<Question> questions = aiQuestionService.generateQuestions(
                    position, topicPlan.skillTag, Map.of(topicPlan.difficulty, 100),
                    QuestionType.SHORT_ANSWER, perTopic);

            int count = 0;
            for (Question q : questions) {
                q.setTopic(topic);
                questionRepository.save(q);
                count++;
            }
            topic.setTotalQuestions(count);
            topicRepository.save(topic);
        }

        program.setTotalTopics(topics.size());
        return toProgramResponse(programRepository.save(program));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudyProgramResponse> getUserPrograms(Long userId) {
        return programRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toProgramResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public StudyProgramResponse getProgramDetail(Long userId, Long programId) {
        StudyProgram program = findOwnedProgram(userId, programId);
        return toProgramResponse(program);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopicResponse> getProgramTopics(Long userId, Long programId) {
        findOwnedProgram(userId, programId);
        return topicRepository.findByProgramIdOrderByTopicOrderAsc(programId).stream()
                .map(this::toTopicResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<QuestionResponse> getTopicQuestions(Long userId, Long topicId, Pageable pageable) {
        StudyTopic topic = findOwnedTopic(userId, topicId);
        return questionRepository.findByTopicId(topic.getId(), pageable)
                .map(this::toQuestionResponse);
    }

    @Override
    @Transactional
    public AnswerEvaluationResponse submitStudyAnswer(Long userId, Long topicId, SubmitStudyAnswerRequest request) {
        StudyTopic topic = findOwnedTopic(userId, topicId);

        Question question = questionRepository.findByIdAndTopicId(request.getQuestionId(), topicId)
                .orElseThrow(() -> new ResourceNotFoundException("Câu hỏi không tồn tại trong topic này"));

        AnswerEvaluationResponse evaluation = aiQuestionService.evaluateAnswer(
                question.getQuestionText(), question.getCorrectAnswer(),
                question.getExplanation(), request.getAnswer());

        updateProgress(topic);
        return evaluation;
    }

    private StudyProgram findOwnedProgram(Long userId, Long programId) {
        return programRepository.findByIdAndUserId(programId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Chương trình ôn tập không tồn tại"));
    }

    private StudyTopic findOwnedTopic(Long userId, Long topicId) {
        StudyTopic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new ResourceNotFoundException("Topic không tồn tại"));
        if (!topic.getProgram().getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Topic không tồn tại");
        }
        return topic;
    }

    private void updateProgress(StudyTopic topic) {
        int total = topic.getTotalQuestions() == null ? 0 : topic.getTotalQuestions();
        int done = topic.getCompletedQuestions() == null ? 0 : topic.getCompletedQuestions();
        topic.setCompletedQuestions(Math.min(done + 1, total));
        topicRepository.save(topic);

        StudyProgram program = topic.getProgram();
        int completedTopics = 0;
        List<StudyTopic> allTopics = topicRepository.findByProgramIdOrderByTopicOrderAsc(program.getId());
        for (StudyTopic t : allTopics) {
            int tTotal = t.getTotalQuestions() == null ? 0 : t.getTotalQuestions();
            int tDone = t.getCompletedQuestions() == null ? 0 : t.getCompletedQuestions();
            if (tTotal > 0 && tDone >= tTotal) {
                completedTopics++;
            }
        }
        program.setCompletedTopics(Math.min(completedTopics, program.getTotalTopics()));
        if (program.getTotalTopics() > 0
                && completedTopics >= program.getTotalTopics()
                && program.getStatus() != ProgramStatus.COMPLETED) {
            program.setStatus(ProgramStatus.COMPLETED);
            program.setCompletedAt(LocalDateTime.now());
        }
        programRepository.save(program);
    }

    private String resolvePosition(GenerateProgramRequest request, CvParseResponse cvData) {
        if (request.getTargetPosition() != null && !request.getTargetPosition().isBlank()) {
            return request.getTargetPosition().trim();
        }
        if (cvData.getTargetPosition() != null && !cvData.getTargetPosition().isBlank()) {
            return cvData.getTargetPosition().trim();
        }
        if (cvData.getSuggestedPositions() != null && !cvData.getSuggestedPositions().isEmpty()) {
            return cvData.getSuggestedPositions().get(0);
        }
        throw new BadRequestException(
                "Chưa xác định được vị trí mục tiêu. Hãy chọn vị trí qua PUT /api/v1/cv/position");
    }

    private int resolveQuestionsPerTopic(GenerateProgramRequest request) {
        int value = request.getQuestionsPerTopic() == null
                ? questionsPerTopic : request.getQuestionsPerTopic();
        return Math.max(1, Math.min(50, value));
    }

    private List<TopicPlan> buildTopics(CvParseResponse cvData, int maxTopics) {
        List<TopicPlan> topics = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        if (cvData.getSkills() != null) {
            for (CvParseResponse.SkillData skill : cvData.getSkills()) {
                if (topics.size() >= maxTopics) {
                    break;
                }
                String name = skill.getName();
                if (name == null || name.isBlank() || !seen.add(name.toLowerCase())) {
                    continue;
                }
                topics.add(new TopicPlan(name, toDifficulty(skill.getProficiency()),
                        "Luyện tập kỹ năng " + name, "Luyện tập kỹ năng " + name + " với các câu hỏi phỏng vấn thực tế"));
            }
        }

        if (cvData.getKnowledgeGaps() != null) {
            for (String gap : cvData.getKnowledgeGaps()) {
                if (topics.size() >= maxTopics) {
                    break;
                }
                if (gap == null || gap.isBlank() || !seen.add(gap.toLowerCase())) {
                    continue;
                }
                topics.add(new TopicPlan(gap, DifficultyLevel.INTERMEDIATE,
                        "Bổ sung kiến thức còn thiếu: " + gap,
                        "Ôn tập chủ đề " + gap + " để lấp khoảng trống kiến thức trong CV"));
            }
        }
        return topics;
    }

    private DifficultyLevel toDifficulty(ProficiencyLevel proficiency) {
        if (proficiency == null) {
            return DifficultyLevel.INTERMEDIATE;
        }
        return switch (proficiency) {
            case BEGINNER -> DifficultyLevel.BEGINNER;
            case ADVANCED, EXPERT -> DifficultyLevel.ADVANCED;
            default -> DifficultyLevel.INTERMEDIATE;
        };
    }

    private StudyProgramResponse toProgramResponse(StudyProgram program) {
        return StudyProgramResponse.builder()
                .id(program.getId())
                .title(program.getTitle())
                .description(program.getDescription())
                .programType(program.getProgramType())
                .targetPosition(program.getTargetPosition())
                .difficultyLevel(program.getDifficultyLevel())
                .status(program.getStatus())
                .totalTopics(program.getTotalTopics())
                .completedTopics(program.getCompletedTopics())
                .createdAt(program.getCreatedAt())
                .completedAt(program.getCompletedAt())
                .build();
    }

    private TopicResponse toTopicResponse(StudyTopic topic) {
        return TopicResponse.builder()
                .id(topic.getId())
                .title(topic.getTitle())
                .description(topic.getDescription())
                .topicOrder(topic.getTopicOrder())
                .difficulty(topic.getDifficulty())
                .skillTag(topic.getSkillTag())
                .totalQuestions(topic.getTotalQuestions())
                .completedQuestions(topic.getCompletedQuestions())
                .build();
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
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<QuestionResponse.Option>>() {
            });
        } catch (Exception e) {
            log.warn("Không parse được options của câu hỏi: {}", e.getMessage());
            return List.of();
        }
    }

    private record TopicPlan(String skillTag, DifficultyLevel difficulty, String title, String description) {
    }
}