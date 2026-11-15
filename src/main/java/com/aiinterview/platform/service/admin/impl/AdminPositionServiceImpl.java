package com.aiinterview.platform.service.admin.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.common.util.SkillMatcher;
import com.aiinterview.platform.model.dto.request.PositionRequest;
import com.aiinterview.platform.model.dto.response.AdminPositionResponse;
import com.aiinterview.platform.model.entity.PositionCatalog;
import com.aiinterview.platform.model.entity.PositionRequiredSkill;
import com.aiinterview.platform.model.repository.PositionCatalogRepository;
import com.aiinterview.platform.service.admin.AdminPositionService;
import com.aiinterview.platform.service.infra.OpenAiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPositionServiceImpl implements AdminPositionService {

    private static final int MAX_SKILLS = 30;
    private static final int MAX_SLUG_LENGTH = 110;

    private final PositionCatalogRepository positionCatalogRepository;
    private final OpenAiClient openAiClient;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminPositionResponse> getPositions(int page, int size, String search, Boolean active) {
        Pageable pageable = PageRequest.of(
                Math.max(0, page),
                Math.max(1, Math.min(size, 100)),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        String searchParam = search == null || search.isBlank() ? null : search.trim();
        return positionCatalogRepository.search(searchParam, active, pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminPositionResponse getPosition(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Override
    @Transactional
    public AdminPositionResponse createPosition(PositionRequest request) {
        List<String> skillNames = resolveSkills(request, List.of());
        PositionCatalog position = PositionCatalog.builder()
                .title(request.getTitle().trim())
                .slug(uniqueSlug(request.getTitle(), null))
                .level(trimToNull(request.getLevel()))
                .description(trimToNull(request.getDescription()))
                .jdText(trimToNull(request.getJdText()))
                .active(true)
                .build();
        applySkills(position, skillNames);
        AdminPositionResponse response = toResponse(positionCatalogRepository.save(position));
        log.info("admin created position id={} title='{}' skills={}",
                response.getId(), response.getTitle(), response.getRequiredSkills().size());
        return response;
    }

    @Override
    @Transactional
    public AdminPositionResponse updatePosition(Long id, PositionRequest request) {
        PositionCatalog position = findOrThrow(id);
        List<String> currentSkills = position.getRequiredSkills().stream()
                .map(PositionRequiredSkill::getSkillName)
                .toList();

        position.setTitle(request.getTitle().trim());
        position.setSlug(uniqueSlug(request.getTitle(), position.getId()));
        position.setLevel(trimToNull(request.getLevel()));
        position.setDescription(trimToNull(request.getDescription()));
        position.setJdText(trimToNull(request.getJdText()));
        applySkills(position, resolveSkills(request, currentSkills));

        AdminPositionResponse response = toResponse(positionCatalogRepository.save(position));
        log.info("admin updated position id={} title='{}' skills={}",
                response.getId(), response.getTitle(), response.getRequiredSkills().size());
        return response;
    }

    @Override
    @Transactional
    public AdminPositionResponse changeStatus(Long id, boolean active) {
        PositionCatalog position = findOrThrow(id);
        position.setActive(active);
        AdminPositionResponse response = toResponse(positionCatalogRepository.save(position));
        log.info("admin changed position id={} active={}", id, active);
        return response;
    }

    @Override
    public List<String> extractSkills(String jdText) {
        if (jdText == null || jdText.isBlank()) {
            throw new BadRequestException("JD text không được để trống");
        }
        return cleanSkills(openAiClient.extractRequiredSkills(jdText.trim()));
    }

    /**
     * Uu tien danh sach skills admin gui len; neu trong thi nho AI trich xuat tu JD.
     * Khi cap nhat ma khong gui ca hai thi giu nguyen ky nang hien co.
     */
    private List<String> resolveSkills(PositionRequest request, List<String> fallback) {
        List<String> provided = cleanSkills(request.getSkills());
        if (!provided.isEmpty()) {
            return provided;
        }
        if (request.getJdText() != null && !request.getJdText().isBlank()) {
            return extractSkills(request.getJdText());
        }
        if (fallback == null || fallback.isEmpty()) {
            throw new BadRequestException(
                    "Cần danh sách kỹ năng hoặc JD để AI trích xuất kỹ năng yêu cầu của vị trí");
        }
        return fallback;
    }

    private List<String> cleanSkills(List<String> rawSkills) {
        if (rawSkills == null || rawSkills.isEmpty()) {
            return List.of();
        }
        Map<String, String> byKey = new LinkedHashMap<>();
        for (String raw : rawSkills) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String name = raw.trim().replaceAll("\\s+", " ");
            if (name.length() > 100) {
                name = name.substring(0, 100);
            }
            String key = SkillMatcher.normalize(name);
            if (key.isBlank()) {
                continue;
            }
            byKey.putIfAbsent(key, name);
            if (byKey.size() >= MAX_SKILLS) {
                break;
            }
        }
        return List.copyOf(byKey.values());
    }

    /** Chi xoa ky nang khong con, giu nguyen row cu de tranh dung do unique (position_id, skill_key). */
    private void applySkills(PositionCatalog position, List<String> skillNames) {
        Map<String, String> desired = new LinkedHashMap<>();
        for (String name : skillNames) {
            desired.putIfAbsent(SkillMatcher.normalize(name), name);
        }

        position.getRequiredSkills().removeIf(skill -> !desired.containsKey(skill.getSkillKey()));
        Set<String> existingKeys = position.getRequiredSkills().stream()
                .map(PositionRequiredSkill::getSkillKey)
                .collect(Collectors.toSet());
        for (Map.Entry<String, String> entry : desired.entrySet()) {
            if (!existingKeys.contains(entry.getKey())) {
                position.addRequiredSkill(entry.getValue(), entry.getKey());
            }
        }
    }

    private String uniqueSlug(String title, Long excludeId) {
        String base = slugify(title);
        String candidate = base;
        int suffix = 2;
        while (existsSlug(candidate, excludeId)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private boolean existsSlug(String slug, Long excludeId) {
        return excludeId == null
                ? positionCatalogRepository.existsBySlug(slug)
                : positionCatalogRepository.existsBySlugAndIdNot(slug, excludeId);
    }

    private String slugify(String title) {
        String slug = title.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (slug.isBlank()) {
            slug = "position";
        }
        return slug.length() > MAX_SLUG_LENGTH ? slug.substring(0, MAX_SLUG_LENGTH) : slug;
    }

    private PositionCatalog findOrThrow(Long id) {
        return positionCatalogRepository.findWithSkillsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vị trí không tồn tại"));
    }

    private AdminPositionResponse toResponse(PositionCatalog position) {
        return AdminPositionResponse.builder()
                .id(position.getId())
                .title(position.getTitle())
                .slug(position.getSlug())
                .level(position.getLevel())
                .description(position.getDescription())
                .jdText(position.getJdText())
                .active(position.getActive())
                .requiredSkills(position.getRequiredSkills().stream()
                        .map(PositionRequiredSkill::getSkillName)
                        .toList())
                .createdAt(position.getCreatedAt())
                .updatedAt(position.getUpdatedAt())
                .build();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
