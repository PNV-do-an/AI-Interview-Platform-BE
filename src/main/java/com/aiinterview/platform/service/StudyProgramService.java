package com.aiinterview.platform.service;

import com.aiinterview.platform.model.dto.request.GenerateProgramRequest;
import com.aiinterview.platform.model.dto.request.SubmitStudyAnswerRequest;
import com.aiinterview.platform.model.dto.response.AnswerEvaluationResponse;
import com.aiinterview.platform.model.dto.response.QuestionResponse;
import com.aiinterview.platform.model.dto.response.StudyProgramResponse;
import com.aiinterview.platform.model.dto.response.TopicResponse;
import com.aiinterview.platform.model.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface StudyProgramService {

    StudyProgramResponse generateProgram(User user, GenerateProgramRequest request);

    List<StudyProgramResponse> getUserPrograms(Long userId);

    StudyProgramResponse getProgramDetail(Long userId, Long programId);

    List<TopicResponse> getProgramTopics(Long userId, Long programId);

    Page<QuestionResponse> getTopicQuestions(Long userId, Long topicId, Pageable pageable);

    AnswerEvaluationResponse submitStudyAnswer(Long userId, Long topicId, SubmitStudyAnswerRequest request);
}