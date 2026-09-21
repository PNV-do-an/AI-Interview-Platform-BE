package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {

    Page<Question> findByTopicId(Long topicId, Pageable pageable);

    List<Question> findByTopicId(Long topicId);

    Optional<Question> findByIdAndTopicId(Long id, Long topicId);

    long countByTopicId(Long topicId);
}