package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.StudyAnswer;
import com.aiinterview.platform.model.enums.QuestionArchetype;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudyAnswerRepository extends JpaRepository<StudyAnswer, Long> {

    Optional<StudyAnswer> findByTopicIdAndQuestionId(Long topicId, Long questionId);

    long countByTopicId(Long topicId);

    boolean existsByUserIdAndPoolQuestionId(Long userId, Long poolQuestionId);

    // Toàn bộ câu trả lời của một topic, sắp theo thời điểm nộp — dùng cho study log.
    List<StudyAnswer> findByTopicIdOrderBySubmittedAtAsc(Long topicId);

    // Điểm trung bình của topic: độ phân giải của log và đầu vào của cooldown.
    @Query("select avg(sa.score) from StudyAnswer sa where sa.topic.id = :topicId")
    Double averageScoreByTopicId(@Param("topicId") Long topicId);

    // Điểm trung bình của riêng bộ đã dùng cho topic (spec §4.7: available_at tính
    // một lần khi topic hoàn thành, theo điểm trung bình các câu thuộc bộ đó).
    @Query("select avg(sa.score) from StudyAnswer sa "
            + "where sa.topic.id = :topicId and sa.question.set.id = :setId")
    Double averageScoreByTopicAndSet(@Param("topicId") Long topicId, @Param("setId") Long setId);

    // Nhận xét tổng theo skill: không gọi AI cho phần số liệu (D23).
    @Query("select sa.question.skillTag as skillTag, avg(sa.score) as averageScore, "
            + "count(sa) as answeredCount from StudyAnswer sa "
            + "where sa.topic.id = :topicId group by sa.question.skillTag")
    List<SkillScoreProjection> aggregateBySkill(@Param("topicId") Long topicId);

    // Nhận xét tổng theo dạng câu: cho biết user yếu định nghĩa hay yếu áp dụng.
    @Query("select sa.question.archetype as archetype, avg(sa.score) as averageScore, "
            + "count(sa) as answeredCount from StudyAnswer sa "
            + "where sa.topic.id = :topicId group by sa.question.archetype")
    List<ArchetypeScoreProjection> aggregateByArchetype(@Param("topicId") Long topicId);

    interface SkillScoreProjection {
        String getSkillTag();

        Double getAverageScore();

        Long getAnsweredCount();
    }

    interface ArchetypeScoreProjection {
        QuestionArchetype getArchetype();

        Double getAverageScore();

        Long getAnsweredCount();
    }
}
