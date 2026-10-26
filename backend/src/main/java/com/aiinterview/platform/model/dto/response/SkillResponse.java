package com.aiinterview.platform.model.dto.response;

import com.aiinterview.platform.model.enums.ProficiencyLevel;

/**
 * DTO cho GET /api/v1/cv/skills.
 * Không trả thẳng entity UserSkill: entity có quan hệ cv -> user nên Jackson sẽ kéo theo
 * password hash, verification/reset token của user. Tên field khớp CvParseResponse.SkillData
 * để frontend chỉ cần một type cho kỹ năng.
 */
public record SkillResponse(
        Long id,
        String name,
        ProficiencyLevel proficiency,
        Integer years) {
}
