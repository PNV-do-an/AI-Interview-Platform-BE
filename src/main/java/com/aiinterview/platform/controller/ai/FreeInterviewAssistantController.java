package com.aiinterview.platform.controller.ai;

import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.request.EvaluateFreeInterviewRequest;
import com.aiinterview.platform.model.dto.request.GenerateQuestionRequest;
import com.aiinterview.platform.model.dto.response.EvaluateFreeInterviewResponse;
import com.aiinterview.platform.model.dto.response.GenerateQuestionResponse;
import com.aiinterview.platform.model.dto.response.TranscribeAudioResponse;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.security.utils.SecurityUtils;
import com.aiinterview.platform.service.ai.FreeInterviewAssistantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/ai-interview")
@RequiredArgsConstructor
public class FreeInterviewAssistantController {

    private final FreeInterviewAssistantService freeInterviewAssistantService;
    private final UserRepository userRepository;

    @PostMapping("/question")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<GenerateQuestionResponse>> generateQuestion(
            @RequestBody GenerateQuestionRequest request) {
        SecurityUtils.getCurrentUser(userRepository);
        return ResponseEntity.ok(ApiResponse.success(
                freeInterviewAssistantService.generateQuestion(request)));
    }

    @PostMapping("/transcribe")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TranscribeAudioResponse>> transcribe(
            @RequestParam("file") MultipartFile file) throws Exception {
        SecurityUtils.getCurrentUser(userRepository);
        return ResponseEntity.ok(ApiResponse.success(
                freeInterviewAssistantService.transcribe(file)));
    }

    @PostMapping("/evaluate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<EvaluateFreeInterviewResponse>> evaluate(
            @RequestBody EvaluateFreeInterviewRequest request) {
        SecurityUtils.getCurrentUser(userRepository);
        return ResponseEntity.ok(ApiResponse.success(
                freeInterviewAssistantService.evaluate(request)));
    }
}