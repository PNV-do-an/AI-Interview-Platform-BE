package com.aiinterview.platform.controller;

import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.request.TopupRequest;
import com.aiinterview.platform.model.dto.request.TopupWebhookRequest;
import com.aiinterview.platform.model.dto.response.WalletResponse;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.security.utils.SecurityUtils;
import com.aiinterview.platform.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final UserRepository userRepository;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<WalletResponse>> me() {
        User u = SecurityUtils.getCurrentUser(userRepository);
        return ResponseEntity.ok(ApiResponse.success(WalletResponse.from(walletService.getOrCreateWallet(u))));
    }

    @PostMapping("/topup")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<WalletResponse>> topup(@Valid @RequestBody TopupRequest req) {
        User u = SecurityUtils.getCurrentUser(userRepository);
        walletService.topUp(u, req.getAmount(), req.getIdempotencyKey(), "Nap tien");
        return ResponseEntity.ok(ApiResponse.success(WalletResponse.from(walletService.getOrCreateWallet(u))));
    }

    @PostMapping("/webhook/topup")
    public ResponseEntity<ApiResponse<String>> webhook(@RequestBody TopupWebhookRequest req) {
        // resolve user
        User u = userRepository.findById(req.getUserId()).orElseThrow();
        walletService.topUp(u, req.getAmount(), req.getProviderTxId(), "Webhook topup");
        return ResponseEntity.ok(ApiResponse.success("OK"));
    }
}
