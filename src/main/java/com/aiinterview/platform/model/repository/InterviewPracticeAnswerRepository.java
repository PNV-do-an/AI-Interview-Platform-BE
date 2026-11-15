package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.InterviewPracticeAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewPracticeAnswerRepository extends JpaRepository<InterviewPracticeAnswer, Long> {

    List<InterviewPracticeAnswer> findBySessionIdOrderByAnswerOrderAsc(Long sessionId);

    Optional<InterviewPracticeAnswer> findBySessionIdAndQuestionId(Long sessionId, Long questionId);

    long countBySessionId(Long sessionId);
}
