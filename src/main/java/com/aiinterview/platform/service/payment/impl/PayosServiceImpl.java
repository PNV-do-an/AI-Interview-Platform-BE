package com.aiinterview.platform.service.payment.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.exception.ForbiddenException;
import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.model.dto.request.PayosWebhookRequest;
import com.aiinterview.platform.model.dto.response.PayosCreateResponse;
import com.aiinterview.platform.model.dto.response.PayosStatusResponse;
import com.aiinterview.platform.model.entity.PayosTransaction;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.PayosStatus;
import com.aiinterview.platform.model.repository.PayosTransactionRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.service.payment.PayosService;
import com.aiinterview.platform.service.payment.WalletService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * payOS payment-request v2: create link -> redirect -> webhook/query -> cộng ví idempotent.
 * payOS hiện không có sandbox riêng; credential được đưa qua app.payos.* / env.
 */
@Slf4j
@Service
public class PayosServiceImpl implements PayosService {

    private final PayosTransactionRepository txnRepository;
    private final WalletService walletService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final TransactionTemplate transactionTemplate;

    public PayosServiceImpl(PayosTransactionRepository txnRepository,
                            WalletService walletService,
                            UserRepository userRepository,
                            ObjectMapper objectMapper,
                            RestTemplateBuilder restTemplateBuilder,
                            PlatformTransactionManager transactionManager) {
        this.txnRepository = txnRepository;
        this.walletService = walletService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(15))
                .build();
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Value("${app.payos.client-id:}")
    private String clientId;
    @Value("${app.payos.api-key:}")
    private String apiKey;
    @Value("${app.payos.checksum-key:}")
    private String checksumKey;
    @Value("${app.payos.base-url:https://api-merchant.payos.vn}")
    private String baseUrl;
    @Value("${app.payos.return-url:}")
    private String returnUrl;
    @Value("${app.payos.cancel-url:}")
    private String cancelUrl;
    /**
     * URL public của backend, dùng để tự dựng return/cancel URL khi biến PAYOS_* bị thiếu
     * hoặc còn sót giá trị localhost trên môi trường deploy.
     */
    @Value("${app.public-base-url:}")
    private String publicBaseUrl;
    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${app.payos.reconcile-enabled:true}")
    private boolean reconcileEnabled;
    @Value("${app.payos.reconcile-lookback-hours:24}")
    private long reconcileLookbackHours;
    @Value("${app.payos.reconcile-batch-size:50}")
    private int reconcileBatchSize;

    @Override
    // Không dùng @Transactional ở đây: nếu gọi payOS lỗi sau khi đã lưu, transaction sẽ
    // rollback và mất bản ghi -> /return không tìm thấy đơn dù payOS đã tạo lệnh thanh
    // toán. Mỗi lần save tự commit để webhook/scheduler vẫn có row để đối soát.
    public PayosCreateResponse createPayment(User user, long amount, String description) {
        if (amount <= 0) throw new BadRequestException("Số tiền phải lớn hơn 0");
        requireConfig();

        long orderCode = generateOrderCode();
        String desc = normalizeDescription(description);
        String effectiveReturnUrl = resolvePublicUrl(returnUrl, "/api/v1/payment/payos/return");
        String effectiveCancelUrl = resolvePublicUrl(cancelUrl, "/api/v1/payment/payos/cancel");

        PayosTransaction txn = PayosTransaction.builder()
                .userId(user.getId())
                .orderCode(orderCode)
                .amount(amount)
                .description(desc)
                .status(PayosStatus.PENDING)
                .build();
        txnRepository.save(txn);

        String signatureData = "amount=" + amount
                + "&cancelUrl=" + effectiveCancelUrl
                + "&description=" + desc
                + "&orderCode=" + orderCode
                + "&returnUrl=" + effectiveReturnUrl;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderCode", orderCode);
        body.put("amount", amount);
        body.put("description", desc);
        body.put("cancelUrl", effectiveCancelUrl);
        body.put("returnUrl", effectiveReturnUrl);
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

            log.info("payOS create OK orderCode={} amount={} returnUrl={} cancelUrl={}",
                    orderCode, amount, effectiveReturnUrl, effectiveCancelUrl);

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
                log.warn("payOS webhook ignored: missing payload/signature");
                return webhookResult("01", "invalid payload");
            }
            if (!isBlank(request.getCode()) && !"00".equals(request.getCode())) {
                return webhookResult("00", "ignored non-success webhook");
            }
            String expected = hmacSHA256(checksumKey, signable(request.getData()));
            if (!constantEquals(expected, request.getSignature())) {
                log.warn("payOS webhook rejected: invalid signature");
                return webhookResult("01", "invalid signature");
            }

            JsonNode data = request.getData();
            long orderCode = data.path("orderCode").asLong(0L);
            long amount = data.path("amount").asLong(0L);
            boolean paid = "00".equals(data.path("code").asText(""))
                    && (request.getSuccess() == null || request.getSuccess());

            PayosTransaction txn = txnRepository.findByOrderCodeForUpdate(orderCode).orElse(null);
            if (txn == null) {
                log.warn("payOS webhook orderCode={} not found", orderCode);
                return webhookResult("01", "order not found");
            }
            if (amount != txn.getAmount()) {
                log.warn("payOS webhook orderCode={} amount mismatch: provider={} txn={}",
                        orderCode, amount, txn.getAmount());
                return webhookResult("01", "invalid amount");
            }

            if (paid) {
                confirmSuccess(txn,
                        data.path("paymentLinkId").asText(""),
                        data.path("reference").asText(""));
                log.info("payOS webhook credited orderCode={} amount={}", orderCode, amount);
            } else if (txn.getStatus() == PayosStatus.PENDING) {
                txn.setStatus(PayosStatus.FAILED);
                txnRepository.save(txn);
                log.info("payOS webhook marked FAILED orderCode={} dataCode={} success={}",
                        orderCode, data.path("code").asText(""), request.getSuccess());
            }
            // payOS chỉ cần 2XX để biết đã nhận webhook.
            return webhookResult("00", "success");
        } catch (Exception e) {
            // 2XX vẫn trả để payOS không loop retry; lỗi được log ở tầng exception handler.
            log.error("payOS webhook processing error", e);
            return webhookResult("99", String.valueOf(e.getMessage()));
        }
    }

    @Override
    public String handleReturn(Map<String, String> params) {
        String orderCodeRaw = params == null ? null : params.get("orderCode");
        try {
            return handleReturnInternal(params == null ? Map.of() : params);
        } catch (Exception e) {
            // Return URL không bao giờ được trả 5xx cho payOS/FE. Lỗi được log lại,
            // giao dịch vẫn để PENDING để webhook/scheduler tiếp tục đối soát.
            log.error("payOS return failed orderCode={} params={}", orderCodeRaw, params, e);
            return resultPage("error", orderCodeRaw);
        }
    }

    private String handleReturnInternal(Map<String, String> params) {
        String orderCodeRaw = params.get("orderCode");
        String status = params.get("status");

        if ("true".equalsIgnoreCase(params.get("cancel")) || "CANCELLED".equalsIgnoreCase(status)) {
            markFailedByRawCode(orderCodeRaw);
            return resultPage("failed", orderCodeRaw);
        }

        long orderCode;
        try {
            orderCode = Long.parseLong(orderCodeRaw);
        } catch (Exception e) {
            return resultPage("error", orderCodeRaw);
        }

        PayosTransaction txn = txnRepository.findByOrderCode(orderCode).orElse(null);
        if (txn == null) {
            // Bản ghi local có thể đã bị rollback lúc tạo đơn dù payOS đã nhận lệnh thanh
            // toán. Thử khôi phục từ chính payOS (đã verify chữ ký) thay vì trả lỗi cứng.
            log.warn("payOS return orderCode={} not found locally, attempting recovery", orderCode);
            if (recoverMissingTransaction(orderCode)) return resultPage("success", orderCodeRaw);
            // Chưa xác định được người nhận tiền: để FE tiếp tục đối soát, không chặn ở lỗi.
            return resultPage("pending", orderCodeRaw);
        }
        if (txn.getStatus() == PayosStatus.SUCCESS) return resultPage("success", orderCodeRaw);

        // Luôn đối soát trực tiếp với payOS thay vì tin query param (có thể thiếu/đổi tên).
        PaymentQueryResult result = queryPayment(orderCode);
        if (result != null && result.status() == PayosStatus.SUCCESS) {
            confirmSuccessLocked(orderCode, result);
            return resultPage("success", orderCodeRaw);
        }
        if (result != null && result.status() == PayosStatus.FAILED) {
            markFailedLocked(orderCode);
            return resultPage("failed", orderCodeRaw);
        }
        if ("FAILED".equalsIgnoreCase(status) || "EXPIRED".equalsIgnoreCase(status)) {
            markFailedLocked(orderCode);
            return resultPage("failed", orderCodeRaw);
        }
        // Query chưa thấy PAID (payOS có độ trễ): để webhook/scheduler xác nhận.
        return resultPage("pending", orderCodeRaw);
    }

    @Override
    public PayosStatusResponse getPaymentStatus(User user, long orderCode) {
        PayosTransaction txn = txnRepository.findByOrderCode(orderCode).orElse(null);
        if (txn == null) {
            recoverMissingTransaction(orderCode);
            txn = txnRepository.findByOrderCode(orderCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch payOS"));
        }
        if (!txn.getUserId().equals(user.getId())) {
            throw new ForbiddenException("Bạn không có quyền xem giao dịch này");
        }

        if (txn.getStatus() == PayosStatus.PENDING) {
            PaymentQueryResult result = queryPayment(orderCode);
            if (result != null) {
                if (result.status() == PayosStatus.SUCCESS) {
                    confirmSuccessLocked(orderCode, result);
                } else if (result.status() == PayosStatus.FAILED) {
                    markFailedLocked(orderCode);
                }
            }
        }

        PayosTransaction latest = txnRepository.findByOrderCode(orderCode).orElse(txn);
        return PayosStatusResponse.builder()
                .orderCode(latest.getOrderCode())
                .status(latest.getStatus().name())
                .amount(latest.getAmount())
                .paymentLinkId(latest.getPaymentLinkId())
                .build();
    }

    /**
     * Khôi phục giao dịch khi bản ghi local bị mất (thường do createPayment bị rollback
     * sau khi payOS đã tạo đơn). Chỉ tin dữ liệu payOS đã verify chữ ký. userId được
     * lấy từ description do chính backend sinh ra lúc tạo đơn, nên kẻ tấn công không
     * thể tự gán ví khác. Idempotency key PAYOS-&lt;orderCode&gt; chặn cộng tiền trùng.
     */
    private boolean recoverMissingTransaction(long orderCode) {
        if (isBlank(clientId) || isBlank(apiKey) || isBlank(checksumKey)) return false;
        PaymentQueryResult result = queryPayment(orderCode);
        if (result == null || result.status() != PayosStatus.SUCCESS) return false;

        Long userId = extractUserId(result.description());
        if (userId == null || userRepository.findById(userId).isEmpty()) {
            log.warn("payOS recover orderCode={} cannot resolve user from description='{}'",
                    orderCode, result.description());
            return false;
        }

        transactionTemplate.executeWithoutResult(ignored -> {
            PayosTransaction existing = txnRepository.findByOrderCode(orderCode).orElse(null);
            if (existing != null) {
                if (existing.getStatus() == PayosStatus.PENDING) {
                    confirmSuccess(existing, result.paymentLinkId(), result.reference());
                }
                return;
            }
            PayosTransaction restored = PayosTransaction.builder()
                    .userId(userId)
                    .orderCode(orderCode)
                    .amount(result.amount())
                    .description(result.description())
                    .status(PayosStatus.PENDING)
                    .paymentLinkId(result.paymentLinkId())
                    .reference(result.reference())
                    .build();
            txnRepository.save(restored);
            confirmSuccess(restored, result.paymentLinkId(), result.reference());
            log.info("payOS recovered missing orderCode={} userId={} amount={}",
                    orderCode, userId, result.amount());
        });
        return true;
    }

    /** description lúc tạo đơn: NAPTIEN&lt;id&gt; (legacy, tối đa 2 chữ số) hoặc NAP&lt;id 6 chữ số&gt;. */
    private Long extractUserId(String description) {
        if (isBlank(description)) return null;
        Matcher m = Pattern.compile("^(?:NAPTIEN|NAP)(\\d{1,6})$")
                .matcher(description.trim().toUpperCase(Locale.ROOT));
        if (!m.matches()) return null;
        try {
            long id = Long.parseLong(m.group(1));
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
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
                    txn.getId(), "PAYOS-" + txn.getOrderCode(),
                    "Nap tien qua payOS " + txn.getOrderCode());
        }
    }

    /** Khoá row rồi xác nhận, tránh webhook và return/status cộng tiền hai lần. */
    private void confirmSuccessLocked(long orderCode, PaymentQueryResult result) {
        transactionTemplate.executeWithoutResult(ignored -> {
            PayosTransaction txn = txnRepository.findByOrderCodeForUpdate(orderCode).orElse(null);
            if (txn == null) return;
            if (result.amount() != txn.getAmount()) {
                log.warn("payOS query orderCode={} amount mismatch: provider={} txn={}",
                        orderCode, result.amount(), txn.getAmount());
                return;
            }
            confirmSuccess(txn, result.paymentLinkId(), result.reference());
        });
    }

    private void markFailedLocked(long orderCode) {
        transactionTemplate.executeWithoutResult(ignored ->
                txnRepository.findByOrderCodeForUpdate(orderCode).ifPresent(txn -> {
                    if (txn.getStatus() == PayosStatus.PENDING) {
                        txn.setStatus(PayosStatus.FAILED);
                        txnRepository.save(txn);
                    }
                }));
    }

    private void markFailedByRawCode(String orderCodeRaw) {
        if (isBlank(orderCodeRaw)) return;
        try {
            markFailedLocked(Long.parseLong(orderCodeRaw));
        } catch (NumberFormatException ignored) {
            // bỏ qua orderCode lạ
        }
    }

    /** Fallback định kỳ: đối soát các giao dịch còn PENDING, phòng khi webhook/return bị lỡ. */
    @Scheduled(
            fixedDelayString = "${app.payos.reconcile-delay-ms:120000}",
            initialDelayString = "${app.payos.reconcile-initial-delay-ms:60000}")
    public void reconcilePendingPayments() {
        if (!reconcileEnabled) return;
        if (isBlank(clientId) || isBlank(apiKey) || isBlank(checksumKey)) return;

        LocalDateTime now = LocalDateTime.now();
        int batchSize = Math.max(1, Math.min(reconcileBatchSize, 100));
        List<PayosTransaction> pending;
        try {
            pending = txnRepository.findByStatusAndCreatedAtAfterOrderByCreatedAtAsc(
                    PayosStatus.PENDING,
                    now.minusHours(Math.max(1, reconcileLookbackHours)),
                    PageRequest.of(0, batchSize));
        } catch (Exception e) {
            log.warn("payOS reconcile query failed: {}", e.getMessage());
            return;
        }

        int credited = 0;
        for (PayosTransaction txn : pending) {
            // Bỏ qua giao dịch vừa tạo, payOS có thể chưa kịp cập nhật.
            if (txn.getCreatedAt() != null && txn.getCreatedAt().isAfter(now.minusSeconds(30))) continue;
            PaymentQueryResult result = queryPayment(txn.getOrderCode());
            if (result == null) continue;
            if (result.status() == PayosStatus.SUCCESS) {
                confirmSuccessLocked(txn.getOrderCode(), result);
                credited++;
            } else if (result.status() == PayosStatus.FAILED) {
                markFailedLocked(txn.getOrderCode());
            }
        }
        if (credited > 0) {
            log.info("payOS reconcile credited {} of {} pending transactions", credited, pending.size());
        }
    }

    private PaymentQueryResult queryPayment(long orderCode) {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(authHeaders(false));
            String body = restTemplate.exchange(
                    baseUrl + "/v2/payment-requests/" + orderCode,
                    HttpMethod.GET, entity, String.class).getBody();
            if (isBlank(body)) return null;
            JsonNode root = objectMapper.readTree(body);
            if (!"00".equals(root.path("code").asText())) {
                log.warn("payOS query orderCode={} failed: {} - {}",
                        orderCode, root.path("code").asText(), root.path("desc").asText());
                return null;
            }
            JsonNode data = root.path("data");
            if (!data.isObject()) {
                log.warn("payOS query orderCode={} missing data object", orderCode);
                return null;
            }

            String signature = root.path("signature").asText(null);
            if (isBlank(signature)
                    || !constantEquals(hmacSHA256(checksumKey, signable(data)), signature)) {
                log.warn("payOS query orderCode={} signature mismatch", orderCode);
                return null;
            }

            long returnedOrderCode = data.path("orderCode").asLong(0L);
            if (returnedOrderCode != 0L && returnedOrderCode != orderCode) {
                log.warn("payOS query returned orderCode={} for requested {}", returnedOrderCode, orderCode);
                return null;
            }
            long amount = data.path("amount").asLong(0L);
            if (amount <= 0) {
                log.warn("payOS query orderCode={} invalid amount={}", orderCode, amount);
                return null;
            }

            String rawStatus = data.path("status").asText("");
            PayosStatus status = switch (rawStatus.toUpperCase(Locale.ROOT)) {
                case "PAID", "SUCCESS", "SUCCEEDED", "COMPLETED" -> PayosStatus.SUCCESS;
                case "CANCELLED", "CANCELED", "EXPIRED", "FAILED" -> PayosStatus.FAILED;
                default -> PayosStatus.PENDING;
            };
            log.info("payOS query orderCode={} status={} amount={}", orderCode, rawStatus, amount);
            return new PaymentQueryResult(
                    status,
                    amount,
                    data.path("id").asText(""),
                    extractReference(data),
                    rawStatus,
                    data.path("description").asText(""));
        } catch (Exception e) {
            log.warn("payOS query orderCode={} error: {}", orderCode, e.getMessage());
            return null;
        }
    }

    private String extractReference(JsonNode data) {
        JsonNode transactions = data.path("transactions");
        if (transactions.isArray()) {
            for (JsonNode tx : transactions) {
                String ref = tx.path("reference").asText("");
                if (!isBlank(ref)) return ref;
            }
        } else if (transactions.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = transactions.fields();
            while (it.hasNext()) {
                String ref = it.next().getValue().path("reference").asText("");
                if (!isBlank(ref)) return ref;
            }
        }
        return data.path("reference").asText("");
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
        return stripTrailingSlash(frontendUrl) + "/payment/payos/result?status=" + safe(status)
                + "&orderCode=" + safe(orderCode);
    }

    /**
     * Trả URL public dùng cho payOS. Nếu cấu hình thiếu, trỏ về localhost, hoặc trỏ nhầm
     * vào FE thì tự dựng lại từ app.public-base-url (RENDER_EXTERNAL_URL trên Render).
     */
    private String resolvePublicUrl(String configured, String path) {
        String base = stripTrailingSlash(publicBaseUrl);
        boolean baseUsable = !isBlank(base) && !isLocalUrl(base);

        if (!isBlank(configured) && !isLocalUrl(configured) && !pointsToFrontend(configured)) {
            return configured;
        }
        if (baseUsable) {
            return base + path;
        }
        if (!isBlank(configured)) {
            return configured;
        }
        return "http://localhost:8081" + path;
    }

    private boolean pointsToFrontend(String url) {
        if (isBlank(url) || isBlank(frontendUrl)) return false;
        String fe = stripTrailingSlash(frontendUrl);
        String value = stripTrailingSlash(url);
        return value.equals(fe) || value.startsWith(fe + "/");
    }

    private boolean isLocalUrl(String url) {
        if (isBlank(url)) return false;
        String value = url.toLowerCase(Locale.ROOT);
        return value.contains("localhost") || value.contains("127.0.0.1") || value.contains("0.0.0.0");
    }

    private String stripTrailingSlash(String url) {
        if (url == null) return "";
        String value = url.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
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

    /**
     * Sắp xếp key alphabetically. Ở top-level mọi scalar thành text, null => "" theo payOS;
     * bên trong object/list (transactions[]) phải giữ null/number/bool để Jackson serialize đúng JSON.
     */
    private String signable(JsonNode node) {
        Object sorted = toSortedValue(node, false);
        @SuppressWarnings("unchecked")
        TreeMap<String, Object> map = (TreeMap<String, Object>) sorted;
        return map.entrySet().stream()
                .map(e -> e.getKey() + "=" + stringify(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private Object toSortedValue(JsonNode node, boolean nested) {
        if (node.isObject()) {
            TreeMap<String, Object> map = new TreeMap<>();
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                map.put(e.getKey(), toSortedValue(e.getValue(), true));
            }
            return map;
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.forEach(child -> list.add(toSortedValue(child, true)));
            return list;
        }
        if (node.isNull()) return nested ? null : "";
        // Bên trong JSON (transactions[]) phải giữ kiểu number/bool để Jackson serialize đúng.
        // Ở top-level payOS cần dạng key=value nên mọi scalar đều thành text.
        if (nested && node.isNumber()) return node.numberValue();
        if (nested && node.isBoolean()) return node.booleanValue();
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

    private record PaymentQueryResult(PayosStatus status, long amount, String paymentLinkId,
                                      String reference, String rawStatus, String description) {
    }
}
