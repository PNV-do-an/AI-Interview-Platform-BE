package com.aiinterview.platform.service.payment;

import com.aiinterview.platform.model.dto.response.MomoCreateResponse;
import com.aiinterview.platform.model.entity.User;

import java.util.Map;

public interface MomoService {
    MomoCreateResponse createPayment(User user, long amount, String orderInfo);

    /** MoMo IPN server-to-server; luôn trả resultCode/message. */
    Map<String, Object> handleCallback(Map<String, String> params);

    /** Browser redirect từ MoMo; trả URL trang kết quả frontend. */
    String handleReturn(Map<String, String> params);
}
