package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.Question;
import com.aiinterview.platform.model.enums.QuestionReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long>, JpaSpecificationExecutor<Question> {

    Page<Question> findByTopicId(Long topicId, Pageable pageable);

    List<Question> findByTopicId(Long topicId);

    Optional<Question> findByIdAndTopicId(Long id, Long topicId);

    long countByTopicId(Long topicId);

    List<Question> findByFingerprintIn(Collection<String> fingerprints);

    // acquire cấp nguyên bộ, không trộn câu — thứ tự ổn định để bản copy của topic
    // giữ đúng thứ tự dạng câu.
    List<Question> findBySetIdAndReviewStatusNotOrderByArchetypeAscIdAsc(
            Long setId, QuestionReviewStatus reviewStatus);

    long countBySetIdAndReviewStatusNot(Long setId, QuestionReviewStatus reviewStatus);
}
