package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.QuestionSet;
import com.aiinterview.platform.model.enums.DifficultyLevel;
import com.aiinterview.platform.model.enums.QuestionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QuestionSetRepository extends JpaRepository<QuestionSet, Long> {

    /** Dùng khi sinh set_name và khi race làm UNIQUE(set_name) ném lỗi. */
    Optional<QuestionSet> findBySetName(String setName);

    /** Số bộ hiện có của cell — đầu vào của set_name "<Level> <Skill> <n>". */
    long countBySkillKeyAndDifficultyAndQuestionTypeAndLanguage(
            String skillKey, DifficultyLevel difficulty, QuestionType questionType, String language);

    long countBySubtopicId(Long subtopicId);
}
