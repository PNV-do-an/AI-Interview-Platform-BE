package com.aiinterview.platform.controller.document;

import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.request.CvTextRequest;
import com.aiinterview.platform.model.dto.request.SetPositionRequest;
import com.aiinterview.platform.model.dto.response.CvParseResponse;
import com.aiinterview.platform.model.dto.response.SkillResponse;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.service.document.CvService;
import com.aiinterview.platform.service.auth.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cv")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class CvController {

    private final CvService cvService;
    private final UserService userService;

    /** POST /api/v1/cv/upload — upload PDF/text, AI phân tích (targetPositionId tuỳ chọn) */
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<CvParseResponse>> uploadCv(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "targetPositionId", required = false) Long targetPositionId,
            Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(ApiResponse.success("Phân tích CV thành công",
                cvService.uploadCv(file, user, targetPositionId)));
    }

    /** POST /api/v1/cv/parse-text — paste CV text, AI phân tích */
    @PostMapping("/parse-text")
    public ResponseEntity<ApiResponse<CvParseResponse>> parseText(
            @Valid @RequestBody CvTextRequest request,
            Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(ApiResponse.success("Phân tích CV thành công",
                cvService.parseCvText(request.getCvText(), user, request.getTargetPositionId())));
    }

    /** GET /api/v1/cv — CV mới nhất của user */
    @GetMapping
    public ResponseEntity<ApiResponse<CvParseResponse>> getCv(Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(ApiResponse.success(cvService.getCv(user.getId())));
    }

    /** PUT /api/v1/cv/position — chọn vị trí + level mục tiêu */
    @PutMapping("/position")
    public ResponseEntity<ApiResponse<CvParseResponse>> setPosition(
            @Valid @RequestBody SetPositionRequest request,
            Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật vị trí mục tiêu",
                cvService.setPosition(user.getId(), request.getPositionId(),
                        request.getPosition(), request.getLevel())));
    }

    /** GET /api/v1/cv/skills — danh sách kỹ năng từ CV (trả DTO, không trả entity) */
    @GetMapping("/skills")
    public ResponseEntity<ApiResponse<List<SkillResponse>>> getSkills(Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(ApiResponse.success(cvService.getCvSkills(user.getId())));
    }

    private User currentUser(Authentication authentication) {
        return userService.findByEmail(authentication.getName());
    }
}
