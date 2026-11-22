package com.aiinterview.platform.controller.payment;

import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.request.MomoCreateRequest;
import com.aiinterview.platform.model.dto.response.MomoCreateResponse;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.security.utils.SecurityUtils;
import com.aiinterview.platform.service.payment.MomoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payment/momo")
@RequiredArgsConstructor
public class MomoController {

    private final MomoService momoService;
    private final UserRepository userRepository;

    @PostMapping("/create")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MomoCreateResponse>> create(@Valid @RequestBody MomoCreateRequest request) {
        User currentUser = SecurityUtils.getCurrentUser(userRepository);
        MomoCreateResponse result = momoService.createPayment(currentUser, request.getAmount(), request.getOrderInfo());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /** MoMo IPN server-to-server. */
    @PostMapping("/ipn")
    public ResponseEntity<Map<String, Object>> ipn(@RequestBody Map<String, String> params) {
        return ResponseEntity.ok(momoService.handleCallback(params));
    }

    /** Browser redirect từ MoMo. */
    @GetMapping("/return")
    public ResponseEntity<Void> returnFromGateway(@RequestParam Map<String, String> params) {
        return ResponseEntity.status(302).location(URI.create(momoService.handleReturn(params))).build();
    }
}
