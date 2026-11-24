package com.aiinterview.platform.service.payment;

import com.aiinterview.platform.model.dto.request.PayosWebhookRequest;
import com.aiinterview.platform.model.dto.response.PayosCreateResponse;
import com.aiinterview.platform.model.dto.response.PayosStatusResponse;
import com.aiinterview.platform.model.entity.User;

import java.util.Map;

public interface PayosService {
    PayosCreateResponse createPayment(User user, long amount, String description);

    /** Webhook server-to-server từ payOS; trả JSON xác nhận đã nhận. */
    Map<String, Object> handleWebhook(PayosWebhookRequest request);

    /** Browser redirect từ payOS; trả URL frontend cần chuyển tới. */
    String handleReturn(Map<String, String> params);

    /**
     * Xác thực trạng thái một giao dịch với payOS và cộng ví nếu đã thanh toán.
     * Đây là fallback khi webhook/return URL bị lỡ.
     */
    PayosStatusResponse getPaymentStatus(User user, long orderCode);
}
