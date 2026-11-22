package com.aiinterview.platform.controller.payment;

import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.request.TopupRequest;
import com.aiinterview.platform.model.dto.request.TopupWebhookRequest;
import com.aiinterview.platform.model.dto.request.UpgradeAccountRequest;
import com.aiinterview.platform.model.dto.response.WalletResponse;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.InterviewPlan;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.security.utils.SecurityUtils;
import com.aiinterview.platform.service.payment.WalletService;
import com.aiinterview.platform.common.exception.BadRequestException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

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

    @PostMapping("/upgrade")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> upgrade(@Valid @RequestBody UpgradeAccountRequest req) {
        User u = SecurityUtils.getCurrentUser(userRepository);
        String planName = req.getPlan().trim().toUpperCase();
        String cycle = req.getBillingCycle().trim().toUpperCase();
        if (!planName.equals("NORMAL") && !planName.equals("ADVANCED")) {
            throw new BadRequestException("Gói nâng cấp không hợp lệ");
        }
        if (!cycle.equals("MONTHLY") && !cycle.equals("YEARLY")) {
            throw new BadRequestException("Chu kỳ thanh toán không hợp lệ");
        }
        long price = priceOf(planName, cycle);
        walletService.debitAvailable(u, price, "PACKAGE_PURCHASE", "USER", u.getId(),
                "UPGRADE-" + u.getId() + "-" + planName + "-" + cycle,
                "Nang cap goi " + planName);
        u.setInterviewPlan(InterviewPlan.valueOf(planName));
        userRepository.save(u);
        Map<String, Object> body = new HashMap<>();
        body.put("plan", planName);
        body.put("billingCycle", cycle);
        body.put("amount", price);
        body.put("wallet", WalletResponse.from(walletService.getOrCreateWallet(u)));
        return ResponseEntity.ok(ApiResponse.success("Nang cap tai khoan thanh cong", body));
    }

    private long priceOf(String plan, String cycle) {
        if (plan.equals("NORMAL")) {
            return 99_000L;
        }
        return 299_000L;
    }

    @PostMapping("/webhook/topup")
    public ResponseEntity<ApiResponse<String>> webhook(@RequestBody TopupWebhookRequest req) {
        // resolve user
        User u = userRepository.findById(req.getUserId()).orElseThrow();
        walletService.topUp(u, req.getAmount(), req.getProviderTxId(), "Webhook topup");
        return ResponseEntity.ok(ApiResponse.success("OK"));
    }
}

