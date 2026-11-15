package com.aiinterview.platform.controller.user;

import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.request.CandidateProfileRequest;
import com.aiinterview.platform.model.dto.response.CandidateProfileResponse;
import com.aiinterview.platform.service.user.CandidateProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/candidate-profile")
@RequiredArgsConstructor
public class CandidateProfileController {

    private final CandidateProfileService candidateProfileService;

    /** GET /api/v1/candidate-profile */
    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<CandidateProfileResponse>> getMyProfile() {
        return ResponseEntity.ok(ApiResponse.success(candidateProfileService.getMyProfile()));
    }

    /** PUT /api/v1/candidate-profile */
    @PutMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<CandidateProfileResponse>> updateMyProfile(
            @RequestBody CandidateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Hồ sơ đã được cập nhật", candidateProfileService.updateMyProfile(request)));
    }
}
