package com.aiinterview.platform.service.feedback.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.model.dto.request.ReviewRequest;
import com.aiinterview.platform.model.dto.response.ReviewListResponse;
import com.aiinterview.platform.model.dto.response.ReviewResponse;
import com.aiinterview.platform.model.entity.JobDescription;
import com.aiinterview.platform.model.entity.Review;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.ApplicationStatus;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.ApplicationRepository;
import com.aiinterview.platform.model.repository.JobDescriptionRepository;
import com.aiinterview.platform.model.repository.ReviewRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Review rules (Postman folder 11): only a candidate who actually finished an interview may rate
 * an employer, nobody rates themselves, and only staff may attach the public reply.
 */
class ReviewServiceImplTest {

    private static final Long REVIEW_ID = 31L;
    private static final Long JOB_ID = 7L;

    private ReviewServiceImpl service;
    private ReviewRepository reviewRepository;
    private UserRepository userRepository;
    private JobDescriptionRepository jobRepository;
    private ApplicationRepository applicationRepository;

    private User candidate;
    private User employer;
    private User staff;

    @BeforeEach
    void setUp() {
        reviewRepository = mock(ReviewRepository.class);
        userRepository = mock(UserRepository.class);
        jobRepository = mock(JobDescriptionRepository.class);
        applicationRepository = mock(ApplicationRepository.class);
        service = new ReviewServiceImpl(reviewRepository, userRepository, jobRepository, applicationRepository);

        candidate = user(20L, "candidate@example.com", "Candidate User", Role.ROLE_USER);
        employer = user(10L, "employer@example.com", "Employer User", Role.ROLE_INTERVIEWER);
        staff = user(2L, "staff@example.com", "Staff User", Role.ROLE_STAFF);

        when(userRepository.findById(employer.getId())).thenReturn(Optional.of(employer));
        when(userRepository.findById(candidate.getId())).thenReturn(Optional.of(candidate));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Danh gia nguoi khong ton tai tra 404")
    void create_unknownTarget_returnsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(candidate, request(99L, 5)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy người được đánh giá");
        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    @DisplayName("Tu danh gia chinh minh bi chan 400")
    void create_selfReview_returnsBadRequest() {
        assertThatThrownBy(() -> service.create(candidate, request(candidate.getId(), 5)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Bạn không thể tự đánh giá chính mình");
        verifyNoInteractions(applicationRepository);
    }

    @Test
    @DisplayName("Chua phong van xong thi chua duoc danh gia")
    void create_withoutFinishedInterview_returnsBadRequest() {
        when(applicationRepository.existsByCandidateAndJobEmployerAndStatusIn(
                eq(candidate), eq(employer), anyCollection())).thenReturn(false);

        assertThatThrownBy(() -> service.create(candidate, request(employer.getId(), 5)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Bạn chỉ có thể đánh giá sau khi hoàn thành buổi phỏng vấn");
        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    @DisplayName("Chi hai trang thai INTERVIEWED va ACCEPTED moi duoc tinh la da phong van")
    void create_checksOnlyCompletedStatuses() {
        when(applicationRepository.existsByCandidateAndJobEmployerAndStatusIn(
                any(User.class), any(User.class), anyCollection())).thenReturn(true);

        service.create(candidate, request(employer.getId(), 5));

        ArgumentCaptor<Collection<ApplicationStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(applicationRepository).existsByCandidateAndJobEmployerAndStatusIn(
                eq(candidate), eq(employer), statuses.capture());
        assertThat(statuses.getValue()).containsExactlyInAnyOrder(
                ApplicationStatus.INTERVIEWED, ApplicationStatus.ACCEPTED);
    }

    @Test
    @DisplayName("Danh gia thanh cong luu dung nguoi viet, nguoi duoc danh gia va so sao")
    void create_withFinishedInterview_persistsReview() {
        givenCompletedInterview();
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job()));
        ReviewRequest request = request(employer.getId(), 4);
        request.setJobId(JOB_ID);

        ReviewResponse response = service.create(candidate, request);

        assertThat(response.getReviewerId()).isEqualTo(candidate.getId());
        assertThat(response.getTargetId()).isEqualTo(employer.getId());
        assertThat(response.getRating()).isEqualTo(4);
        assertThat(response.getJobId()).isEqualTo(JOB_ID);
        ArgumentCaptor<Review> saved = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(saved.capture());
        assertThat(saved.getValue().getComment()).isEqualTo("Trai nghiem tot");
        assertThat(saved.getValue().getReply()).isNull();
    }

    @Test
    @DisplayName("Job khong bat buoc: danh gia chung van luu duoc khi khong co jobId")
    void create_withoutJob_savesGenericReview() {
        givenCompletedInterview();

        ReviewResponse response = service.create(candidate, request(employer.getId(), 5));

        assertThat(response.getJobId()).isNull();
        verifyNoInteractions(jobRepository);
    }

    @Test
    @DisplayName("Job duoc neu nhung khong ton tai tra 404")
    void create_unknownJob_returnsNotFound() {
        givenCompletedInterview();
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.empty());
        ReviewRequest request = request(employer.getId(), 5);
        request.setJobId(JOB_ID);

        assertThatThrownBy(() -> service.create(candidate, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy tin tuyển dụng");
        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    @DisplayName("GET /reviews/target/{id} tinh diem trung binh va tong so danh gia")
    void listByTarget_returnsAverageAndCount() {
        Review review = review(candidate, employer, 4);
        when(reviewRepository.findByTargetOrderByCreatedAtDesc(employer)).thenReturn(List.of(review));
        when(reviewRepository.averageRatingByTarget(employer)).thenReturn(4.0);
        when(reviewRepository.countByTarget(employer)).thenReturn(1L);

        ReviewListResponse response = service.listByTarget(employer.getId());

        assertThat(response.getAverageRating()).isEqualTo(4.0);
        assertThat(response.getTotalReviews()).isEqualTo(1L);
        assertThat(response.getReviews()).hasSize(1);
    }

    @Test
    @DisplayName("GET /reviews/target/{id} voi nguoi dung khong ton tai tra 404")
    void listByTarget_unknownTarget_returnsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listByTarget(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy người dùng");
    }

    @Test
    @DisplayName("GET /reviews/my chi tra danh gia do chinh minh viet")
    void listMine_queriesCurrentReviewer() {
        when(reviewRepository.findByReviewerOrderByCreatedAtDesc(candidate))
                .thenReturn(List.of(review(candidate, employer, 3)));

        List<ReviewResponse> reviews = service.listMine(candidate);

        assertThat(reviews).hasSize(1);
        assertThat(reviews.get(0).getReviewerId()).isEqualTo(candidate.getId());
        verify(reviewRepository).findByReviewerOrderByCreatedAtDesc(candidate);
    }

    @Test
    @DisplayName("GET /reviews (admin) sap xep moi nhat truoc")
    void listAll_sortsNewestFirst() {
        Review older = review(candidate, employer, 3);
        older.setCreatedAt(LocalDateTime.now().minusDays(2));
        Review newer = review(candidate, employer, 5);
        newer.setCreatedAt(LocalDateTime.now());
        when(reviewRepository.findAll()).thenReturn(List.of(older, newer));

        List<ReviewResponse> reviews = service.listAll();

        assertThat(reviews).extracting(ReviewResponse::getRating).containsExactly(5, 3);
    }

    @Test
    @DisplayName("Phan hoi cua staff luu reply, nguoi phan hoi va thoi diem")
    void reply_byStaff_storesReplyMetadata() {
        Review review = review(candidate, employer, 4);
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));

        ReviewResponse response = service.reply(staff, REVIEW_ID, "Cam on ban da danh gia");

        assertThat(response.getReply()).isEqualTo("Cam on ban da danh gia");
        assertThat(response.getRepliedByName()).isEqualTo(staff.getFullName());
        assertThat(response.getRepliedAt()).isNotNull();
        verify(reviewRepository).save(review);
    }

    @Test
    @DisplayName("Phan hoi danh gia khong ton tai tra 404")
    void reply_unknownReview_returnsNotFound() {
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reply(staff, REVIEW_ID, "Cam on"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy đánh giá");
        verify(reviewRepository, never()).save(any(Review.class));
    }

    private void givenCompletedInterview() {
        when(applicationRepository.existsByCandidateAndJobEmployerAndStatusIn(
                any(User.class), any(User.class), anyCollection())).thenReturn(true);
    }

    private ReviewRequest request(Long targetId, int rating) {
        ReviewRequest request = new ReviewRequest();
        request.setTargetId(targetId);
        request.setRating(rating);
        request.setComment("Trai nghiem tot");
        return request;
    }

    private Review review(User reviewer, User target, int rating) {
        return Review.builder()
                .id(REVIEW_ID)
                .reviewer(reviewer)
                .target(target)
                .rating(rating)
                .comment("Danh gia")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private JobDescription job() {
        return JobDescription.builder().id(JOB_ID).title("Backend Engineer").build();
    }

    private User user(Long id, String email, String fullName, Role role) {
        return User.builder()
                .id(id)
                .email(email)
                .fullName(fullName)
                .password("secret")
                .role(role)
                .build();
    }
}
