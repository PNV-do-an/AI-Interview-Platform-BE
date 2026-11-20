package com.aiinterview.platform.service.job.impl;

import com.aiinterview.platform.model.dto.response.MentorResponse;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.ReviewRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * GET /api/v1/mentors — danh sách người phỏng vấn kèm điểm đánh giá.
 * Điểm trung bình lấy từ bảng review tại thời điểm gọi (không cache), nên khi chưa có review nào
 * phải giữ null chứ không được biến thành 0 — 0 sao và "chưa có ai đánh giá" là hai chuyện khác nhau.
 */
class MentorServiceImplTest {

    private UserRepository userRepository;
    private ReviewRepository reviewRepository;
    private MentorServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        reviewRepository = mock(ReviewRepository.class);
        service = new MentorServiceImpl(userRepository, reviewRepository);
    }

    @Test
    @DisplayName("Tra ve dung ho so mentor kem diem trung binh va so luot danh gia")
    void listMentors_mapsProfileRatingAndReviewCount() {
        User mentor = interviewer(7L, "Mentor One", "data:image/png;base64,AAA");
        when(userRepository.findByRoleAndDeletedAtIsNull(Role.ROLE_INTERVIEWER)).thenReturn(List.of(mentor));
        when(reviewRepository.averageRatingByTarget(mentor)).thenReturn(4.5);
        when(reviewRepository.countByTarget(mentor)).thenReturn(12L);

        List<MentorResponse> result = service.listMentors();

        assertThat(result).hasSize(1);
        MentorResponse response = result.get(0);
        assertThat(response.getId()).isEqualTo(7L);
        assertThat(response.getFullName()).isEqualTo("Mentor One");
        assertThat(response.getAvatar()).isEqualTo("data:image/png;base64,AAA");
        assertThat(response.getRating()).isEqualTo(4.5);
        assertThat(response.getReviewCount()).isEqualTo(12L);
    }

    @Test
    @DisplayName("Chi lay ROLE_INTERVIEWER va bo qua tai khoan da xoa mem")
    void listMentors_queriesOnlyInterviewersNotDeleted() {
        when(userRepository.findByRoleAndDeletedAtIsNull(Role.ROLE_INTERVIEWER)).thenReturn(List.of());

        service.listMentors();

        verify(userRepository).findByRoleAndDeletedAtIsNull(Role.ROLE_INTERVIEWER);
        verifyNoInteractions(reviewRepository);
    }

    @Test
    @DisplayName("Chua co review nao thi rating la null, khong phai 0 sao")
    void listMentors_withoutReviews_keepsNullRating() {
        User mentor = interviewer(9L, "New Mentor", null);
        when(userRepository.findByRoleAndDeletedAtIsNull(Role.ROLE_INTERVIEWER)).thenReturn(List.of(mentor));
        when(reviewRepository.averageRatingByTarget(mentor)).thenReturn(null);
        when(reviewRepository.countByTarget(mentor)).thenReturn(0L);

        MentorResponse response = service.listMentors().get(0);

        assertThat(response.getRating()).isNull();
        assertThat(response.getReviewCount()).isZero();
        assertThat(response.getAvatar()).isNull();
    }

    @Test
    @DisplayName("Khong co mentor nao thi tra danh sach rong")
    void listMentors_whenNoInterviewer_returnsEmptyList() {
        when(userRepository.findByRoleAndDeletedAtIsNull(Role.ROLE_INTERVIEWER)).thenReturn(List.of());

        assertThat(service.listMentors()).isEmpty();
    }

    @Test
    @DisplayName("Nhieu mentor thi giu nguyen thu tu tra ve cua repository")
    void listMentors_keepsRepositoryOrder() {
        User first = interviewer(1L, "An", null);
        User second = interviewer(2L, "Binh", null);
        when(userRepository.findByRoleAndDeletedAtIsNull(Role.ROLE_INTERVIEWER)).thenReturn(List.of(first, second));
        when(reviewRepository.averageRatingByTarget(first)).thenReturn(5.0);
        when(reviewRepository.averageRatingByTarget(second)).thenReturn(3.0);
        when(reviewRepository.countByTarget(first)).thenReturn(2L);
        when(reviewRepository.countByTarget(second)).thenReturn(1L);

        assertThat(service.listMentors())
                .extracting(MentorResponse::getFullName)
                .containsExactly("An", "Binh");
    }

    private User interviewer(Long id, String fullName, String avatar) {
        return User.builder()
                .id(id)
                .email("mentor" + id + "@example.com")
                .fullName(fullName)
                .avatar(avatar)
                .role(Role.ROLE_INTERVIEWER)
                .enabled(true)
                .build();
    }
}
