package com.aiinterview.platform.service.job.impl;

import com.aiinterview.platform.model.dto.response.MatchResponse;
import com.aiinterview.platform.model.entity.CandidateProfile;
import com.aiinterview.platform.model.entity.JobDescription;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.CandidateProfileRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Gợi ý ứng viên cho một tin tuyển dụng (dùng ở màn hình admin xem tin).
 * <p>
 * Công thức hiện tại: 100 × (số skill khớp / số skill yêu cầu), cộng thêm bonus kinh nghiệm
 * (2 điểm mỗi năm, tối đa 10), chặn trần 100; chỉ giữ ứng viên có ít nhất 1 skill khớp, sắp xếp
 * giảm dần và cắt còn 20 người. Đây là heuristic — test khoá lại đúng con số để lần sau đổi công
 * thức là biết ngay.
 */
class MatchingServiceImplTest {

    private static final int MAX = 20;

    private UserRepository userRepository;
    private CandidateProfileRepository candidateProfileRepository;
    private MatchingServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        candidateProfileRepository = mock(CandidateProfileRepository.class);
        service = new MatchingServiceImpl(userRepository, candidateProfileRepository);
    }

    @Test
    @DisplayName("Tin khong co requiredSkills thi khong goi database, tra rong")
    void matchCandidates_withoutRequiredSkills_returnsEmpty() {
        List<MatchResponse> result = service.matchCandidates(job("  "));

        assertThat(result).isEmpty();
        verifyNoInteractions(userRepository, candidateProfileRepository);
    }

    @Test
    @DisplayName("Ung vien khong co skill nao trung thi bi loai, ke ca khi chua co profile")
    void matchCandidates_skipsCandidatesWithoutSkillOverlap() {
        User withOtherSkills = user(1L, "Other");
        User withoutProfile = user(2L, "NoProfile");
        stubCandidates(withOtherSkills, withoutProfile);
        when(candidateProfileRepository.findByUser(withOtherSkills))
                .thenReturn(Optional.of(profile("python, sql", 5)));
        when(candidateProfileRepository.findByUser(withoutProfile)).thenReturn(Optional.empty());

        assertThat(service.matchCandidates(job("java, spring"))).isEmpty();
    }

    @Test
    @DisplayName("Khop 2 tren 4 skill duoc 50 diem, khong cong kinh nghiem")
    void matchCandidates_scoresSkillRatio() {
        User candidate = user(1L, "Candidate");
        stubCandidates(candidate);
        when(candidateProfileRepository.findByUser(candidate))
                .thenReturn(Optional.of(profile("java, spring", null)));

        MatchResponse match = service.matchCandidates(job("java, spring, docker, kafka")).get(0);

        assertThat(match.getMatchScore()).isEqualTo(50);
        assertThat(match.getMatchedSkills()).containsExactly("java", "spring");
        assertThat(match.getExperienceYears()).isNull();
    }

    @Test
    @DisplayName("Kinh nghiem cong 2 diem moi nam, toi da 10 diem")
    void matchCandidates_addsExperienceBonusCappedAtTen() {
        User twoYears = user(1L, "TwoYears");
        User tenYears = user(2L, "TenYears");
        stubCandidates(twoYears, tenYears);
        when(candidateProfileRepository.findByUser(twoYears))
                .thenReturn(Optional.of(profile("java, spring, docker, kafka", 2)));
        when(candidateProfileRepository.findByUser(tenYears))
                .thenReturn(Optional.of(profile("java, spring, docker, kafka", 10)));

        List<MatchResponse> matches = service.matchCandidates(job("java, spring, docker, kafka"));

        // 100 diem skill + bonus: 2 nam -> 104 nhung chan tran 100; 10 nam -> 120 cung chan 100
        assertThat(matches).extracting(MatchResponse::getMatchScore).containsExactly(100, 100);
    }

    @Test
    @DisplayName("Skill duoc chuan hoa: hoa thuong, bo khoang trang, chong trung lap")
    void matchCandidates_normalisesAndDedupesSkills() {
        User candidate = user(1L, "Candidate");
        stubCandidates(candidate);
        when(candidateProfileRepository.findByUser(candidate))
                .thenReturn(Optional.of(profile("JAVA,  Spring , java", 0)));

        MatchResponse match = service.matchCandidates(job("Java,JAVA,spring,SPRING")).get(0);

        assertThat(match.getMatchScore()).isEqualTo(100);
        assertThat(match.getMatchedSkills()).containsExactly("java", "spring");
    }

    @Test
    @DisplayName("Ket qua sap xep giam dan theo diem")
    void matchCandidates_sortsByScoreDescending() {
        User partial = user(1L, "Partial");
        User full = user(2L, "Full");
        stubCandidates(partial, full);
        when(candidateProfileRepository.findByUser(partial))
                .thenReturn(Optional.of(profile("java", 0)));
        when(candidateProfileRepository.findByUser(full))
                .thenReturn(Optional.of(profile("java, spring", 0)));

        List<MatchResponse> matches = service.matchCandidates(job("java, spring"));

        assertThat(matches).extracting(MatchResponse::getFullName).containsExactly("Full", "Partial");
        assertThat(matches).extracting(MatchResponse::getMatchScore).containsExactly(100, 50);
    }

    @Test
    @DisplayName("Toi da 20 goi y du co nhieu ung vien khop hon")
    void matchCandidates_limitsToTwentySuggestions() {
        List<User> candidates = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            User candidate = user((long) i, "Candidate " + i);
            candidates.add(candidate);
            when(candidateProfileRepository.findByUser(candidate))
                    .thenReturn(Optional.of(profile("java", 0)));
        }
        when(userRepository.findAllWithFilters(isNull(), eq(Role.ROLE_USER), eq("ACTIVE"),
                eq(PageRequest.of(0, 500)))).thenReturn(new PageImpl<>(candidates));

        assertThat(service.matchCandidates(job("java"))).hasSize(MAX);
    }

    private void stubCandidates(User... users) {
        when(userRepository.findAllWithFilters(isNull(), eq(Role.ROLE_USER), eq("ACTIVE"),
                eq(PageRequest.of(0, 500)))).thenReturn(new PageImpl<>(List.of(users)));
    }

    private JobDescription job(String requiredSkills) {
        return JobDescription.builder().id(7L).requiredSkills(requiredSkills).build();
    }

    private User user(Long id, String fullName) {
        return User.builder()
                .id(id)
                .email("candidate" + id + "@example.com")
                .fullName(fullName)
                .role(Role.ROLE_USER)
                .enabled(true)
                .build();
    }

    private CandidateProfile profile(String skills, Integer experienceYears) {
        return CandidateProfile.builder()
                .skills(skills)
                .experienceYears(experienceYears)
                .build();
    }
}
