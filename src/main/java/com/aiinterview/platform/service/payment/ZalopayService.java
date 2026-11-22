package com.aiinterview.platform.service.payment;

import com.aiinterview.platform.model.dto.request.ZalopayCallbackRequest;
import com.aiinterview.platform.model.dto.response.ZalopayCreateResponse;
import com.aiinterview.platform.model.entity.User;

import java.util.Map;

public interface ZalopayService {
    ZalopayCreateResponse createPayment(User user, long amount, String orderInfo);

    /** Callback server-to-server từ ZaloPay; trả về {return_code, return_message}. */
    Map<String, Object> handleCallback(ZalopayCallbackRequest request);

    /** Redirect từ cổng ZaloPay về; trả về URL frontend cần chuyển tới. */
    String handleReturn(Map<String, String> params);
}
