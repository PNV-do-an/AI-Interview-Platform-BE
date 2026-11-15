package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.AiQuestionCache;
import com.aiinterview.platform.model.enums.DifficultyLevel;
import com.aiinterview.platform.model.enums.QuestionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiQuestionCacheRepository extends JpaRepository<AiQuestionCache, Long> {

    List<AiQuestionCache> findTop10ByPositionAndSkillTagAndDifficultyAndQuestionTypeOrderByTimesUsedDesc(
            String position, String skillTag, DifficultyLevel difficulty, QuestionType questionType);

    Optional<AiQuestionCache> findByCacheKey(String cacheKey);
}