package com.aiinterview.platform.service;

import com.aiinterview.platform.model.entity.Question;
import com.aiinterview.platform.model.enums.DifficultyLevel;
import com.aiinterview.platform.model.enums.QuestionType;

import java.util.List;

public interface QuestionPoolService {

    /**
     * Lay toi da {@code needed} cau hoi cho mot cell (skill + do kho + loai + ngon ngu).
     * Uu tien cau hoi co san trong DB, chi goi AI cho phan con thieu.
     * Cac cau hoi tra ve la entity moi (chua co id) de gan vao topic.
     */
    List<Question> acquire(String position, String skillTag, DifficultyLevel difficulty,
                           QuestionType type, String language, int needed, Long userId);
}
