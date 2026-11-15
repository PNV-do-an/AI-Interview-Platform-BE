package com.aiinterview.platform.controller.commerce;

import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.request.OrderRequest;
import com.aiinterview.platform.model.dto.response.OrderResponse;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.security.utils.SecurityUtils;
import com.aiinterview.platform.service.commerce.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final UserRepository userRepository;

    /** POST /api/v1/orders — nhà tuyển dụng tạo đơn hàng cho gói dịch vụ */
    @PostMapping
    @PreAuthorize("hasAnyRole('INTERVIEWER', 'ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<OrderResponse>> create(@Valid @RequestBody OrderRequest request) {
        User buyer = SecurityUtils.getCurrentUser(userRepository);
        return ResponseEntity.ok(ApiResponse.success(orderService.createOrder(buyer, request)));
    }

    /** GET /api/v1/orders/my — lịch sử đơn hàng của tôi */
    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('INTERVIEWER', 'ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getMyOrders() {
        User buyer = SecurityUtils.getCurrentUser(userRepository);
        return ResponseEntity.ok(ApiResponse.success(orderService.getMyOrders(buyer)));
    }

    /** GET /api/v1/orders/{id} — chi tiết đơn hàng (chủ đơn hoặc admin/staff) */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('INTERVIEWER', 'ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(@PathVariable Long id) {
        User currentUser = SecurityUtils.getCurrentUser(userRepository);
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrder(currentUser, id)));
    }

    /** POST /api/v1/orders/{id}/pay — thanh toán (giả lập cổng thanh toán) */
    @PostMapping("/{id}/pay")
    @PreAuthorize("hasAnyRole('INTERVIEWER', 'ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<OrderResponse>> pay(@PathVariable Long id) {
        User buyer = SecurityUtils.getCurrentUser(userRepository);
        return ResponseEntity.ok(ApiResponse.success("Thanh toán thành công — email xác nhận đã được gửi",
                orderService.pay(buyer, id)));
    }

    /** POST /api/v1/orders/{id}/cancel — hủy đơn chờ thanh toán */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('INTERVIEWER', 'ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<OrderResponse>> cancel(@PathVariable Long id) {
        User buyer = SecurityUtils.getCurrentUser(userRepository);
        return ResponseEntity.ok(ApiResponse.success(orderService.cancel(buyer, id)));
    }

    /** GET /api/v1/orders/admin/all — toàn bộ đơn hàng (quản trị) */
    @GetMapping("/admin/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getAllOrders() {
        return ResponseEntity.ok(ApiResponse.success(orderService.getAllOrders()));
    }
}
