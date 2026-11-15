package com.aiinterview.platform.service.job.impl;

import com.aiinterview.platform.model.dto.response.MatchResponse;
import com.aiinterview.platform.model.entity.CandidateProfile;
import com.aiinterview.platform.model.entity.JobDescription;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.CandidateProfileRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.service.job.MatchingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MatchingServiceImpl implements MatchingService {

    private static final int MAX_SUGGESTIONS = 20;

    private final UserRepository userRepository;
    private final CandidateProfileRepository candidateProfileRepository;

    @Override
    public List<MatchResponse> matchCandidates(JobDescription job) {
        List<String> requiredSkills = splitSkills(job.getRequiredSkills());
        if (requiredSkills.isEmpty()) {
            return List.of();
        }

        List<User> candidates = userRepository
                .findAllWithFilters(null, Role.ROLE_USER, "ACTIVE", PageRequest.of(0, 500))
                .getContent();

        List<MatchResponse> matches = new ArrayList<>();
        for (User candidate : candidates) {
            Optional<CandidateProfile> profileOpt = candidateProfileRepository.findByUser(candidate);
            CandidateProfile profile = profileOpt.orElse(null);
            List<String> candidateSkills = splitSkills(profile != null ? profile.getSkills() : null);
            List<String> matched = requiredSkills.stream()
                    .filter(rs -> candidateSkills.stream().anyMatch(cs -> cs.equals(rs)))
                    .collect(Collectors.toList());
            if (matched.isEmpty()) {
                continue;
            }
            int skillScore = (int) Math.round(100.0 * matched.size() / requiredSkills.size());
            int experienceScore = experienceBonus(profile, job);
            int score = Math.min(100, skillScore + experienceScore);
            matches.add(MatchResponse.builder()
                    .candidateId(candidate.getId())
                    .fullName(candidate.getFullName())
                    .email(candidate.getEmail())
                    .skills(profile != null ? profile.getSkills() : null)
                    .experienceYears(profile != null ? profile.getExperienceYears() : null)
                    .matchScore(score)
                    .matchedSkills(matched)
                    .build());
        }

        return matches.stream()
                .sorted((a, b) -> Integer.compare(b.getMatchScore(), a.getMatchScore()))
                .limit(MAX_SUGGESTIONS)
                .collect(Collectors.toList());
    }

    private int experienceBonus(CandidateProfile profile, JobDescription job) {
        if (profile == null || profile.getExperienceYears() == null || job.getRequiredSkills() == null) {
            return 0;
        }
        // simple heuristic: more experience than half the required-skill count is a small bonus
        return Math.min(10, profile.getExperienceYears() * 2);
    }

    private List<String> splitSkills(String skills) {
        if (skills == null || skills.isBlank()) {
            return List.of();
        }
        Map<String, Boolean> seen = new java.util.HashMap<>();
        List<String> result = new ArrayList<>();
        Arrays.stream(skills.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .forEach(s -> {
                    if (seen.put(s, Boolean.TRUE) == null) {
                        result.add(s);
                    }
                });
        return result;
    }
}
