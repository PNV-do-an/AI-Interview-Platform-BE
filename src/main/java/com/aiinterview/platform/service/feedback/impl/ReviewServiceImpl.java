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
import com.aiinterview.platform.model.repository.ApplicationRepository;
import com.aiinterview.platform.model.repository.JobDescriptionRepository;
import com.aiinterview.platform.model.repository.ReviewRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.service.feedback.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final JobDescriptionRepository jobRepository;
    private final ApplicationRepository applicationRepository;

    @Override
    @Transactional
    public ReviewResponse create(User reviewer, ReviewRequest request) {
        User target = userRepository.findById(request.getTargetId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người được đánh giá"));
        if (target.getId().equals(reviewer.getId())) {
            throw new BadRequestException("Bạn không thể tự đánh giá chính mình");
        }

        boolean interviewed = applicationRepository
                .existsByCandidateAndJobEmployerAndStatusIn(
                        reviewer, target, List.of(ApplicationStatus.INTERVIEWED, ApplicationStatus.ACCEPTED));
        if (!interviewed) {
            throw new BadRequestException("Bạn chỉ có thể đánh giá sau khi hoàn thành buổi phỏng vấn");
        }

        JobDescription job = null;
        if (request.getJobId() != null) {
            job = jobRepository.findById(request.getJobId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tin tuyển dụng"));
        }

        Review review = Review.builder()
                .reviewer(reviewer)
                .target(target)
                .job(job)
                .rating(request.getRating())
                .comment(request.getComment())
                .build();
        return ReviewResponse.from(reviewRepository.save(review));
    }

    @Override
    public ReviewListResponse listByTarget(Long targetId) {
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        List<ReviewResponse> reviews = reviewRepository.findByTargetOrderByCreatedAtDesc(target).stream()
                .map(ReviewResponse::from)
                .toList();
        Double average = reviewRepository.averageRatingByTarget(target);
        long count = reviewRepository.countByTarget(target);
        return ReviewListResponse.of(reviews, average, count);
    }

    @Override
    public List<ReviewResponse> listMine(User reviewer) {
        return reviewRepository.findByReviewerOrderByCreatedAtDesc(reviewer).stream()
                .map(ReviewResponse::from)
                .toList();
    }

    @Override
    public List<ReviewResponse> listAll() {
        return reviewRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(ReviewResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public ReviewResponse reply(User staff, Long reviewId, String reply) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đánh giá"));
        review.setReply(reply);
        review.setRepliedBy(staff);
        review.setRepliedAt(LocalDateTime.now());
        return ReviewResponse.from(reviewRepository.save(review));
    }
}
