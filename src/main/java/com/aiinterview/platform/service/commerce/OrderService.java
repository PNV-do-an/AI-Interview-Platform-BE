package com.aiinterview.platform.service.commerce;

import com.aiinterview.platform.model.dto.request.OrderRequest;
import com.aiinterview.platform.model.dto.response.OrderResponse;
import com.aiinterview.platform.model.entity.User;

import java.util.List;

public interface OrderService {

    OrderResponse createOrder(User buyer, OrderRequest request);

    OrderResponse pay(User buyer, Long orderId);

    OrderResponse cancel(User buyer, Long orderId);

    List<OrderResponse> getMyOrders(User buyer);

    OrderResponse getOrder(User currentUser, Long orderId);

    List<OrderResponse> getAllOrders();
}
