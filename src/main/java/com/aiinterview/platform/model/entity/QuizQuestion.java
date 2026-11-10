package com.aiinterview.platform.model.entity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Lob;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Một câu hỏi trắc nghiệm (4 lựa chọn) dùng cho các phiên luyện MOCK/KNOWLEDGE.
 * Lưu options dưới dạng JSON array trong một cột để không cần bảng collection lồng nhau.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizQuestion {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Column(name = "question", nullable = false, length = 2000)
    private String question;

    @Lob
    @Column(name = "options", nullable = false, columnDefinition = "TEXT")
    private String optionsJson;

    @Column(name = "correct_option_index", nullable = false)
    private int correctOptionIndex;

    public static QuizQuestion of(String question, List<String> options, int correctOptionIndex) {
        QuizQuestion q = new QuizQuestion();
        q.question = question;
        q.optionsJson = toJson(options);
        q.correctOptionIndex = correctOptionIndex;
        return q;
    }

    public List<String> getOptions() {
        if (optionsJson == null || optionsJson.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(optionsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void setOptions(List<String> options) {
        this.optionsJson = toJson(options);
    }

    private static String toJson(List<String> options) {
        try {
            return MAPPER.writeValueAsString(options == null ? new ArrayList<>() : options);
        } catch (Exception e) {
            return "[]";
        }
    }
}
