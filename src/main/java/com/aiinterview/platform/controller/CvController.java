package com.aiinterview.platform.controller;

import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.request.CvTextRequest;
import com.aiinterview.platform.model.dto.request.SetPositionRequest;
import com.aiinterview.platform.model.dto.response.CvParseResponse;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.entity.UserSkill;
import com.aiinterview.platform.service.CvService;
import com.aiinterview.platform.service.UserService;
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

    /** POST /api/v1/cv/upload — upload PDF/text, AI phân tích */
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<CvParseResponse>> uploadCv(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(ApiResponse.success("Phân tích CV thành công",
                cvService.uploadCv(file, user)));
    }

    /** POST /api/v1/cv/parse-text — paste CV text, AI phân tích */
    @PostMapping("/parse-text")
    public ResponseEntity<ApiResponse<CvParseResponse>> parseText(
            @Valid @RequestBody CvTextRequest request,
            Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(ApiResponse.success("Phân tích CV thành công",
                cvService.parseCvText(request.getCvText(), user)));
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
                cvService.setPosition(user.getId(), request.getPosition(), request.getLevel())));
    }

    /** GET /api/v1/cv/skills — danh sách kỹ năng từ CV */
    @GetMapping("/skills")
    public ResponseEntity<ApiResponse<List<UserSkill>>> getSkills(Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(ApiResponse.success(cvService.getCvSkills(user.getId())));
    }

    private User currentUser(Authentication authentication) {
        return userService.findByEmail(authentication.getName());
    }
}