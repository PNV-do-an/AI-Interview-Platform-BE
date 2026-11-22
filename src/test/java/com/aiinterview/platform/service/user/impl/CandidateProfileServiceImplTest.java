package com.aiinterview.platform.service.user.impl;

import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.model.dto.request.CandidateProfileRequest;
import com.aiinterview.platform.model.dto.response.CandidateProfileResponse;
import com.aiinterview.platform.model.entity.CandidateProfile;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.CandidateProfileRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Candidate profile rules (Postman folder 03): the first PUT creates the row, later PUTs update
 * it, and a blank cvFileName never erases the CV the candidate already uploaded.
 *
 * <p>Mỗi test chia 3 khối comment: <b>Arrange</b> → <b>Act</b> → <b>Assert</b>.</p>
 */
class CandidateProfileServiceImplTest {

    private static final Long PROFILE_ID = 9L;

    private CandidateProfileServiceImpl service;
    private CandidateProfileRepository profileRepository;
    private UserRepository userRepository;

    private User candidate;

    @BeforeEach
    void setUp() {
        profileRepository = mock(CandidateProfileRepository.class);
        userRepository = mock(UserRepository.class);
        service = new CandidateProfileServiceImpl(profileRepository, userRepository);

        // Ung vien dang nhap: service doc nguoi dung qua SecurityUtils
        candidate = User.builder()
                .id(20L)
                .email("candidate@example.com")
                .fullName("Candidate User")
                .password("secret")
                .role(Role.ROLE_USER)
                .build();
        when(userRepository.findByEmail(candidate.getEmail())).thenReturn(Optional.of(candidate));
        when(profileRepository.save(any(CandidateProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clearSecurityContext() {
        // SecurityContext la ThreadLocal -> clear giua cac test
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /users/me/candidate-profile chua co ho so tra ve null thay vi 404")
    void getMyProfile_withoutProfile_returnsNull() {
        // Arrange: da dang nhap nhung chua tao ho so
        signIn(candidate);
        when(profileRepository.findByUser(candidate)).thenReturn(Optional.empty());

        // Act & Assert: tra null (FE hien form trong) chu khong phai loi 404
        assertThat(service.getMyProfile()).isNull();
    }

    @Test
    @DisplayName("GET /users/me/candidate-profile tra ve ho so cua nguoi dang dang nhap")
    void getMyProfile_mapsStoredProfile() {
        // Arrange: da co ho so voi ky nang va so nam kinh nghiem
        signIn(candidate);
        when(profileRepository.findByUser(candidate)).thenReturn(Optional.of(
                CandidateProfile.builder()
                        .id(PROFILE_ID)
                        .user(candidate)
                        .skills("Java, SQL")
                        .experienceYears(3)
                        .build()));

        // Act
        CandidateProfileResponse response = service.getMyProfile();

        // Assert: map dung id / skills / experienceYears sang response
        assertThat(response.getId()).isEqualTo(PROFILE_ID);
        assertThat(response.getSkills()).isEqualTo("Java, SQL");
        assertThat(response.getExperienceYears()).isEqualTo(3);
    }

    @Test
    @DisplayName("PUT lan dau tao moi ho so gan voi nguoi dang dang nhap")
    void updateMyProfile_withoutProfile_createsNewOne() {
        // Arrange: chua co ho so trong DB
        signIn(candidate);
        when(profileRepository.findByUser(candidate)).thenReturn(Optional.empty());
        CandidateProfileRequest request = request();

        // Act
        CandidateProfileResponse response = service.updateMyProfile(request);

        // Assert: response phan anh du lieu vua gui
        assertThat(response.getSkills()).isEqualTo("Java");
        assertThat(response.getExperienceYears()).isEqualTo(2);

        // Assert: dong duoc luu la ban ghi MOI (id null) va gan dung user dang nhap
        ArgumentCaptor<CandidateProfile> saved = ArgumentCaptor.forClass(CandidateProfile.class);
        verify(profileRepository).save(saved.capture());
        assertThat(saved.getValue().getUser()).isEqualTo(candidate);
        assertThat(saved.getValue().getId()).isNull();
    }

    @Test
    @DisplayName("PUT lan sau cap nhat tren ho so cu, khong tao ban ghi thu hai")
    void updateMyProfile_withProfile_updatesInPlace() {
        // Arrange: da co ho so id = 9
        signIn(candidate);
        CandidateProfile existing = CandidateProfile.builder()
                .id(PROFILE_ID)
                .user(candidate)
                .skills("Cu")
                .summary("Tom tat cu")
                .build();
        when(profileRepository.findByUser(candidate)).thenReturn(Optional.of(existing));

        // Act
        CandidateProfileResponse response = service.updateMyProfile(request());

        // Assert: van la dong cu (giu id) nhung field da duoc ghi de
        assertThat(response.getId()).isEqualTo(PROFILE_ID);
        assertThat(existing.getSkills()).isEqualTo("Java");
        assertThat(existing.getSummary()).isEqualTo("Tom tat moi");
        verify(profileRepository).save(existing);
    }

    @Test
    @DisplayName("cvFileName rong khong duoc ghi de ten CV da luu")
    void updateMyProfile_blankCvFileName_keepsExistingFileName() {
        // Arrange: da co ten CV, form moi gui len cvFileName chi gom khoang trang
        signIn(candidate);
        CandidateProfile existing = CandidateProfile.builder()
                .id(PROFILE_ID)
                .user(candidate)
                .cvFileName("cv-cu.pdf")
                .build();
        when(profileRepository.findByUser(candidate)).thenReturn(Optional.of(existing));
        CandidateProfileRequest request = request();
        request.setCvFileName("   ");

        // Act
        CandidateProfileResponse response = service.updateMyProfile(request);

        // Assert: giu ten CV cu — neu khong, FE gui form khong co CV se xoa mat CV da upload
        assertThat(response.getCvFileName()).isEqualTo("cv-cu.pdf");
    }

    @Test
    @DisplayName("cvFileName moi duoc luu khi co gia tri")
    void updateMyProfile_presentCvFileName_overwrites() {
        // Arrange: da co ten CV cu, form moi gui ten moi
        signIn(candidate);
        when(profileRepository.findByUser(candidate)).thenReturn(Optional.of(
                CandidateProfile.builder().id(PROFILE_ID).user(candidate).cvFileName("cv-cu.pdf").build()));
        CandidateProfileRequest request = request();
        request.setCvFileName("cv-moi.pdf");

        // Act
        CandidateProfileResponse response = service.updateMyProfile(request);

        // Assert: co gia tri thi ghi de binh thuong
        assertThat(response.getCvFileName()).isEqualTo("cv-moi.pdf");
    }

    @Test
    @DisplayName("Chua dang nhap thi tra 404 va khong doc repository")
    void getMyProfile_whenAnonymous_returnsNotFound() {
        // Arrange: khong set authentication (khach chua dang nhap)
        SecurityContextHolder.clearContext();

        // Act & Assert: bao chua dang nhap, khong query DB
        assertThatThrownBy(service::getMyProfile)
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Người dùng chưa đăng nhập");
        verifyNoInteractions(profileRepository);
    }

    private void signIn(User user) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(user.getEmail(), null));
    }

    private CandidateProfileRequest request() {
        CandidateProfileRequest request = new CandidateProfileRequest();
        request.setSkills("Java");
        request.setExperienceYears(2);
        request.setSummary("Tom tat moi");
        request.setCareerGoal("Tro thanh senior");
        return request;
    }
}
