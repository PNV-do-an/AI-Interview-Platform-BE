package com.aiinterview.platform.service.job.impl;

import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.common.util.SkillMatcher;
import com.aiinterview.platform.model.dto.response.PositionRecommendationResponse;
import com.aiinterview.platform.model.entity.PositionCatalog;
import com.aiinterview.platform.model.entity.PositionRequiredSkill;
import com.aiinterview.platform.model.entity.UserSkill;
import com.aiinterview.platform.model.repository.PositionCatalogRepository;
import com.aiinterview.platform.model.repository.UserCvRepository;
import com.aiinterview.platform.model.repository.UserSkillRepository;
import com.aiinterview.platform.service.job.PositionRecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PositionRecommendationServiceImpl implements PositionRecommendationService {

    private final PositionCatalogRepository positionCatalogRepository;
    private final UserCvRepository userCvRepository;
    private final UserSkillRepository userSkillRepository;

    @Override
    @Transactional(readOnly = true)
    public List<PositionRecommendationResponse> recommend(Long userId, int limit) {
        Set<String> cvSkillKeys = loadCvSkillKeys(userId);
        if (cvSkillKeys.isEmpty()) {
            log.info("position recommendation skipped: user={} has no CV skills", userId);
            return List.of();
        }

        List<PositionRecommendationResponse> ranked = positionCatalogRepository.findActiveWithSkills().stream()
                .map(position -> toResponse(position, cvSkillKeys))
                .filter(result -> result.getMatchedSkillCount() > 0)
                .sorted(Comparator
                        .comparingInt(PositionRecommendationResponse::getMatchedSkillCount).reversed()
                        .thenComparing(Comparator
                                .comparingInt(PositionRecommendationResponse::getMatchPercentage).reversed())
                        .thenComparing(PositionRecommendationResponse::getTitle))
                .limit(Math.max(1, limit))
                .toList();

        log.info("position recommendation user={} cvSkills={} matched={}",
                userId, cvSkillKeys.size(), ranked.size());
        return ranked;
    }

    @Override
    @Transactional(readOnly = true)
    public PositionRecommendationResponse matchPosition(Long userId, Long positionId) {
        PositionCatalog position = positionCatalogRepository.findActiveWithSkillsById(positionId)
                .orElseThrow(() -> new ResourceNotFoundException("Vị trí không tồn tại"));
        return toResponse(position, loadCvSkillKeys(userId));
    }

    private Set<String> loadCvSkillKeys(Long userId) {
        return userCvRepository.findTopByUserIdOrderByUpdatedAtDesc(userId)
                .map(cv -> userSkillRepository.findByCvId(cv.getId()).stream()
                        .map(UserSkill::getSkillName)
                        .map(SkillMatcher::normalize)
                        .filter(key -> !key.isBlank())
                        .collect(Collectors.toCollection(LinkedHashSet::new)))
                .orElseGet(LinkedHashSet::new);
    }

    private PositionRecommendationResponse toResponse(PositionCatalog position, Set<String> cvSkillKeys) {
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (PositionRequiredSkill required : position.getRequiredSkills()) {
            if (cvSkillKeys.contains(required.getSkillKey())) {
                matched.add(required.getSkillName());
            } else {
                missing.add(required.getSkillName());
            }
        }
        matched.sort(String.CASE_INSENSITIVE_ORDER);
        missing.sort(String.CASE_INSENSITIVE_ORDER);

        int requiredCount = matched.size() + missing.size();
        int percentage = requiredCount == 0 ? 0 : (int) Math.round(matched.size() * 100.0 / requiredCount);

        return PositionRecommendationResponse.builder()
                .positionId(position.getId())
                .title(position.getTitle())
                .slug(position.getSlug())
                .level(position.getLevel())
                .description(position.getDescription())
                .requiredSkillCount(requiredCount)
                .matchedSkillCount(matched.size())
                .matchPercentage(percentage)
                .matchedSkills(matched)
                .missingSkills(missing)
                .build();
    }
}
