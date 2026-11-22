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
 *
 * <p>Mỗi test chia 3 khối comment: <b>Arrange</b> → <b>Act</b> → <b>Assert</b>.</p>
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

        // 3 vai: nguoi viet danh gia, nguoi duoc danh gia, staff duoc phep phan hoi
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
        // Arrange: targetId 99 khong co trong users
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert: 404 (khac 400 cua cac vi pham nghiep vu)
        assertThatThrownBy(() -> service.create(candidate, request(99L, 5)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy người được đánh giá");

        // Assert: khong luu danh gia
        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    @DisplayName("Tu danh gia chinh minh bi chan 400")
    void create_selfReview_returnsBadRequest() {
        // Arrange: targetId = chinh candidate

        // Act & Assert: chan tu danh gia (se lam sai diem trung binh)
        assertThatThrownBy(() -> service.create(candidate, request(candidate.getId(), 5)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Bạn không thể tự đánh giá chính mình");

        // Assert: chan truoc khi kiem tra lich su ung tuyen
        verifyNoInteractions(applicationRepository);
    }

    @Test
    @DisplayName("Chua phong van xong thi chua duoc danh gia")
    void create_withoutFinishedInterview_returnsBadRequest() {
        // Arrange: khong co don nao o trang thai da phong van voi employer nay
        when(applicationRepository.existsByCandidateAndJobEmployerAndStatusIn(
                eq(candidate), eq(employer), anyCollection())).thenReturn(false);

        // Act & Assert: chi nguoi da thuc su phong van moi duoc danh gia
        assertThatThrownBy(() -> service.create(candidate, request(employer.getId(), 5)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Bạn chỉ có thể đánh giá sau khi hoàn thành buổi phỏng vấn");

        // Assert: khong luu danh gia
        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    @DisplayName("Chi hai trang thai INTERVIEWED va ACCEPTED moi duoc tinh la da phong van")
    void create_checksOnlyCompletedStatuses() {
        // Arrange: co don da phong van
        when(applicationRepository.existsByCandidateAndJobEmployerAndStatusIn(
                any(User.class), any(User.class), anyCollection())).thenReturn(true);

        // Act
        service.create(candidate, request(employer.getId(), 5));

        // Assert: tap trang thai truyen xuong query dung 2 gia tri nay (khong gom SCHEDULED/PENDING)
        ArgumentCaptor<Collection<ApplicationStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(applicationRepository).existsByCandidateAndJobEmployerAndStatusIn(
                eq(candidate), eq(employer), statuses.capture());
        assertThat(statuses.getValue()).containsExactlyInAnyOrder(
                ApplicationStatus.INTERVIEWED, ApplicationStatus.ACCEPTED);
    }

    @Test
    @DisplayName("Danh gia thanh cong luu dung nguoi viet, nguoi duoc danh gia va so sao")
    void create_withFinishedInterview_persistsReview() {
        // Arrange: da phong van xong, co kem jobId de danh gia theo tin
        givenCompletedInterview();
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job()));
        ReviewRequest request = request(employer.getId(), 4);
        request.setJobId(JOB_ID);

        // Act
        ReviewResponse response = service.create(candidate, request);

        // Assert: reviewer lay tu nguoi dang nhap, target tu request
        assertThat(response.getReviewerId()).isEqualTo(candidate.getId());
        assertThat(response.getTargetId()).isEqualTo(employer.getId());
        assertThat(response.getRating()).isEqualTo(4);
        assertThat(response.getJobId()).isEqualTo(JOB_ID);

        // Assert: dong duoc luu co comment va reply con null (staff chua phan hoi)
        ArgumentCaptor<Review> saved = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(saved.capture());
        assertThat(saved.getValue().getComment()).isEqualTo("Trai nghiem tot");
        assertThat(saved.getValue().getReply()).isNull();
    }

    @Test
    @DisplayName("Job khong bat buoc: danh gia chung van luu duoc khi khong co jobId")
    void create_withoutJob_savesGenericReview() {
        // Arrange: da phong van xong, khong truyen jobId
        givenCompletedInterview();

        // Act
        ReviewResponse response = service.create(candidate, request(employer.getId(), 5));

        // Assert: danh gia chung van hop le va khong can doc bang job
        assertThat(response.getJobId()).isNull();
        verifyNoInteractions(jobRepository);
    }

    @Test
    @DisplayName("Job duoc neu nhung khong ton tai tra 404")
    void create_unknownJob_returnsNotFound() {
        // Arrange: co jobId nhung tin da bi xoa
        givenCompletedInterview();
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.empty());
        ReviewRequest request = request(employer.getId(), 5);
        request.setJobId(JOB_ID);

        // Act & Assert: 404 thay vi luu danh gia tro toi job khong con
        assertThatThrownBy(() -> service.create(candidate, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy tin tuyển dụng");

        // Assert: khong luu danh gia
        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    @DisplayName("GET /reviews/target/{id} tinh diem trung binh va tong so danh gia")
    void listByTarget_returnsAverageAndCount() {
        // Arrange: employer co 1 danh gia 4 sao
        Review review = review(candidate, employer, 4);
        when(reviewRepository.findByTargetOrderByCreatedAtDesc(employer)).thenReturn(List.of(review));
        when(reviewRepository.averageRatingByTarget(employer)).thenReturn(4.0);
        when(reviewRepository.countByTarget(employer)).thenReturn(1L);

        // Act
        ReviewListResponse response = service.listByTarget(employer.getId());

        // Assert: diem trung binh + tong so + danh sach duoc tra cung luc (mot request)
        assertThat(response.getAverageRating()).isEqualTo(4.0);
        assertThat(response.getTotalReviews()).isEqualTo(1L);
        assertThat(response.getReviews()).hasSize(1);
    }

    @Test
    @DisplayName("GET /reviews/target/{id} voi nguoi dung khong ton tai tra 404")
    void listByTarget_unknownTarget_returnsNotFound() {
        // Arrange: id 99 khong co trong users
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert: 404 (khac voi truong hop co nguoi nhung 0 danh gia => list rong)
        assertThatThrownBy(() -> service.listByTarget(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy người dùng");
    }

    @Test
    @DisplayName("GET /reviews/my chi tra danh gia do chinh minh viet")
    void listMine_queriesCurrentReviewer() {
        // Arrange: candidate da viet 1 danh gia
        when(reviewRepository.findByReviewerOrderByCreatedAtDesc(candidate))
                .thenReturn(List.of(review(candidate, employer, 3)));

        // Act
        List<ReviewResponse> reviews = service.listMine(candidate);

        // Assert: query loc theo reviewer dang dang nhap
        assertThat(reviews).hasSize(1);
        assertThat(reviews.get(0).getReviewerId()).isEqualTo(candidate.getId());
        verify(reviewRepository).findByReviewerOrderByCreatedAtDesc(candidate);
    }

    @Test
    @DisplayName("GET /reviews (admin) sap xep moi nhat truoc")
    void listAll_sortsNewestFirst() {
        // Arrange: 2 danh gia, repository tra ve thu tu cu -> moi (khong dam bao tu DB)
        Review older = review(candidate, employer, 3);
        older.setCreatedAt(LocalDateTime.now().minusDays(2));
        Review newer = review(candidate, employer, 5);
        newer.setCreatedAt(LocalDateTime.now());
        when(reviewRepository.findAll()).thenReturn(List.of(older, newer));

        // Act
        List<ReviewResponse> reviews = service.listAll();

        // Assert: service tu sap xep lai — danh gia 5 sao (moi) dung dau
        assertThat(reviews).extracting(ReviewResponse::getRating).containsExactly(5, 3);
    }

    @Test
    @DisplayName("Phan hoi cua staff luu reply, nguoi phan hoi va thoi diem")
    void reply_byStaff_storesReplyMetadata() {
        // Arrange: danh gia ton tai
        Review review = review(candidate, employer, 4);
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));

        // Act
        ReviewResponse response = service.reply(staff, REVIEW_ID, "Cam on ban da danh gia");

        // Assert: luu noi dung + ten nguoi phan hoi + moc thoi gian (de hien thi cong khai)
        assertThat(response.getReply()).isEqualTo("Cam on ban da danh gia");
        assertThat(response.getRepliedByName()).isEqualTo(staff.getFullName());
        assertThat(response.getRepliedAt()).isNotNull();
        verify(reviewRepository).save(review);
    }

    @Test
    @DisplayName("Phan hoi danh gia khong ton tai tra 404")
    void reply_unknownReview_returnsNotFound() {
        // Arrange: reviewId khong co trong DB
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.empty());

        // Act & Assert: 404 va khong ghi gi
        assertThatThrownBy(() -> service.reply(staff, REVIEW_ID, "Cam on"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy đánh giá");
        verify(reviewRepository, never()).save(any(Review.class));
    }

    /** Stub "candidate da hoan thanh phong van voi employer". */
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
