package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.common.util.SkillMatcher;
import com.aiinterview.platform.model.dto.response.PositionRecommendationResponse;
import com.aiinterview.platform.model.entity.PositionCatalog;
import com.aiinterview.platform.model.entity.UserCv;
import com.aiinterview.platform.model.entity.UserSkill;
import com.aiinterview.platform.model.repository.PositionCatalogRepository;
import com.aiinterview.platform.model.repository.UserCvRepository;
import com.aiinterview.platform.model.repository.UserSkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PositionRecommendationServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long CV_ID = 11L;

    private PositionRecommendationServiceImpl service;
    private PositionCatalogRepository positionCatalogRepository;
    private UserCvRepository userCvRepository;
    private UserSkillRepository userSkillRepository;

    @BeforeEach
    void setUp() {
        positionCatalogRepository = mock(PositionCatalogRepository.class);
        userCvRepository = mock(UserCvRepository.class);
        userSkillRepository = mock(UserSkillRepository.class);
        service = new PositionRecommendationServiceImpl(
                positionCatalogRepository, userCvRepository, userSkillRepository);
    }

    @Test
    void matchesSkillsIgnoringCaseSeparatorAndAlias() {
        givenCvSkills("SpringBoot", "spring-boot", "RESTful API");
        givenPositions(position(1L, "Backend Java Engineer", "Java", "Spring Boot", "REST API"));

        List<PositionRecommendationResponse> result = service.recommend(USER_ID, 10);

        assertThat(result).hasSize(1);
        PositionRecommendationResponse match = result.get(0);
        assertThat(match.getMatchedSkillCount()).isEqualTo(2);
        assertThat(match.getRequiredSkillCount()).isEqualTo(3);
        assertThat(match.getMatchPercentage()).isEqualTo(67);
        assertThat(match.getMatchedSkills()).containsExactly("REST API", "Spring Boot");
        assertThat(match.getMissingSkills()).containsExactly("Java");
    }

    @Test
    void ranksByMatchedCountThenPercentageThenTitle() {
        givenCvSkills("Java", "Docker", "MySQL", "React");
        givenPositions(
                position(1L, "A Backend", "Java", "Docker", "MySQL", "React", "Kafka", "Redis"),
                position(2L, "B Frontend", "JavaScript", "React", "TypeScript", "CSS"),
                position(3L, "C Fullstack", "Java", "Docker", "MySQL", "React"),
                position(4L, "D Cobol", "Cobol", "JCL"));

        List<PositionRecommendationResponse> result = service.recommend(USER_ID, 10);

        assertThat(result).extracting(PositionRecommendationResponse::getTitle)
                .containsExactly("C Fullstack", "A Backend", "B Frontend");
        assertThat(result).extracting(PositionRecommendationResponse::getMatchedSkillCount)
                .containsExactly(4, 4, 1);
    }

    @Test
    void excludesPositionsWithoutAnyMatchedSkill() {
        givenCvSkills("Java");
        givenPositions(
                position(1L, "Backend Java Engineer", "Java", "Spring Boot"),
                position(2L, "Data Engineer", "Python", "SQL"));

        List<PositionRecommendationResponse> result = service.recommend(USER_ID, 10);

        assertThat(result).extracting(PositionRecommendationResponse::getTitle)
                .containsExactly("Backend Java Engineer");
    }

    @Test
    void appliesLimitToRankedResults() {
        givenCvSkills("Java", "Docker");
        givenPositions(
                position(1L, "Position A", "Java", "Docker"),
                position(2L, "Position B", "Java", "Docker"),
                position(3L, "Position C", "Java", "Docker"));

        List<PositionRecommendationResponse> result = service.recommend(USER_ID, 2);

        assertThat(result).hasSize(2);
    }

    @Test
    void returnsEmptyListWhenUserHasNoCv() {
        when(userCvRepository.findTopByUserIdOrderByUpdatedAtDesc(USER_ID)).thenReturn(Optional.empty());

        assertThat(service.recommend(USER_ID, 10)).isEmpty();

        verifyNoInteractions(positionCatalogRepository, userSkillRepository);
    }

    @Test
    void matchPositionReturnsFullBreakdownWithoutCv() {
        PositionCatalog position = position(5L, "DevOps Engineer", "Docker", "Kubernetes", "AWS");
        when(positionCatalogRepository.findActiveWithSkillsById(5L)).thenReturn(Optional.of(position));
        when(userCvRepository.findTopByUserIdOrderByUpdatedAtDesc(USER_ID)).thenReturn(Optional.empty());

        PositionRecommendationResponse match = service.matchPosition(USER_ID, 5L);

        assertThat(match.getTitle()).isEqualTo("DevOps Engineer");
        assertThat(match.getMatchedSkillCount()).isZero();
        assertThat(match.getMatchPercentage()).isZero();
        assertThat(match.getMissingSkills()).containsExactly("AWS", "Docker", "Kubernetes");
    }

    @Test
    void matchPositionRejectsUnknownId() {
        when(positionCatalogRepository.findActiveWithSkillsById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.matchPosition(USER_ID, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Vị trí không tồn tại");
    }

    private void givenCvSkills(String... skillNames) {
        UserCv cv = UserCv.builder().id(CV_ID).build();
        when(userCvRepository.findTopByUserIdOrderByUpdatedAtDesc(USER_ID)).thenReturn(Optional.of(cv));
        when(userSkillRepository.findByCvId(CV_ID)).thenReturn(Arrays.stream(skillNames)
                .map(name -> UserSkill.builder().cv(cv).skillName(name).build())
                .toList());
    }

    @SafeVarargs
    private void givenPositions(PositionCatalog... positions) {
        when(positionCatalogRepository.findActiveWithSkills()).thenReturn(List.of(positions));
    }

    private PositionCatalog position(Long id, String title, String... requiredSkills) {
        PositionCatalog position = PositionCatalog.builder()
                .id(id)
                .title(title)
                .slug(title.toLowerCase().replace(' ', '-'))
                .level("MID")
                .description(title + " description")
                .active(true)
                .build();
        for (String skill : requiredSkills) {
            position.addRequiredSkill(skill, SkillMatcher.normalize(skill));
        }
        return position;
    }
}
