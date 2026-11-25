package com.aiinterview.platform.common.util;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SkillMatcherTest {

    @Test
    void normalizeAppliesSkillKeysRules() {
        assertThat(SkillMatcher.normalize("  Spring   Boot ")).isEqualTo("spring boot");
        assertThat(SkillMatcher.normalize("SpringBoot")).isEqualTo("spring boot");
        assertThat(SkillMatcher.normalize("spring-boot")).isEqualTo("spring boot");
        assertThat(SkillMatcher.normalize("K8s")).isEqualTo("kubernetes");
        assertThat(SkillMatcher.normalize("Postgres")).isEqualTo("postgresql");
    }

    @Test
    void normalizeAppliesMatchingOnlyAliases() {
        assertThat(SkillMatcher.normalize("RESTful API")).isEqualTo("rest api");
        assertThat(SkillMatcher.normalize("REST API")).isEqualTo("rest api");
        assertThat(SkillMatcher.normalize("Rest")).isEqualTo("rest api");
        assertThat(SkillMatcher.normalize("Amazon Web Services")).isEqualTo("aws");
        assertThat(SkillMatcher.normalize("GCP")).isEqualTo("google cloud");
        assertThat(SkillMatcher.normalize("CI CD")).isEqualTo("ci/cd");
        assertThat(SkillMatcher.normalize("Unit Tests")).isEqualTo("unit testing");
        assertThat(SkillMatcher.normalize("Spring Framework")).isEqualTo("spring");
    }

    @Test
    void normalizeKeepsSlashFormStable() {
        assertThat(SkillMatcher.normalize("CI/CD")).isEqualTo("ci/cd");
        assertThat(SkillMatcher.normalize("AI/ML")).isEqualTo("ai/ml");
    }

    @Test
    void normalizeHandlesBlankAndNull() {
        assertThat(SkillMatcher.normalize(null)).isEmpty();
        assertThat(SkillMatcher.normalize("   ")).isEmpty();
    }

    @Test
    void normalizeCollapsesPluralAndVersionVariants() {
        // Số nhiều của CV phải về cùng khóa với tên số ít trong catalog (lỗi từng làm mất "REST API")
        assertThat(SkillMatcher.normalize("RESTful APIs")).isEqualTo("rest api");
        assertThat(SkillMatcher.normalize("REST APIs")).isEqualTo("rest api");
        assertThat(SkillMatcher.normalize("Microservices")).isEqualTo("microservice");
        assertThat(SkillMatcher.normalize("PostgreSQL Databases")).isEqualTo("postgresql database");
        assertThat(SkillMatcher.normalize("Java 17")).isEqualTo("java");
        assertThat(SkillMatcher.normalize("Spring Boot 3")).isEqualTo("spring boot");
    }

    @Test
    void normalizeKeepsProtectedTokensStable() {
        // Từ kết thúc bằng 's' nhưng không phải số nhiều, và token có ký tự đặc biệt
        assertThat(SkillMatcher.normalize("Kubernetes")).isEqualTo("kubernetes");
        assertThat(SkillMatcher.normalize("Jenkins")).isEqualTo("jenkins");
        assertThat(SkillMatcher.normalize("Redis")).isEqualTo("redis");
        assertThat(SkillMatcher.normalize("Node.js")).isEqualTo("node.js");
        assertThat(SkillMatcher.normalize("NodeJS")).isEqualTo("node.js");
        assertThat(SkillMatcher.normalize("ASP.NET")).isEqualTo("asp.net");
    }

    @Test
    void keysExpandsCompoundSkillsButKeepsKnownTerms() {
        assertThat(SkillMatcher.keys("HTML/CSS")).containsExactly("html/css", "html", "css");
        assertThat(SkillMatcher.keys("React, Redux")).containsExactly("react, redux", "react", "redux");
        assertThat(SkillMatcher.keys("CI/CD")).containsExactly("ci/cd");
        assertThat(SkillMatcher.keys("Spring Boot")).containsExactly("spring boot");
    }

    @Test
    void matchesUsesEveryVariantOfTheRequiredSkill() {
        Set<String> cvKeys = Set.of("html", "css", "rest api");

        assertThat(SkillMatcher.matches(cvKeys, "REST API", "REST API")).isTrue();
        assertThat(SkillMatcher.matches(cvKeys, "HTML")).isTrue();
        assertThat(SkillMatcher.matches(cvKeys, "Docker")).isFalse();
        assertThat(SkillMatcher.matches(Set.of(), "HTML")).isFalse();
    }
}
