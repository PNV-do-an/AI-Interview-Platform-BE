package com.aiinterview.platform.common.util;

import com.aiinterview.platform.model.enums.DifficultyLevel;
import com.aiinterview.platform.model.enums.QuestionType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillKeysTest {

    @Test
    void normalizeTrimsCollapsesAndLowercases() {
        assertThat(SkillKeys.normalize("  Spring   Boot ")).isEqualTo("spring boot");
    }

    @Test
    void normalizeAppliesAliases() {
        assertThat(SkillKeys.normalize("SpringBoot")).isEqualTo("spring boot");
        assertThat(SkillKeys.normalize("spring-boot")).isEqualTo("spring boot");
        assertThat(SkillKeys.normalize("K8s")).isEqualTo("kubernetes");
        assertThat(SkillKeys.normalize("Postgres")).isEqualTo("postgresql");
    }

    @Test
    void normalizeKeepsDots() {
        assertThat(SkillKeys.normalize("Node.js")).isEqualTo("node.js");
    }

    @Test
    void normalizeHandlesNull() {
        assertThat(SkillKeys.normalize(null)).isEmpty();
    }

    @Test
    void fingerprintIsStableAndIgnoresWhitespaceAndCase() {
        String first = SkillKeys.fingerprint("docker", DifficultyLevel.BEGINNER,
                QuestionType.ESSAY, "en", "What is a layer?");
        String second = SkillKeys.fingerprint("Docker", DifficultyLevel.BEGINNER,
                QuestionType.ESSAY, "EN", "  what is a   layer?  ");

        assertThat(first).isEqualTo(second).hasSize(64);
    }

    @Test
    void fingerprintChangesWithCell() {
        String beginner = SkillKeys.fingerprint("docker", DifficultyLevel.BEGINNER,
                QuestionType.ESSAY, "en", "What is a layer?");
        String advanced = SkillKeys.fingerprint("docker", DifficultyLevel.ADVANCED,
                QuestionType.ESSAY, "en", "What is a layer?");

        assertThat(beginner).isNotEqualTo(advanced);
    }
}
