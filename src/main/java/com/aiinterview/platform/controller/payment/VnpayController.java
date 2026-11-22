package com.aiinterview.platform.controller.payment;

import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.request.VnpayCreateRequest;
import com.aiinterview.platform.model.dto.response.VnpayCreateResponse;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.security.utils.SecurityUtils;
import com.aiinterview.platform.service.payment.VnpayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payment/vnpay")
@RequiredArgsConstructor
public class VnpayController {

    private final VnpayService vnpayService;
    private final UserRepository userRepository;

    @PostMapping("/create")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<VnpayCreateResponse>> create(@Valid @RequestBody VnpayCreateRequest req) {
        User u = SecurityUtils.getCurrentUser(userRepository);
        VnpayCreateResponse resp = vnpayService.createPaymentUrl(u, req.getAmount(), req.getOrderInfo());
        return ResponseEntity.ok(ApiResponse.success(resp));
    }

    @GetMapping("/return")
    public ResponseEntity<Void> vnpayReturn(@RequestParam Map<String, String> params) {
        String redirect = vnpayService.handleReturn(params);
        return ResponseEntity.status(302).location(URI.create(redirect)).build();
    }

    @GetMapping("/ipn")
    public ResponseEntity<Map<String, String>> ipnGet(@RequestParam Map<String, String> params) {
        return ResponseEntity.ok(vnpayService.handleIpn(params));
    }

    @PostMapping("/ipn")
    public ResponseEntity<Map<String, String>> ipnPost(@RequestParam Map<String, String> params) {
        return ResponseEntity.ok(vnpayService.handleIpn(params));
    }
}