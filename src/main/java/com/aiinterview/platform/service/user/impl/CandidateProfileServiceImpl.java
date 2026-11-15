package com.aiinterview.platform.service.user.impl;

import com.aiinterview.platform.model.dto.request.CandidateProfileRequest;
import com.aiinterview.platform.model.dto.response.CandidateProfileResponse;
import com.aiinterview.platform.model.entity.CandidateProfile;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.repository.CandidateProfileRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.security.utils.SecurityUtils;
import com.aiinterview.platform.service.user.CandidateProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CandidateProfileServiceImpl implements CandidateProfileService {

    private final CandidateProfileRepository profileRepository;
    private final UserRepository userRepository;

    @Override
    public CandidateProfileResponse getMyProfile() {
        User user = SecurityUtils.getCurrentUser(userRepository);
        return CandidateProfileResponse.from(profileRepository.findByUser(user).orElse(null));
    }

    @Override
    @Transactional
    public CandidateProfileResponse updateMyProfile(CandidateProfileRequest request) {
        User user = SecurityUtils.getCurrentUser(userRepository);
        CandidateProfile profile = profileRepository.findByUser(user)
                .orElseGet(() -> CandidateProfile.builder().user(user).build());
        profile.setSkills(request.getSkills());
        profile.setExperienceYears(request.getExperienceYears());
        profile.setSummary(request.getSummary());
        profile.setCareerGoal(request.getCareerGoal());
        if (request.getCvFileName() != null && !request.getCvFileName().isBlank()) {
            profile.setCvFileName(request.getCvFileName());
        }
        return CandidateProfileResponse.from(profileRepository.save(profile));
    }
}
