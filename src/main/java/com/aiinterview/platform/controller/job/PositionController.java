package com.aiinterview.platform.controller.job;

import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.response.PositionRecommendationResponse;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.service.job.PositionRecommendationService;
import com.aiinterview.platform.service.auth.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/positions")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class PositionController {

    private final PositionRecommendationService positionRecommendationService;
    private final UserService userService;

    /** GET /api/v1/positions/recommendations — xep hang vi tri theo so ky nang trong CV trung khop */
    @GetMapping("/recommendations")
    public ResponseEntity<ApiResponse<List<PositionRecommendationResponse>>> getRecommendations(
            @RequestParam(defaultValue = "10") int limit,
            Authentication authentication) {
        User user = currentUser(authentication);
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return ResponseEntity.ok(ApiResponse.success(
                positionRecommendationService.recommend(user.getId(), safeLimit)));
    }

    /** GET /api/v1/positions/{positionId}/match — chi tiet ky nang trung khop / con thieu cua mot vi tri */
    @GetMapping("/{positionId}/match")
    public ResponseEntity<ApiResponse<PositionRecommendationResponse>> matchPosition(
            @PathVariable Long positionId,
            Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(ApiResponse.success(
                positionRecommendationService.matchPosition(user.getId(), positionId)));
    }

    private User currentUser(Authentication authentication) {
        return userService.findByEmail(authentication.getName());
    }
}
