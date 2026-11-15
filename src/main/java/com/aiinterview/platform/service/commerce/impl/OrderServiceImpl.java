package com.aiinterview.platform.service.commerce.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.model.dto.request.OrderRequest;
import com.aiinterview.platform.model.dto.response.OrderResponse;
import com.aiinterview.platform.model.entity.Order;
import com.aiinterview.platform.model.entity.ServicePackage;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.OrderStatus;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.OrderRepository;
import com.aiinterview.platform.model.repository.ServicePackageRepository;
import com.aiinterview.platform.service.infra.EmailService;
import com.aiinterview.platform.service.commerce.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ServicePackageRepository servicePackageRepository;
    private final EmailService emailService;

    @Override
    @Transactional
    public OrderResponse createOrder(User buyer, OrderRequest request) {
        ServicePackage pkg = servicePackageRepository.findById(request.getPackageId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy gói dịch vụ"));
        if (!Boolean.TRUE.equals(pkg.getActive())) {
            throw new BadRequestException("Gói dịch vụ này hiện không còn mở bán");
        }

        Order order = Order.builder()
                .orderCode(generateOrderCode())
                .user(buyer)
                .servicePackage(pkg)
                .packageName(pkg.getName())
                .amount(pkg.getPrice())
                .status(OrderStatus.PENDING)
                .paymentMethod(request.getPaymentMethod())
                .build();
        order = orderRepository.save(order);
        return OrderResponse.from(order);
    }

    @Override
    @Transactional
    public OrderResponse pay(User buyer, Long orderId) {
        Order order = ownOrder(buyer, orderId);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BadRequestException("Chỉ đơn hàng ở trạng thái chờ thanh toán mới có thể thanh toán");
        }
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        order = orderRepository.save(order);

        emailService.sendOrderConfirmationEmail(
                buyer.getEmail(),
                buyer.getFullName(),
                order.getPackageName(),
                order.getAmount(),
                order.getOrderCode());
        log.info("Order {} paid by {} via {}", order.getOrderCode(), buyer.getEmail(), order.getPaymentMethod());
        return OrderResponse.from(order);
    }

    @Override
    @Transactional
    public OrderResponse cancel(User buyer, Long orderId) {
        Order order = ownOrder(buyer, orderId);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BadRequestException("Chỉ đơn hàng ở trạng thái chờ thanh toán mới có thể hủy");
        }
        order.setStatus(OrderStatus.CANCELLED);
        return OrderResponse.from(orderRepository.save(order));
    }

    @Override
    public List<OrderResponse> getMyOrders(User buyer) {
        return orderRepository.findByUserOrderByCreatedAtDesc(buyer).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @Override
    public OrderResponse getOrder(User currentUser, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng"));
        boolean isOwner = order.getUser().getId().equals(currentUser.getId());
        boolean isStaff = currentUser.getRole() == Role.ROLE_ADMIN || currentUser.getRole() == Role.ROLE_STAFF;
        if (!isOwner && !isStaff) {
            throw new BadRequestException("Bạn không có quyền xem đơn hàng này");
        }
        return OrderResponse.from(order);
    }

    @Override
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(OrderResponse::from)
                .toList();
    }

    private Order ownOrder(User buyer, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng"));
        if (!order.getUser().getId().equals(buyer.getId())) {
            throw new BadRequestException("Bạn không phải chủ sở hữu đơn hàng này");
        }
        return order;
    }

    private String generateOrderCode() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int suffix = ThreadLocalRandom.current().nextInt(1000, 10000);
        return "ORD-" + date + "-" + suffix;
    }
}
