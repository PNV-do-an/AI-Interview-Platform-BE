package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.StudyAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StudyAnswerRepository extends JpaRepository<StudyAnswer, Long> {

    Optional<StudyAnswer> findByTopicIdAndQuestionId(Long topicId, Long questionId);

    long countByTopicId(Long topicId);

    boolean existsByUserIdAndPoolQuestionId(Long userId, Long poolQuestionId);
}
