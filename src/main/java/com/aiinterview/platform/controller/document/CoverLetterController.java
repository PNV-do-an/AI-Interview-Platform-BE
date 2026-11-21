package com.aiinterview.platform.controller.document;

import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.request.CoverLetterGenerateRequest;
import com.aiinterview.platform.model.dto.response.CoverLetterResponse;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.security.utils.SecurityUtils;
import com.aiinterview.platform.service.document.CoverLetterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/cover-letters")
@RequiredArgsConstructor
public class CoverLetterController {

    private final CoverLetterService coverLetterService;
    private final UserRepository userRepository;

    /** POST /api/v1/cover-letters/generate — AI drafts a cover letter from CV + job */
    @PostMapping("/generate")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<CoverLetterResponse>> generate(
            @Valid @RequestBody CoverLetterGenerateRequest request) {
        User candidate = SecurityUtils.getCurrentUser(userRepository);
        return ResponseEntity.ok(ApiResponse.success(coverLetterService.generate(candidate, request)));
    }

    /** GET /api/v1/cover-letters/my — candidate's letters */
    @GetMapping("/my")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<List<CoverLetterResponse>>> getMyLetters() {
        User candidate = SecurityUtils.getCurrentUser(userRepository);
        return ResponseEntity.ok(ApiResponse.success(coverLetterService.getMyLetters(candidate)));
    }

    /** GET /api/v1/cover-letters/{id} */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<CoverLetterResponse>> getLetter(@PathVariable Long id) {
        User candidate = SecurityUtils.getCurrentUser(userRepository);
        return ResponseEntity.ok(ApiResponse.success(coverLetterService.getLetter(candidate, id)));
    }

    /** PATCH /api/v1/cover-letters/{id} — edit letter content */
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<CoverLetterResponse>> updateContent(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        User candidate = SecurityUtils.getCurrentUser(userRepository);
        return ResponseEntity.ok(ApiResponse.success(
                coverLetterService.updateContent(candidate, id, body.get("content"))));
    }

    /** DELETE /api/v1/cover-letters/{id} */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        User candidate = SecurityUtils.getCurrentUser(userRepository);
        coverLetterService.delete(candidate, id);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa thư xin việc", null));
    }
}
