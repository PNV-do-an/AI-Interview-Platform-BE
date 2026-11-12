package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.model.dto.response.MentorResponse;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.ReviewRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.service.MentorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MentorServiceImpl implements MentorService {

    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;

    @Override
    @Transactional(readOnly = true)
    public List<MentorResponse> listMentors() {
        return userRepository.findByRoleAndDeletedAtIsNull(Role.ROLE_INTERVIEWER).stream()
                .map(this::toResponse)
                .toList();
    }

    private MentorResponse toResponse(User user) {
        return MentorResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .avatar(user.getAvatar())
                .rating(reviewRepository.averageRatingByTarget(user))
                .reviewCount(reviewRepository.countByTarget(user))
                .build();
    }
}
