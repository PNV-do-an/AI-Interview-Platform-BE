package com.aiinterview.platform.controller;

import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.request.StartInterviewRequest;
import com.aiinterview.platform.model.dto.request.SubmitInterviewAnswerRequest;
import com.aiinterview.platform.model.dto.response.AnswerEvaluationResponse;
import com.aiinterview.platform.model.dto.response.EvaluationReportResponse;
import com.aiinterview.platform.model.dto.response.InterviewSessionResponse;
import com.aiinterview.platform.model.dto.response.QuestionResponse;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.service.InterviewService;
import com.aiinterview.platform.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/interview")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;
    private final UserService userService;

    /** POST /api/v1/interview/start — bắt đầu phiên phỏng vấn mô phỏng */
    @PostMapping("/start")
    public ResponseEntity<ApiResponse<InterviewSessionResponse>> startSession(
            @RequestBody StartInterviewRequest request,
            Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(ApiResponse.success("Đã bắt đầu phiên phỏng vấn",
                interviewService.startSession(user, request)));
    }

    /** GET /api/v1/interview/{sessionId}/next — lấy câu hỏi tiếp theo */
    @GetMapping("/{sessionId}/next")
    public ResponseEntity<ApiResponse<QuestionResponse>> getNextQuestion(
            @PathVariable Long sessionId,
            Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(ApiResponse.success(interviewService.getNextQuestion(user.getId(), sessionId)));
    }

    /** POST /api/v1/interview/{sessionId}/answer — nộp câu trả lời (text bắt buộc, audio tùy chọn) */
    @PostMapping("/{sessionId}/answer")
    public ResponseEntity<ApiResponse<AnswerEvaluationResponse>> submitAnswer(
            @PathVariable Long sessionId,
            @Valid @RequestBody SubmitInterviewAnswerRequest request,
            Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(ApiResponse.success("Đã đánh giá câu trả lời",
                interviewService.submitAnswer(user.getId(), sessionId, request)));
    }

    /** POST /api/v1/interview/{sessionId}/finish — kết thúc phiên và đánh giá tổng quan */
    @PostMapping("/{sessionId}/finish")
    public ResponseEntity<ApiResponse<EvaluationReportResponse>> finishSession(
            @PathVariable Long sessionId,
            Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(ApiResponse.success("Phiên phỏng vấn đã kết thúc",
                interviewService.finishSession(user.getId(), sessionId)));
    }

    /** GET /api/v1/interview/history — danh sách phiên của user */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<InterviewSessionResponse>>> getHistory(Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(ApiResponse.success(interviewService.getUserHistory(user.getId())));
    }

    /** GET /api/v1/interview/{sessionId}/report — bảng đánh giá chi tiết */
    @GetMapping("/{sessionId}/report")
    public ResponseEntity<ApiResponse<EvaluationReportResponse>> getReport(
            @PathVariable Long sessionId,
            Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(ApiResponse.success(interviewService.getSessionReport(user.getId(), sessionId)));
    }

    private User currentUser(Authentication authentication) {
        return userService.findByEmail(authentication.getName());
    }
}