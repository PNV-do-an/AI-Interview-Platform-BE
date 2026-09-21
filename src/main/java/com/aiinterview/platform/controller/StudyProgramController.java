package com.aiinterview.platform.controller;

import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.request.GenerateProgramRequest;
import com.aiinterview.platform.model.dto.request.SubmitStudyAnswerRequest;
import com.aiinterview.platform.model.dto.response.AnswerEvaluationResponse;
import com.aiinterview.platform.model.dto.response.QuestionResponse;
import com.aiinterview.platform.model.dto.response.StudyProgramResponse;
import com.aiinterview.platform.model.dto.response.TopicResponse;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.service.StudyProgramService;
import com.aiinterview.platform.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/study")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class StudyProgramController {

    private final StudyProgramService studyProgramService;
    private final UserService userService;

    /** POST /api/v1/study/generate — tạo chương trình ôn tập dựa trên CV */
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<StudyProgramResponse>> generateProgram(
            @Valid @RequestBody GenerateProgramRequest request,
            Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(ApiResponse.success("Tạo chương trình ôn tập thành công",
                studyProgramService.generateProgram(user, request)));
    }

    /** GET /api/v1/study/programs — danh sách chương trình của user */
    @GetMapping("/programs")
    public ResponseEntity<ApiResponse<List<StudyProgramResponse>>> getPrograms(Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(ApiResponse.success(studyProgramService.getUserPrograms(user.getId())));
    }

    /** GET /api/v1/study/programs/{id} — chi tiết một chương trình */
    @GetMapping("/programs/{id}")
    public ResponseEntity<ApiResponse<StudyProgramResponse>> getProgramDetail(
            @PathVariable Long id,
            Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(ApiResponse.success(studyProgramService.getProgramDetail(user.getId(), id)));
    }

    /** GET /api/v1/study/programs/{id}/topics — danh sách topic của chương trình */
    @GetMapping("/programs/{id}/topics")
    public ResponseEntity<ApiResponse<List<TopicResponse>>> getTopics(
            @PathVariable Long id,
            Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(ApiResponse.success(studyProgramService.getProgramTopics(user.getId(), id)));
    }

    /** GET /api/v1/study/topics/{topicId}/questions — câu hỏi của topic (phân trang) */
    @GetMapping("/topics/{topicId}/questions")
    public ResponseEntity<ApiResponse<Page<QuestionResponse>>> getTopicQuestions(
            @PathVariable Long topicId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        User user = currentUser(authentication);
        int safeSize = Math.max(1, Math.min(size, 50));
        return ResponseEntity.ok(ApiResponse.success(studyProgramService.getTopicQuestions(
                user.getId(), topicId, PageRequest.of(Math.max(0, page), safeSize))));
    }

    /** POST /api/v1/study/topics/{topicId}/answer — nộp câu trả lời ôn tập, nhận feedback AI */
    @PostMapping("/topics/{topicId}/answer")
    public ResponseEntity<ApiResponse<AnswerEvaluationResponse>> submitAnswer(
            @PathVariable Long topicId,
            @Valid @RequestBody SubmitStudyAnswerRequest request,
            Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(ApiResponse.success("Đã đánh giá câu trả lời",
                studyProgramService.submitStudyAnswer(user.getId(), topicId, request)));
    }

    private User currentUser(Authentication authentication) {
        return userService.findByEmail(authentication.getName());
    }
}