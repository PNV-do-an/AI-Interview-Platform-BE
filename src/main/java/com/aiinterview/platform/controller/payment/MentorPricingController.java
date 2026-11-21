package com.aiinterview.platform.controller.payment;

import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.request.MentorPricingRequest;
import com.aiinterview.platform.model.dto.response.MentorPricingResponse;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.security.utils.SecurityUtils;
import com.aiinterview.platform.service.payment.MentorPricingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/mentor-pricing")
@RequiredArgsConstructor
public class MentorPricingController {

    private final MentorPricingService mentorPricingService;
    private final UserRepository userRepository;

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('INTERVIEWER','ADMIN','STAFF')")
    public ResponseEntity<ApiResponse<MentorPricingResponse>> me() {
        User u = SecurityUtils.getCurrentUser(userRepository);
        return ResponseEntity.ok(ApiResponse.success(MentorPricingResponse.from(mentorPricingService.getOrCreate(u))));
    }

    @PutMapping("/me")
    @PreAuthorize("hasAnyRole('INTERVIEWER','ADMIN','STAFF')")
    public ResponseEntity<ApiResponse<MentorPricingResponse>> updateMe(@Valid @RequestBody MentorPricingRequest req) {
        User u = SecurityUtils.getCurrentUser(userRepository);
        return ResponseEntity.ok(ApiResponse.success(MentorPricingResponse.from(
                mentorPricingService.update(u, req.getMinPrice(), req.getMaxPrice(), req.getHourlyRate(), req.getLevel(), req.getFeatured()))));
    }

    @GetMapping("/{mentorId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MentorPricingResponse>> get(@PathVariable Long mentorId) {
        User u = userRepository.findById(mentorId).orElseThrow();
        return ResponseEntity.ok(ApiResponse.success(MentorPricingResponse.from(mentorPricingService.getOrCreate(u))));
    }
}
