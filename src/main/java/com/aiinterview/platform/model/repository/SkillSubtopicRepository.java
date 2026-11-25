package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.SkillSubtopic;
import com.aiinterview.platform.model.enums.SubtopicSource;
import com.aiinterview.platform.model.enums.SubtopicStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SkillSubtopicRepository extends JpaRepository<SkillSubtopic, Long> {

    /** Identity là cặp (skill_key, subtopic_key) — dùng khi AI đề xuất nhãn đã tồn tại. */
    Optional<SkillSubtopic> findBySkillKeyAndSubtopicKey(String skillKey, String subtopicKey);

    /** Danh sách đề xuất cho admin, không phân biệt source. */
    List<SkillSubtopic> findBySkillKeyOrderBySortOrderAscIdAsc(String skillKey);

    /** Danh sách được chọn làm đích sinh, theo thứ tự ưu tiên (spec §7.3.1). */
    List<SkillSubtopic> findBySkillKeyAndStatusOrderBySortOrderAscIdAsc(
            String skillKey, SubtopicStatus status);

    /** Đếm theo source để biết skill đã có taxonomy do admin khai báo chưa. */
    long countBySkillKeyAndSourceAndStatus(String skillKey, SubtopicSource source, SubtopicStatus status);
}
