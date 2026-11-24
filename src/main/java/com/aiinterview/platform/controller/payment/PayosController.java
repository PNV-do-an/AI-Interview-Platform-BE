package com.aiinterview.platform.controller.payment;

import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.request.PayosCreateRequest;
import com.aiinterview.platform.model.dto.request.PayosWebhookRequest;
import com.aiinterview.platform.model.dto.response.PayosCreateResponse;
import com.aiinterview.platform.model.dto.response.PayosStatusResponse;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.security.utils.SecurityUtils;
import com.aiinterview.platform.service.payment.PayosService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

/** payOS checkout: FE tạo đơn, payOS gọi webhook, browser redirect về FE qua /return. */
@RestController
@RequestMapping("/api/v1/payment/payos")
@RequiredArgsConstructor
public class PayosController {

    private final PayosService payosService;
    private final UserRepository userRepository;

    @PostMapping("/create")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PayosCreateResponse>> create(@Valid @RequestBody PayosCreateRequest req) {
        User user = SecurityUtils.getCurrentUser(userRepository);
        return ResponseEntity.ok(ApiResponse.success(
                payosService.createPayment(user, req.getAmount(), req.getDescription())));
    }

    /**
     * FE gọi sau khi payOS redirect (hoặc khi người dùng bấm kiểm tra lại) để backend
     * đối soát với payOS. Nếu đã thanh toán, ví được cộng ngay cả khi webhook bị lỡ.
     */
    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PayosStatusResponse>> status(@RequestParam long orderCode) {
        User user = SecurityUtils.getCurrentUser(userRepository);
        return ResponseEntity.ok(ApiResponse.success(
                payosService.getPaymentStatus(user, orderCode)));
    }

    /** Server-to-server từ payOS. Luôn trả 2XX để payOS không retry vô hạn. */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(@RequestBody PayosWebhookRequest req) {
        return ResponseEntity.ok(payosService.handleWebhook(req));
    }

    /** Browser redirect từ payOS; BE verify lại rồi 302 về trang kết quả của FE. */
    @GetMapping("/return")
    public ResponseEntity<Void> returnFromPayos(@RequestParam Map<String, String> params) {
        return redirectToResult(params);
    }

    /** payOS redirect về đây khi người dùng bấm huỷ trên trang checkout. */
    @GetMapping("/cancel")
    public ResponseEntity<Void> cancelFromPayos(@RequestParam Map<String, String> params) {
        return redirectToResult(params);
    }

    private ResponseEntity<Void> redirectToResult(Map<String, String> params) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(payosService.handleReturn(params)))
                .build();
    }
}
