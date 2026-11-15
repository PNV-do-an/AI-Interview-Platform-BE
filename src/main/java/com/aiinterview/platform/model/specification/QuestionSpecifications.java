package com.aiinterview.platform.model.specification;

import com.aiinterview.platform.model.entity.Question;
import com.aiinterview.platform.model.entity.StudyAnswer;
import com.aiinterview.platform.model.enums.DifficultyLevel;
import com.aiinterview.platform.model.enums.QuestionType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

/**
 * Dieu kien truy van cho question pool.
 * Chi chua predicate, khong chua nghiep vu (shortfall, nguong, ...).
 */
public final class QuestionSpecifications {

    private QuestionSpecifications() {
    }

    public static Specification<Question> pool() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.isNull(root.get("topic"));
    }

    public static Specification<Question> cell(String skillKey, DifficultyLevel difficulty,
                                               QuestionType type, String language) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.and(
                criteriaBuilder.equal(root.get("skillKey"), skillKey),
                criteriaBuilder.equal(root.get("difficulty"), difficulty),
                criteriaBuilder.equal(root.get("questionType"), type),
                criteriaBuilder.equal(root.get("language"), language),
                criteriaBuilder.isNotNull(root.get("correctAnswer")));
    }

    public static Specification<Question> unseenBy(Long userId) {
        return (root, query, criteriaBuilder) -> {
            Subquery<Long> answered = query.subquery(Long.class);
            Root<StudyAnswer> answer = answered.from(StudyAnswer.class);
            answered.select(answer.get("poolQuestionId"))
                    .where(criteriaBuilder.equal(answer.get("user").get("id"), userId),
                            criteriaBuilder.isNotNull(answer.get("poolQuestionId")));
            return criteriaBuilder.not(root.get("id").in(answered));
        };
    }
}
