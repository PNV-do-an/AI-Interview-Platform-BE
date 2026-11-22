package com.aiinterview.platform.service.payment.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.model.dto.response.MomoCreateResponse;
import com.aiinterview.platform.model.entity.MomoTransaction;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.MomoStatus;
import com.aiinterview.platform.model.repository.MomoTransactionRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.service.payment.MomoService;
import com.aiinterview.platform.service.payment.WalletService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MoMo Payment Gateway v2: create -> redirect -> IPN -> cộng ví idempotent.
 * Credential lấy từ env (partnerCode/accessKey/secretKey); không hard-code secret.
 */
@Service
public class MomoServiceImpl implements MomoService {

    private final MomoTransactionRepository txnRepository;
    private final WalletService walletService;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate;

    public MomoServiceImpl(MomoTransactionRepository txnRepository,
                           WalletService walletService,
                           UserRepository userRepository,
                           RestTemplateBuilder restTemplateBuilder) {
        this.txnRepository = txnRepository;
        this.walletService = walletService;
        this.userRepository = userRepository;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Value("${app.momo.partner-code:}")
    private String partnerCode;
    @Value("${app.momo.partner-name:AI Interview}")
    private String partnerName;
    @Value("${app.momo.store-id:AIInterview}")
    private String storeId;
    @Value("${app.momo.access-key:}")
    private String accessKey;
    @Value("${app.momo.secret-key:}")
    private String secretKey;
    @Value("${app.momo.create-url:https://payment.momo.vn/v2/gateway/api/create}")
    private String createUrl;
    @Value("${app.momo.redirect-url:http://localhost:8081/api/v1/payment/momo/return}")
    private String redirectUrl;
    @Value("${app.momo.ipn-url:http://localhost:8081/api/v1/payment/momo/ipn}")
    private String ipnUrl;
    @Value("${app.momo.request-type:captureWallet}")
    private String requestType;
    @Value("${app.momo.order-type:momo_wallet}")
    private String orderType;
    @Value("${app.momo.lang:vi}")
    private String lang;
    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Override
    @Transactional
    public MomoCreateResponse createPayment(User user, long amount, String orderInfo) {
        if (amount <= 0) throw new BadRequestException("Số tiền phải lớn hơn 0");
        if (isBlank(partnerCode) || isBlank(accessKey) || isBlank(secretKey)) {
            throw new BadRequestException("MoMo chưa được cấu hình (partner-code/access-key/secret-key)");
        }
        String info = (orderInfo == null || orderInfo.isBlank()) ? "Nap tien vi" : orderInfo.trim();
        String orderId = System.currentTimeMillis() + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        MomoTransaction txn = MomoTransaction.builder()
                .userId(user.getId())
                .orderId(orderId)
                .amount(amount)
                .orderInfo(info)
                .status(MomoStatus.PENDING)
                .build();
        txnRepository.save(txn);

        String extraData = "";
        String signature = createSignature(orderId, amount, extraData, info);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partnerCode", partnerCode);
        body.put("partnerName", partnerName);
        body.put("storeId", storeId);
        body.put("requestId", orderId);
        body.put("orderId", orderId);
        body.put("amount", amount);
        body.put("lang", lang);
        body.put("redirectUrl", redirectUrl);
        body.put("ipnUrl", ipnUrl);
        body.put("orderInfo", info);
        body.put("orderType", orderType);
        body.put("requestType", requestType);
        body.put("extraData", extraData);
        body.put("signature", signature);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForEntity(
                    createUrl, jsonEntity(body), Map.class).getBody();
            int resultCode = toInt(resp == null ? null : resp.get("resultCode"));
            if (resultCode != 0) {
                throw new BadRequestException("MoMo tạo đơn thất bại: "
                        + (resp == null ? "không có phản hồi" : String.valueOf(resp.get("message"))));
            }
            String payUrl = str(resp.get("payUrl"));
            if (payUrl.isBlank()) throw new BadRequestException("MoMo không trả payUrl");
            return MomoCreateResponse.builder()
                    .paymentUrl(payUrl)
                    .qrCodeUrl(str(resp.get("qrCodeUrl")))
                    .deeplink(str(resp.get("deeplink")))
                    .orderId(orderId)
                    .requestId(orderId)
                    .build();
        } catch (Exception e) {
            txn.setStatus(MomoStatus.FAILED);
            txnRepository.save(txn);
            if (e instanceof BadRequestException) throw (BadRequestException) e;
            throw new BadRequestException("Không thể kết nối MoMo: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public Map<String, Object> handleCallback(Map<String, String> params) {
        if (!verifySignature(params)) return callbackResult(1, "Invalid signature");
        String orderId = params.get("orderId");
        MomoTransaction txn = isBlank(orderId) ? null
                : txnRepository.findByOrderIdForUpdate(orderId).orElse(null);
        if (txn == null) return callbackResult(1, "Order not found");
        if (toLong(params.get("amount")) != txn.getAmount()) return callbackResult(1, "Invalid amount");

        if (toInt(params.get("resultCode")) == 0) {
            confirmSuccess(txn, params);
        } else if (txn.getStatus() == MomoStatus.PENDING) {
            txn.setStatus(MomoStatus.FAILED);
            txn.setResultCode(params.get("resultCode"));
            txnRepository.save(txn);
        }
        return callbackResult(0, "Success");
    }

    @Override
    @Transactional
    public String handleReturn(Map<String, String> params) {
        if (!verifySignature(params)) return resultPage("invalid-signature", params.get("orderId"));
        String orderId = params.get("orderId");
        MomoTransaction txn = isBlank(orderId) ? null : txnRepository.findByOrderId(orderId).orElse(null);
        if (txn == null) return resultPage("error", orderId);
        if (txn.getStatus() == MomoStatus.SUCCESS) return resultPage("success", orderId);
        if (toInt(params.get("resultCode")) == 0) {
            confirmSuccess(txn, params);
            return resultPage("success", orderId);
        }
        if (txn.getStatus() == MomoStatus.PENDING) {
            txn.setStatus(MomoStatus.FAILED);
            txn.setResultCode(params.get("resultCode"));
            txnRepository.save(txn);
        }
        return resultPage("failed", orderId);
    }

    /** Đánh dấu thành công + cộng ví. Gọi lại nhiều lần vẫn chỉ cộng 1 lần. */
    private void confirmSuccess(MomoTransaction txn, Map<String, String> params) {
        if (txn.getStatus() == MomoStatus.SUCCESS) return;
        txn.setStatus(MomoStatus.SUCCESS);
        txn.setResultCode(params.get("resultCode"));
        txn.setTransId(params.get("transId"));
        txn.setPayType(params.get("payType"));
        txnRepository.save(txn);

        User user = userRepository.findById(txn.getUserId()).orElse(null);
        if (user != null) {
            walletService.creditAvailable(user, txn.getAmount(), "MOMO_TOPUP", "MOMO",
                    txn.getId(), txn.getOrderId(), "Nap tien qua MoMo " + txn.getOrderId());
        }
    }

    private String createSignature(String orderId, long amount, String extraData, String orderInfo) {
        String raw = "accessKey=" + accessKey
                + "&amount=" + amount
                + "&extraData=" + extraData
                + "&ipnUrl=" + ipnUrl
                + "&lang=" + lang
                + "&orderId=" + orderId
                + "&orderInfo=" + orderInfo
                + "&orderType=" + orderType
                + "&partnerCode=" + partnerCode
                + "&redirectUrl=" + redirectUrl
                + "&requestId=" + orderId
                + "&requestType=" + requestType;
        return hmacSHA256(secretKey, raw);
    }

    private boolean verifySignature(Map<String, String> params) {
        String provided = params.get("signature");
        if (isBlank(provided) || isBlank(secretKey)) return false;
        String raw = "accessKey=" + accessKey
                + "&amount=" + str(params.get("amount"))
                + "&extraData=" + str(params.get("extraData"))
                + "&message=" + str(params.get("message"))
                + "&orderId=" + str(params.get("orderId"))
                + "&orderType=" + orderType
                + "&partnerCode=" + str(params.get("partnerCode"))
                + "&payType=" + str(params.get("payType"))
                + "&requestId=" + str(params.get("requestId"))
                + "&responseTime=" + str(params.get("responseTime"))
                + "&resultCode=" + str(params.get("resultCode"))
                + "&transId=" + str(params.get("transId"));
        return constantEquals(hmacSHA256(secretKey, raw), provided);
    }

    private HttpEntity<Map<String, Object>> jsonEntity(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private Map<String, Object> callbackResult(int code, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resultCode", code);
        result.put("message", message);
        return result;
    }

    private String resultPage(String status, String orderId) {
        return frontendUrl + "/payment/momo/result?status=" + safe(status)
                + "&orderId=" + safe(orderId);
    }

    private String hmacSHA256(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC SHA256 error", e);
        }
    }

    private boolean constantEquals(String expected, String provided) {
        if (expected == null || provided == null) return false;
        return MessageDigest.isEqual(
                expected.toLowerCase().getBytes(StandardCharsets.UTF_8),
                provided.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    private int toInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value != null) {
            try {
                return Integer.parseInt(value.toString().trim());
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    private long toLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value != null) {
            try {
                return Long.parseLong(value.toString().trim());
            } catch (NumberFormatException ignored) {
                return -1L;
            }
        }
        return -1L;
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String safe(String s) {
        if (s == null) return "";
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
