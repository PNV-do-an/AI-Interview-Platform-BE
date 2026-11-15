package com.aiinterview.platform.common.util;

import com.aiinterview.platform.model.enums.DifficultyLevel;
import com.aiinterview.platform.model.enums.QuestionType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/**
 * Chuan hoa skill_tag thanh khoa on dinh cho question pool.
 * skill_tag do AI tra ve la van ban tu do nen khong the dung lam khoa truy van.
 */
public final class SkillKeys {

    private static final Map<String, String> ALIASES = Map.of(
            "springboot", "spring boot",
            "js", "javascript",
            "ts", "typescript",
            "postgres", "postgresql",
            "k8s", "kubernetes",
            "node js", "node.js",
            "next js", "next.js",
            "react js", "react",
            "vue js", "vue"
    );

    // Khop voi do dai cot question.skill_key
    private static final int MAX_KEY_LENGTH = 100;

    private SkillKeys() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[-_]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        value = ALIASES.getOrDefault(value, value);
        return value.length() > MAX_KEY_LENGTH ? value.substring(0, MAX_KEY_LENGTH) : value;
    }

    public static String fingerprint(String skillKey, DifficultyLevel difficulty, QuestionType type,
                                     String language, String questionText) {
        String base = normalize(skillKey) + "|"
                + difficulty + "|"
                + type + "|"
                + normalizeText(language) + "|"
                + normalizeText(questionText);
        return sha256(base);
    }

    private static String normalizeText(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String sha256(String base) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(base.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
