package com.aiinterview.platform.controller;

import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.request.WithdrawalRequest;
import com.aiinterview.platform.model.dto.response.WithdrawalResponse;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.security.utils.SecurityUtils;
import com.aiinterview.platform.service.WithdrawalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/withdrawals")
@RequiredArgsConstructor
public class WithdrawalController {

    private final WithdrawalService withdrawalService;
    private final UserRepository userRepository;

    @PostMapping
    @PreAuthorize("hasAnyRole('INTERVIEWER','ADMIN','STAFF')")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> request(@Valid @RequestBody WithdrawalRequest req) {
        User u = SecurityUtils.getCurrentUser(userRepository);
        return ResponseEntity.ok(ApiResponse.success(WithdrawalResponse.from(
                withdrawalService.requestWithdrawal(u, req.getAmount(), req.getBankName(), req.getAccountNumber(), req.getAccountHolder()))));
    }

    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('INTERVIEWER','ADMIN','STAFF')")
    public ResponseEntity<ApiResponse<List<WithdrawalResponse>>> my() {
        User u = SecurityUtils.getCurrentUser(userRepository);
        return ResponseEntity.ok(ApiResponse.success(withdrawalService.getMyWithdrawals(u).stream().map(WithdrawalResponse::from).collect(Collectors.toList())));
    }

    @GetMapping("/admin/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<WithdrawalResponse>>> pending() {
        return ResponseEntity.ok(ApiResponse.success(withdrawalService.getPending().stream().map(WithdrawalResponse::from).collect(Collectors.toList())));
    }

    @PostMapping("/admin/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> approve(@PathVariable Long id, @RequestParam(required = false) String note) {
        return ResponseEntity.ok(ApiResponse.success(WithdrawalResponse.from(withdrawalService.approve(id, note))));
    }

    @PostMapping("/admin/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> reject(@PathVariable Long id, @RequestParam(required = false) String note) {
        return ResponseEntity.ok(ApiResponse.success(WithdrawalResponse.from(withdrawalService.reject(id, note))));
    }
}
