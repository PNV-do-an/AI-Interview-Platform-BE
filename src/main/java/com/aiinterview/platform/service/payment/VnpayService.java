package com.aiinterview.platform.service.payment;

import com.aiinterview.platform.model.dto.response.VnpayCreateResponse;
import com.aiinterview.platform.model.entity.User;

import java.util.Map;

public interface VnpayService {
    VnpayCreateResponse createPaymentUrl(User user, long amount, String orderInfo);
    String handleReturn(Map<String, String> params);
    Map<String, String> handleIpn(Map<String, String> params);
}