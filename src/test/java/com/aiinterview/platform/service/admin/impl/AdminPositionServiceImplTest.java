package com.aiinterview.platform.service.admin.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.common.util.SkillMatcher;
import com.aiinterview.platform.model.dto.request.PositionRequest;
import com.aiinterview.platform.model.dto.response.AdminPositionResponse;
import com.aiinterview.platform.model.entity.PositionCatalog;
import com.aiinterview.platform.model.repository.PositionCatalogRepository;
import com.aiinterview.platform.service.infra.OpenAiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminPositionServiceImplTest {

    private AdminPositionServiceImpl service;
    private PositionCatalogRepository positionCatalogRepository;
    private OpenAiClient openAiClient;

    @BeforeEach
    void setUp() {
        positionCatalogRepository = mock(PositionCatalogRepository.class);
        openAiClient = mock(OpenAiClient.class);
        service = new AdminPositionServiceImpl(positionCatalogRepository, openAiClient);
        when(positionCatalogRepository.save(any(PositionCatalog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createUsesExplicitSkillsAndDeduplicatesThem() {
        PositionRequest request = request("Backend Java Engineer");
        request.setSkills(List.of("Java", " java ", "Spring Boot"));

        AdminPositionResponse response = service.createPosition(request);

        assertThat(response.getRequiredSkills()).containsExactly("Java", "Spring Boot");
        assertThat(response.getSlug()).isEqualTo("backend-java-engineer");
        assertThat(response.getActive()).isTrue();
        verifyNoInteractions(openAiClient);
    }

    @Test
    void createExtractsSkillsFromJdWhenSkillsAreMissing() {
        PositionRequest request = request("Android Engineer");
        request.setJdText("We need Kotlin and Jetpack Compose experience");
        when(openAiClient.extractRequiredSkills(request.getJdText()))
                .thenReturn(List.of("Kotlin", "Jetpack Compose", "kotlin"));

        AdminPositionResponse response = service.createPosition(request);

        assertThat(response.getRequiredSkills()).containsExactly("Kotlin", "Jetpack Compose");
        verify(openAiClient).extractRequiredSkills(request.getJdText());
    }

    @Test
    void createRejectsRequestWithoutSkillsAndJd() {
        PositionRequest request = request("Data Engineer");

        assertThatThrownBy(() -> service.createPosition(request))
                .isInstanceOf(BadRequestException.class);

        verify(positionCatalogRepository, never()).save(any(PositionCatalog.class));
    }

    @Test
    void createStoresJdTextForLaterEditing() {
        PositionRequest request = request("Backend Java Engineer");
        request.setJdText("  3+ years Java, Spring Boot, MySQL  ");
        request.setSkills(List.of("Java"));

        AdminPositionResponse response = service.createPosition(request);

        assertThat(response.getJdText()).isEqualTo("3+ years Java, Spring Boot, MySQL");
    }

    @Test
    void extractSkillsCleansAiOutput() {
        when(openAiClient.extractRequiredSkills("Kotlin, Compose"))
                .thenReturn(List.of(" Kotlin ", "kotlin", "", "Jetpack Compose"));

        List<String> skills = service.extractSkills("  Kotlin, Compose  ");

        assertThat(skills).containsExactly("Kotlin", "Jetpack Compose");
        verify(openAiClient).extractRequiredSkills("Kotlin, Compose");
    }

    @Test
    void extractSkillsRejectsBlankJd() {
        assertThatThrownBy(() -> service.extractSkills("   "))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(openAiClient);
    }

    @Test
    void createAddsSuffixWhenSlugAlreadyExists() {
        PositionRequest request = request("Backend Java Engineer");
        request.setSkills(List.of("Java"));
        when(positionCatalogRepository.existsBySlug("backend-java-engineer")).thenReturn(true);
        when(positionCatalogRepository.existsBySlug("backend-java-engineer-2")).thenReturn(false);

        AdminPositionResponse response = service.createPosition(request);

        assertThat(response.getSlug()).isEqualTo("backend-java-engineer-2");
    }

    @Test
    void updateKeepsExistingSkillsWhenNeitherSkillsNorJdProvided() {
        PositionCatalog position = position(3L, "DevOps Engineer", "Docker", "Kubernetes");
        when(positionCatalogRepository.findWithSkillsById(3L)).thenReturn(Optional.of(position));
        PositionRequest request = request("DevOps Engineer");

        AdminPositionResponse response = service.updatePosition(3L, request);

        assertThat(response.getRequiredSkills()).containsExactly("Docker", "Kubernetes");
        verifyNoInteractions(openAiClient);
    }

    @Test
    void updateReplacesSkillsAndRemovesTheMissingOnes() {
        PositionCatalog position = position(4L, "Data Engineer", "Python", "SQL");
        when(positionCatalogRepository.findWithSkillsById(4L)).thenReturn(Optional.of(position));
        PositionRequest request = request("Data Engineer");
        request.setSkills(List.of("Python", "Apache Spark"));

        AdminPositionResponse response = service.updatePosition(4L, request);

        assertThat(response.getRequiredSkills()).containsExactly("Python", "Apache Spark");
        assertThat(position.getRequiredSkills()).extracting(skill -> skill.getSkillKey())
                .containsExactly("python", SkillMatcher.normalize("Apache Spark"));
    }

    @Test
    void changeStatusTogglesActiveFlag() {
        PositionCatalog position = position(5L, "QA Automation Engineer", "Java");
        when(positionCatalogRepository.findWithSkillsById(5L)).thenReturn(Optional.of(position));

        AdminPositionResponse response = service.changeStatus(5L, false);

        assertThat(response.getActive()).isFalse();
        assertThat(position.getActive()).isFalse();
    }

    @Test
    void getPositionRejectsUnknownId() {
        when(positionCatalogRepository.findWithSkillsById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPosition(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private PositionRequest request(String title) {
        PositionRequest request = new PositionRequest();
        request.setTitle(title);
        request.setLevel("MID");
        request.setDescription(title + " description");
        return request;
    }

    private PositionCatalog position(Long id, String title, String... skills) {
        PositionCatalog position = PositionCatalog.builder()
                .id(id)
                .title(title)
                .slug(title.toLowerCase().replace(' ', '-'))
                .level("MID")
                .active(true)
                .build();
        for (String skill : skills) {
            position.addRequiredSkill(skill, SkillMatcher.normalize(skill));
        }
        return position;
    }
}
