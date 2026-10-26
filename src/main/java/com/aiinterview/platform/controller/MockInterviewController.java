package com.aiinterview.platform.controller;

import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.request.InterviewEvaluationRequest;
import com.aiinterview.platform.model.dto.request.StartInterviewRequest;
import com.aiinterview.platform.model.dto.request.SubmitAnswerRequest;
import com.aiinterview.platform.model.dto.response.InterviewAnswerResponse;
import com.aiinterview.platform.model.dto.response.InterviewReportResponse;
import com.aiinterview.platform.model.dto.response.InterviewResultResponse;
import com.aiinterview.platform.model.dto.response.InterviewSessionResponse;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.security.utils.SecurityUtils;
import com.aiinterview.platform.service.MockInterviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/mock-interviews")
@RequiredArgsConstructor
public class MockInterviewController {

    private final MockInterviewService mockInterviewService;
    private final UserRepository userRepository;

    /** POST /api/v1/mock-interviews — start a MOCK, KNOWLEDGE or AVATAR session */
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<InterviewSessionResponse>> start(
            @Valid @RequestBody StartInterviewRequest request) {
        User candidate = SecurityUtils.getCurrentUser(userRepository);
        return ResponseEntity.ok(ApiResponse.success(mockInterviewService.start(candidate, request)));
    }

    /** GET /api/v1/mock-interviews/my?page=0&size=10 — candidate's session history */
    @GetMapping("/my")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Page<InterviewSessionResponse>>> getMySessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        User candidate = SecurityUtils.getCurrentUser(userRepository);
        return ResponseEntity.ok(ApiResponse.success(mockInterviewService.getMySessions(candidate, page, size)));
    }

    /** GET /api/v1/mock-interviews/{id} — session detail + answers */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<InterviewResultResponse>> getResult(@PathVariable Long id) {
        User candidate = SecurityUtils.getCurrentUser(userRepository);
        return ResponseEntity.ok(ApiResponse.success(mockInterviewService.getResult(candidate, id)));
    }

    /** POST /api/v1/mock-interviews/{id}/answer — submit answer for current question */
    @PostMapping("/{id}/answer")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<InterviewAnswerResponse>> submitAnswer(
            @PathVariable Long id,
            @Valid @RequestBody SubmitAnswerRequest request) {
        User candidate = SecurityUtils.getCurrentUser(userRepository);
        return ResponseEntity.ok(ApiResponse.success(mockInterviewService.submitAnswer(candidate, id, request)));
    }

    /** POST /api/v1/mock-interviews/{id}/finish — complete session and get overall score */
    @PostMapping("/{id}/finish")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<InterviewResultResponse>> finish(@PathVariable Long id) {
        User candidate = SecurityUtils.getCurrentUser(userRepository);
        return ResponseEntity.ok(ApiResponse.success(mockInterviewService.finish(candidate, id)));
    }

    /** POST /api/v1/mock-interviews/{id}/evaluation — nộp rubric 5 chiều cho phiên AVATAR đã kết thúc */
    @PostMapping("/{id}/evaluation")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<InterviewReportResponse>> submitEvaluation(
            @PathVariable Long id,
            @Valid @RequestBody InterviewEvaluationRequest request) {
        User candidate = SecurityUtils.getCurrentUser(userRepository);
        return ResponseEntity.ok(ApiResponse.success(
                mockInterviewService.submitEvaluation(candidate, id, request)));
    }

    /** GET /api/v1/mock-interviews/{id}/report — báo cáo avatar, chỉ chính chủ phiên đọc được */
    @GetMapping("/{id}/report")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<InterviewReportResponse>> getReport(@PathVariable Long id) {
        User candidate = SecurityUtils.getCurrentUser(userRepository);
        return ResponseEntity.ok(ApiResponse.success(mockInterviewService.getReport(candidate, id)));
    }

    /** DELETE /api/v1/mock-interviews/{id} — xóa phiên luyện của chính mình, không hoàn tác được */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Void>> deleteSession(@PathVariable Long id) {
        User candidate = SecurityUtils.getCurrentUser(userRepository);
        mockInterviewService.deleteSession(candidate, id);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa phiên luyện tập", null));
    }
}
