package com.aiinterview.platform.controller.payment;

import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.request.ZalopayCallbackRequest;
import com.aiinterview.platform.model.dto.request.ZalopayCreateRequest;
import com.aiinterview.platform.model.dto.response.ZalopayCreateResponse;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.security.utils.SecurityUtils;
import com.aiinterview.platform.service.payment.ZalopayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payment/zalopay")
@RequiredArgsConstructor
public class ZalopayController {
    private final ZalopayService zalopayService;
    private final UserRepository userRepository;

    /** Tạo đơn nạp ví; trả order_url để frontend redirect. */
    @PostMapping("/create")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ZalopayCreateResponse>> create(@Valid @RequestBody ZalopayCreateRequest request) {
        User currentUser = SecurityUtils.getCurrentUser(userRepository);
        ZalopayCreateResponse result = zalopayService.createPayment(currentUser, request.getAmount(), request.getOrderInfo());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /** ZaloPay gọi server-to-server; phải trả {return_code, return_message}. */
    @PostMapping("/callback")
    public ResponseEntity<Map<String, Object>> callback(@RequestBody ZalopayCallbackRequest request) {
        return ResponseEntity.ok(zalopayService.handleCallback(request));
    }

    /** Trang redirect của ZaloPay về: verify checksum rồi 302 về frontend. */
    @GetMapping("/return")
    public ResponseEntity<Void> returnFromGateway(@RequestParam Map<String, String> params) {
        return ResponseEntity.status(302).location(URI.create(zalopayService.handleReturn(params))).build();
    }
}
