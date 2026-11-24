package com.aiinterview.platform.service.payment.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.model.dto.request.PayosWebhookRequest;
import com.aiinterview.platform.model.dto.response.PayosCreateResponse;
import com.aiinterview.platform.model.entity.PayosTransaction;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.PayosStatus;
import com.aiinterview.platform.model.repository.PayosTransactionRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.service.payment.PayosService;
import com.aiinterview.platform.service.payment.WalletService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * payOS payment-request v2: create link -> redirect -> webhook/query -> cộng ví idempotent.
 * payOS hiện không có sandbox riêng; credential được đưa qua app.payos.* / env.
 */
@Service
public class PayosServiceImpl implements PayosService {

    private final PayosTransactionRepository txnRepository;
    private final WalletService walletService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public PayosServiceImpl(PayosTransactionRepository txnRepository,
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

    @Value("${app.payos.client-id:}")
    private String clientId;
    @Value("${app.payos.api-key:}")
    private String apiKey;
    @Value("${app.payos.checksum-key:}")
    private String checksumKey;
    @Value("${app.payos.base-url:https://api-merchant.payos.vn}")
    private String baseUrl;
    @Value("${app.payos.return-url:http://localhost:8081/api/v1/payment/payos/return}")
    private String returnUrl;
    @Value("${app.payos.cancel-url:http://localhost:8081/api/v1/payment/payos/cancel}")
    private String cancelUrl;
    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Override
    @Transactional
    public PayosCreateResponse createPayment(User user, long amount, String description) {
        if (amount <= 0) throw new BadRequestException("Số tiền phải lớn hơn 0");
        requireConfig();

        long orderCode = generateOrderCode();
        String desc = normalizeDescription(description);

        PayosTransaction txn = PayosTransaction.builder()
                .userId(user.getId())
                .orderCode(orderCode)
                .amount(amount)
                .description(desc)
                .status(PayosStatus.PENDING)
                .build();
        txnRepository.save(txn);

        String signatureData = "amount=" + amount
                + "&cancelUrl=" + cancelUrl
                + "&description=" + desc
                + "&orderCode=" + orderCode
                + "&returnUrl=" + returnUrl;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderCode", orderCode);
        body.put("amount", amount);
        body.put("description", desc);
        body.put("cancelUrl", cancelUrl);
        body.put("returnUrl", returnUrl);
        body.put("signature", hmacSHA256(checksumKey, signatureData));

        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, authHeaders(true));
            String response = restTemplate.postForEntity(
                    baseUrl + "/v2/payment-requests", entity, String.class).getBody();
            JsonNode root = objectMapper.readTree(response);
            if (!"00".equals(root.path("code").asText())) {
                throw new BadRequestException("payOS tạo link thất bại: " + root.path("desc").asText("unknown"));
            }
            JsonNode data = root.path("data");
            String checkoutUrl = data.path("checkoutUrl").asText("");
            if (checkoutUrl.isBlank()) throw new BadRequestException("payOS không trả checkoutUrl");

            txn.setPaymentLinkId(data.path("paymentLinkId").asText(""));
            txnRepository.save(txn);

            return PayosCreateResponse.builder()
                    .checkoutUrl(checkoutUrl)
                    .orderCode(orderCode)
                    .qrCode(data.path("qrCode").asText(""))
                    .build();
        } catch (BadRequestException e) {
            txn.setStatus(PayosStatus.FAILED);
            txnRepository.save(txn);
            throw e;
        } catch (Exception e) {
            txn.setStatus(PayosStatus.FAILED);
            txnRepository.save(txn);
            throw new BadRequestException("Không thể kết nối payOS: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public Map<String, Object> handleWebhook(PayosWebhookRequest request) {
        try {
            if (request == null || request.getData() == null || isBlank(request.getSignature())) {
                return webhookResult("01", "invalid payload");
            }
            if (!isBlank(request.getCode()) && !"00".equals(request.getCode())) {
                return webhookResult("00", "ignored non-success webhook");
            }
            String expected = hmacSHA256(checksumKey, signable(request.getData()));
            if (!constantEquals(expected, request.getSignature())) {
                return webhookResult("01", "invalid signature");
            }

            JsonNode data = request.getData();
            long orderCode = data.path("orderCode").asLong(0L);
            long amount = data.path("amount").asLong(0L);
            boolean paid = "00".equals(data.path("code").asText(""))
                    && (request.getSuccess() == null || request.getSuccess());

            PayosTransaction txn = txnRepository.findByOrderCodeForUpdate(orderCode).orElse(null);
            if (txn == null) return webhookResult("01", "order not found");
            if (amount != txn.getAmount()) return webhookResult("01", "invalid amount");

            if (paid) {
                confirmSuccess(txn,
                        data.path("paymentLinkId").asText(""),
                        data.path("reference").asText(""));
            } else if (txn.getStatus() == PayosStatus.PENDING) {
                txn.setStatus(PayosStatus.FAILED);
                txnRepository.save(txn);
            }
            // payOS chỉ cần 2XX để biết đã nhận webhook.
            return webhookResult("00", "success");
        } catch (Exception e) {
            // 2XX vẫn trả để payOS không loop retry; lỗi được log ở tầng exception handler.
            return webhookResult("99", String.valueOf(e.getMessage()));
        }
    }

    @Override
    @Transactional
    public String handleReturn(Map<String, String> params) {
        String orderCodeRaw = params.get("orderCode");
        String status = params.get("status");

        if ("true".equalsIgnoreCase(params.get("cancel")) || "CANCELLED".equalsIgnoreCase(status)) {
            markFailed(orderCodeRaw);
            return resultPage("failed", orderCodeRaw);
        }

        long orderCode;
        try {
            orderCode = Long.parseLong(orderCodeRaw);
        } catch (Exception e) {
            return resultPage("error", orderCodeRaw);
        }

        PayosTransaction txn = txnRepository.findByOrderCode(orderCode).orElse(null);
        if (txn == null) return resultPage("error", orderCodeRaw);
        if (txn.getStatus() == PayosStatus.SUCCESS) return resultPage("success", orderCodeRaw);

        if ("PAID".equalsIgnoreCase(status)) {
            if (queryPaid(orderCode)) {
                confirmSuccess(txn, "", "");
                return resultPage("success", orderCodeRaw);
            }
            // Query chưa thấy PAID (payOS có độ trễ): để webhook xác nhận, không báo failed oan.
            return resultPage("pending", orderCodeRaw);
        }
        if ("PENDING".equalsIgnoreCase(status) || "PROCESSING".equalsIgnoreCase(status)) {
            return resultPage("pending", orderCodeRaw);
        }

        if (txn.getStatus() == PayosStatus.PENDING) {
            txn.setStatus(PayosStatus.FAILED);
            txnRepository.save(txn);
        }
        return resultPage("failed", orderCodeRaw);
    }

    /** Đánh dấu thành công + cộng ví. Gọi lại nhiều lần vẫn chỉ cộng 1 lần. */
    private void confirmSuccess(PayosTransaction txn, String paymentLinkId, String reference) {
        if (txn.getStatus() == PayosStatus.SUCCESS) return;
        txn.setStatus(PayosStatus.SUCCESS);
        if (!isBlank(paymentLinkId)) txn.setPaymentLinkId(paymentLinkId);
        if (!isBlank(reference)) txn.setReference(reference);
        txnRepository.save(txn);

        User user = userRepository.findById(txn.getUserId()).orElse(null);
        if (user != null) {
            walletService.creditAvailable(user, txn.getAmount(), "PAYOS_TOPUP", "PAYOS",
                    txn.getId(), String.valueOf(txn.getOrderCode()),
                    "Nap tien qua payOS " + txn.getOrderCode());
        }
    }

    private boolean queryPaid(long orderCode) {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(authHeaders(false));
            String body = restTemplate.exchange(
                    baseUrl + "/v2/payment-requests/" + orderCode,
                    HttpMethod.GET, entity, String.class).getBody();
            JsonNode root = objectMapper.readTree(body);
            if (!"00".equals(root.path("code").asText())) return false;
            JsonNode data = root.path("data");
            String signature = root.path("signature").asText(null);
            if (!isBlank(signature)
                    && !constantEquals(hmacSHA256(checksumKey, signable(data)), signature)) {
                return false;
            }
            return "PAID".equalsIgnoreCase(data.path("status").asText(""));
        } catch (Exception e) {
            return false;
        }
    }

    private void markFailed(String orderCodeRaw) {
        if (isBlank(orderCodeRaw)) return;
        try {
            txnRepository.findByOrderCode(Long.parseLong(orderCodeRaw)).ifPresent(txn -> {
                if (txn.getStatus() == PayosStatus.PENDING) {
                    txn.setStatus(PayosStatus.FAILED);
                    txnRepository.save(txn);
                }
            });
        } catch (NumberFormatException ignored) {
            // bỏ qua orderCode lạ
        }
    }

    private HttpHeaders authHeaders(boolean json) {
        HttpHeaders headers = new HttpHeaders();
        if (json) headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-client-id", clientId);
        headers.set("x-api-key", apiKey);
        return headers;
    }

    private Map<String, Object> webhookResult(String code, String desc) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("desc", desc);
        result.put("success", "00".equals(code));
        return result;
    }

    private String resultPage(String status, String orderCode) {
        return frontendUrl + "/payment/payos/result?status=" + safe(status)
                + "&orderCode=" + safe(orderCode);
    }

    private void requireConfig() {
        if (isBlank(clientId) || isBlank(apiKey) || isBlank(checksumKey)) {
            throw new BadRequestException("PayOS chưa được cấu hình (client-id/api-key/checksum-key)");
        }
    }

    private long generateOrderCode() {
        // 13 chữ số milliseconds + 3 chữ số ngẫu nhiên => < 10^16, nằm trong Long.
        return System.currentTimeMillis() * 1000L + ThreadLocalRandom.current().nextInt(1000);
    }

    /** payOS giới hạn description 9 ký tự với một số tài khoản ngân hàng. */
    private String normalizeDescription(String raw) {
        if (isBlank(raw)) return "NAPTIEN";
        String s = raw.trim().toUpperCase().replaceAll("[^A-Z0-9 ]", "").trim();
        if (s.isBlank()) return "NAPTIEN";
        return s.length() > 9 ? s.substring(0, 9) : s;
    }

    /** Sắp xếp key alphabetically, value null => "". */
    private String signable(JsonNode node) {
        Object sorted = toSortedValue(node);
        @SuppressWarnings("unchecked")
        TreeMap<String, Object> map = (TreeMap<String, Object>) sorted;
        return map.entrySet().stream()
                .map(e -> e.getKey() + "=" + stringify(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private Object toSortedValue(JsonNode node) {
        if (node.isObject()) {
            TreeMap<String, Object> map = new TreeMap<>();
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                map.put(e.getKey(), toSortedValue(e.getValue()));
            }
            return map;
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.forEach(child -> list.add(toSortedValue(child)));
            return list;
        }
        if (node.isNull()) return "";
        return node.asText();
    }

    private String stringify(Object value) {
        if (value == null) return "";
        if (value instanceof String s) return s;
        if (value instanceof Map || value instanceof List) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception ignored) {
                return String.valueOf(value);
            }
        }
        return String.valueOf(value);
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

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
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
