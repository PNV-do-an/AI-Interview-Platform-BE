package com.aiinterview.platform.controller;

import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.entity.AiCreditPackage;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.repository.AiCreditPackageRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.security.utils.SecurityUtils;
import com.aiinterview.platform.service.AiCreditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai-credits")
@RequiredArgsConstructor
public class AiCreditController {

    private final AiCreditService aiCreditService;
    private final AiCreditPackageRepository packageRepository;
    private final UserRepository userRepository;

    @GetMapping("/balance")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> balance() {
        User u = SecurityUtils.getCurrentUser(userRepository);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "balance", aiCreditService.getBalance(u),
                "todayUsage", aiCreditService.getTodayUsage(u))));
    }

    @GetMapping("/packages")
    public ResponseEntity<ApiResponse<List<AiCreditPackage>>> packages() {
        return ResponseEntity.ok(ApiResponse.success(packageRepository.findByActiveTrueOrderByPriceAsc()));
    }

    @PostMapping("/purchase/{packageId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> purchase(@PathVariable Long packageId) {
        User u = SecurityUtils.getCurrentUser(userRepository);
        aiCreditService.purchasePackage(u, packageId);
        return ResponseEntity.ok(ApiResponse.success("Mua goi thanh cong"));
    }

    @PostMapping("/use")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> use(@RequestParam String difficulty) {
        User u = SecurityUtils.getCurrentUser(userRepository);
        aiCreditService.useCredit(u, difficulty);
        return ResponseEntity.ok(ApiResponse.success("OK"));
    }
}
