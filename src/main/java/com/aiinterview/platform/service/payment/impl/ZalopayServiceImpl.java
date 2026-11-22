package com.aiinterview.platform.service.payment.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.model.dto.request.ZalopayCallbackRequest;
import com.aiinterview.platform.model.dto.response.ZalopayCreateResponse;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.entity.ZalopayTransaction;
import com.aiinterview.platform.model.enums.ZalopayStatus;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.model.repository.ZalopayTransactionRepository;
import com.aiinterview.platform.service.payment.WalletService;
import com.aiinterview.platform.service.payment.ZalopayService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ZaloPay Gateway v2: create -> redirect -> callback/query -> cộng ví idempotent.
 * Dùng credential sandbox công khai của ZaloPay (AppID 554) làm mặc định để không cần đăng ký.
 */
@Service
public class ZalopayServiceImpl implements ZalopayService {

    private final ZalopayTransactionRepository txnRepository;
    private final WalletService walletService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public ZalopayServiceImpl(ZalopayTransactionRepository txnRepository,
                              WalletService walletService,
                              UserRepository userRepository,
                              ObjectMapper objectMapper,
                              RestTemplateBuilder restTemplateBuilder) {
        this.txnRepository = txnRepository;
        this.walletService = walletService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Value("${app.zalopay.app-id:554}")
    private String appId;
    @Value("${app.zalopay.key1:}")
    private String key1;
    @Value("${app.zalopay.key2:}")
    private String key2;
    @Value("${app.zalopay.create-url:https://sb-openapi.zalopay.vn/v2/create}")
    private String createUrl;
    @Value("${app.zalopay.query-url:https://sb-openapi.zalopay.vn/v2/query}")
    private String queryUrl;
    @Value("${app.zalopay.callback-url:http://localhost:8080/api/v1/payment/zalopay/callback}")
    private String callbackUrl;
    @Value("${app.zalopay.redirect-url:http://localhost:3000/payment/zalopay/result}")
    private String redirectUrl;
    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Override
    @Transactional
    public ZalopayCreateResponse createPayment(User user, long amount, String orderInfo) {
        if (amount <= 0) throw new BadRequestException("Số tiền phải lớn hơn 0");
        if (isBlank(appId) || isBlank(key1) || isBlank(key2)) {
            throw new BadRequestException("ZaloPay chưa được cấu hình (app-id/key1/key2)");
        }
        String info = (orderInfo == null || orderInfo.isBlank()) ? "Nap tien vi" : orderInfo.trim();

        String appTransId = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))
                .format(DateTimeFormatter.ofPattern("yyMMdd"))
                + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long appTime = System.currentTimeMillis();
        String appUser = "user_" + user.getId();
        String embedData = "{\"redirecturl\":\"" + jsonEscape(redirectUrl) + "\"}";
        String item = "[]";

        String macInput = appId + "|" + appTransId + "|" + appUser + "|" + amount
                + "|" + appTime + "|" + embedData + "|" + item;

        ZalopayTransaction txn = ZalopayTransaction.builder()
                .userId(user.getId())
                .appTransId(appTransId)
                .amount(amount)
                .orderInfo(info)
                .status(ZalopayStatus.PENDING)
                .build();
        txnRepository.save(txn);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("app_id", Integer.parseInt(appId));
        body.put("app_trans_id", appTransId);
        body.put("app_user", appUser);
        body.put("app_time", appTime);
        body.put("amount", amount);
        body.put("embed_data", embedData);
        body.put("item", item);
        body.put("description", "Nap tien vi " + appTransId);
        body.put("bank_code", "");
        body.put("callback_url", callbackUrl);
        body.put("mac", hmacSHA256(key1, macInput));

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForEntity(
                    createUrl, jsonEntity(body), Map.class).getBody();
            if (resp == null || toInt(resp.get("return_code")) != 1) {
                throw new BadRequestException("ZaloPay tạo đơn thất bại: "
                        + (resp == null ? "không có phản hồi" : String.valueOf(resp.get("return_message"))));
            }
            String orderUrl = str(resp.get("order_url"));
            if (orderUrl.isBlank()) throw new BadRequestException("ZaloPay không trả order_url");
            return ZalopayCreateResponse.builder().paymentUrl(orderUrl).appTransId(appTransId).build();
        } catch (Exception e) {
            txn.setStatus(ZalopayStatus.FAILED);
            txnRepository.save(txn);
            if (e instanceof BadRequestException) throw (BadRequestException) e;
            throw new BadRequestException("Không thể kết nối ZaloPay: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public Map<String, Object> handleCallback(ZalopayCallbackRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            if (request == null || isBlank(request.getData()) || isBlank(request.getMac())) {
                return callbackResult(-1, "invalid payload");
            }
            String dataStr = request.getData();
            if (!constantEquals(hmacSHA256(key2, dataStr), request.getMac())) {
                return callbackResult(-1, "mac not equal");
            }

            JsonNode data = objectMapper.readTree(dataStr);
            String appTransId = data.path("app_trans_id").asText(null);
            long amount = data.path("amount").asLong(0L);

            ZalopayTransaction txn = appTransId == null ? null
                    : txnRepository.findByAppTransIdForUpdate(appTransId).orElse(null);
            if (txn == null) return callbackResult(-1, "order not found");
            if (amount != txn.getAmount()) return callbackResult(-1, "invalid amount");

            confirmSuccess(txn, data.path("zp_trans_id").asText(""), data.path("channel").asText(""));
            return callbackResult(1, "success");
        } catch (Exception e) {
            // return_code 0 => ZaloPay sẽ callback lại (tối đa 3 lần)
            return callbackResult(0, String.valueOf(e.getMessage()));
        }
    }

    @Override
    @Transactional
    public String handleReturn(Map<String, String> params) {
        String appTransId = params.get("apptransid");
        String status = params.get("status");

        String data = str(params.get("appid")) + "|" + str(appTransId) + "|" + str(params.get("pmcid"))
                + "|" + str(params.get("bankcode")) + "|" + str(params.get("amount"))
                + "|" + str(params.get("discountamount")) + "|" + str(status);
        if (!constantEquals(hmacSHA256(key2, data), params.get("checksum"))) {
            return resultPage("invalid-signature", appTransId);
        }
        ZalopayTransaction txn = appTransId == null ? null
                : txnRepository.findByAppTransId(appTransId).orElse(null);
        if (txn == null) return resultPage("error", appTransId);
        if (txn.getStatus() == ZalopayStatus.SUCCESS) return resultPage("success", appTransId);

        if ("1".equals(status)) {
            // Redirect có checksum hợp lệ nhưng callback có thể chưa tới -> truy vấn trạng thái.
            int queryCode = queryOrder(appTransId);
            if (queryCode == 1) {
                confirmSuccess(txn, "", "");
                return resultPage("success", appTransId);
            }
            if (queryCode == 3) return resultPage("pending", appTransId);
        }

        if (txn.getStatus() == ZalopayStatus.PENDING) {
            txn.setStatus(ZalopayStatus.FAILED);
            txnRepository.save(txn);
        }
        return resultPage("failed", appTransId);
    }

    /** Đánh dấu thành công + cộng ví. Gọi lại nhiều lần vẫn chỉ cộng 1 lần. */
    private void confirmSuccess(ZalopayTransaction txn, String zpTransId, String channel) {
        if (txn.getStatus() == ZalopayStatus.SUCCESS) return;
        txn.setStatus(ZalopayStatus.SUCCESS);
        if (zpTransId != null && !zpTransId.isBlank()) txn.setZpTransId(zpTransId);
        if (channel != null && !channel.isBlank()) txn.setChannel(channel);
        txnRepository.save(txn);

        User user = userRepository.findById(txn.getUserId()).orElse(null);
        if (user != null) {
            walletService.creditAvailable(user, txn.getAmount(), "ZALOPAY_TOPUP", "ZALOPAY",
                    txn.getId(), txn.getAppTransId(), "Nap tien qua ZaloPay " + txn.getAppTransId());
        }
    }

    private int queryOrder(String appTransId) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("app_id", Integer.parseInt(appId));
            body.put("app_trans_id", appTransId);
            body.put("mac", hmacSHA256(key1, appId + "|" + appTransId + "|" + key1));
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForEntity(
                    queryUrl, jsonEntity(body), Map.class).getBody();
            return resp == null ? -1 : toInt(resp.get("return_code"));
        } catch (Exception e) {
            return -1;
        }
    }

    private HttpEntity<Map<String, Object>> jsonEntity(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private Map<String, Object> callbackResult(int code, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("return_code", code);
        result.put("return_message", message);
        return result;
    }

    private String resultPage(String status, String appTransId) {
        return frontendUrl + "/payment/zalopay/result?status=" + safe(status)
                + "&appTransId=" + safe(appTransId);
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

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String jsonEscape(String s) {
        return str(s).replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String safe(String s) {
        if (s == null) return "";
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return s;
        }
    }
}
