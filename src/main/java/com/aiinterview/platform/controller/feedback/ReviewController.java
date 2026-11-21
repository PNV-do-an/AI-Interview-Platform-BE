package com.aiinterview.platform.controller.feedback;

import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.request.ReviewReplyRequest;
import com.aiinterview.platform.model.dto.request.ReviewRequest;
import com.aiinterview.platform.model.dto.response.ReviewListResponse;
import com.aiinterview.platform.model.dto.response.ReviewResponse;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.security.utils.SecurityUtils;
import com.aiinterview.platform.service.feedback.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final UserRepository userRepository;

    /** POST /api/v1/reviews — ứng viên đánh giá nhà tuyển dụng sau phỏng vấn */
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ReviewResponse>> create(@Valid @RequestBody ReviewRequest request) {
        User reviewer = SecurityUtils.getCurrentUser(userRepository);
        return ResponseEntity.ok(ApiResponse.success(reviewService.create(reviewer, request)));
    }

    /** GET /api/v1/reviews/target/{userId} — đánh giá + điểm trung bình của nhà tuyển dụng */
    @GetMapping("/target/{userId}")
    public ResponseEntity<ApiResponse<ReviewListResponse>> listByTarget(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.listByTarget(userId)));
    }

    /** GET /api/v1/reviews/my — đánh giá tôi đã viết */
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> listMine() {
        User reviewer = SecurityUtils.getCurrentUser(userRepository);
        return ResponseEntity.ok(ApiResponse.success(reviewService.listMine(reviewer)));
    }

    /** GET /api/v1/reviews/admin/all — toàn bộ đánh giá (quản trị) */
    @GetMapping("/admin/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.success(reviewService.listAll()));
    }

    /** POST /api/v1/reviews/{id}/reply — admin/staff phản hồi đánh giá */
    @PostMapping("/{id}/reply")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<ReviewResponse>> reply(
            @PathVariable Long id,
            @Valid @RequestBody ReviewReplyRequest request) {
        User staff = SecurityUtils.getCurrentUser(userRepository);
        return ResponseEntity.ok(ApiResponse.success(reviewService.reply(staff, id, request.getReply())));
    }
}
