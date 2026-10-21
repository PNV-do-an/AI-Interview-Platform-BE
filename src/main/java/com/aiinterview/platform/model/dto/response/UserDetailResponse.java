package com.aiinterview.platform.model.dto.response;

import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.Role;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class UserDetailResponse {

    private Long id;
    private String email;
    private String fullName;
    private String phone;
    private Role role;
    private boolean enabled;
    /** Locked by an admin (as opposed to the temporary brute-force block in {@code blockedUntil}). */
    private boolean locked;
    private LocalDateTime lockedAt;
    /** True while the verification link has never been used (same rule as the list, Clarify C-2). */
    private boolean pendingVerification;
    /** Until when login is blocked by failed password attempts; null when that block is not active. */
    private LocalDateTime blockedUntil;
    private int failedLoginAttempts;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    /** Newest successful row of login_attempts for this email; null when the account never signed in. */
    private LocalDateTime lastLoginAt;
    private String lastIp;
    /** Mean overall_score of this account's completed sessions, 1 decimal; null when untested. */
    private Double aiReadinessScore;
    private Activity activity;
    /** Null until the candidate fills in their profile. */
    private Profile profile;

    @Data
    @Builder
    public static class Activity {
        private long applications;
        private long mockSessions;
        private long bookedInterviews;
    }

    @Data
    @Builder
    public static class Profile {
        /** candidate_profiles.skills is a comma-separated TEXT column. */
        private List<String> skills;
        private Integer experienceYears;
        private String summary;
        private String careerGoal;
        private String cvFileName;
        private LocalDateTime updatedAt;
    }

    public static UserDetailResponse from(User user) {
        LocalDateTime blockUntil = user.getLockUntil();
        return UserDetailResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .role(user.getRole())
                .enabled(user.isEnabled())
                .locked(user.isLocked())
                .lockedAt(user.getLockedAt())
                .pendingVerification(user.getVerificationToken() != null)
                .blockedUntil(blockUntil != null && blockUntil.isAfter(LocalDateTime.now()) ? blockUntil : null)
                .failedLoginAttempts(user.getLoginAttempts())
                .status(resolveStatus(user))
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .deletedAt(user.getDeletedAt())
                .build();
    }

    private static String resolveStatus(User user) {
        if (user.getDeletedAt() != null)  return "DELETED";
        if (user.isLocked())              return "LOCKED";
        if (!user.isEnabled())            return "INACTIVE";
        return "ACTIVE";
    }
}
