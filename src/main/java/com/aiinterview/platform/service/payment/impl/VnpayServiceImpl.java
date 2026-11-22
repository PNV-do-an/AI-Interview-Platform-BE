package com.aiinterview.platform.service.payment.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.model.dto.response.VnpayCreateResponse;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.entity.VnpayTransaction;
import com.aiinterview.platform.model.entity.WalletTransaction;
import com.aiinterview.platform.model.enums.VnpayStatus;
import com.aiinterview.platform.model.enums.WalletTxType;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.model.repository.VnpayTransactionRepository;
import com.aiinterview.platform.model.repository.WalletTransactionRepository;
import com.aiinterview.platform.service.payment.VnpayService;
import com.aiinterview.platform.service.payment.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VnpayServiceImpl implements VnpayService {

    private final VnpayTransactionRepository txnRepository;
    private final WalletService walletService;
    private final UserRepository userRepository;
    private final WalletTransactionRepository walletTxRepository;

    @Value("${app.vnpay.tmn-code:}")
    private String tmnCode;
    @Value("${app.vnpay.hash-secret:}")
    private String hashSecret;
    @Value("${app.vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String payUrl;
    @Value("${app.vnpay.return-url:http://localhost:8080/api/v1/payment/vnpay/return}")
    private String returnUrl;
    @Value("${app.vnpay.version:2.1.0}")
    private String version;
    @Value("${app.vnpay.command:pay}")
    private String command;
    @Value("${app.vnpay.order-type:other}")
    private String orderType;
    @Value("${app.vnpay.locale:vn}")
    private String locale;
    @Value("${app.vnpay.currency:VND}")
    private String currency;
    @Value("${app.vnpay.expire-minutes:15}")
    private int expireMinutes;
    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Override
    @Transactional
    public VnpayCreateResponse createPaymentUrl(User user, long amount, String orderInfo) {
        if (amount <= 0) throw new BadRequestException("Số tiền phải lớn hơn 0");
        if (tmnCode == null || tmnCode.isBlank() || hashSecret == null || hashSecret.isBlank()) {
            throw new BadRequestException("VNPay chưa được cấu hình (tmn-code/hash-secret)");
        }
        String info = (orderInfo == null || orderInfo.isBlank()) ? "Nap tien vi" : orderInfo.trim();

        String txnRef = System.currentTimeMillis() + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        VnpayTransaction txn = VnpayTransaction.builder()
                .userId(user.getId())
                .txnRef(txnRef)
                .amount(amount)
                .orderInfo(info)
                .status(VnpayStatus.PENDING)
                .build();
        txnRepository.save(txn);

        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        ZonedDateTime now = ZonedDateTime.now(zone);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String createDate = now.format(fmt);
        String expireDate = now.plusMinutes(expireMinutes).format(fmt);

        TreeMap<String, String> params = new TreeMap<>();
        params.put("vnp_Version", version);
        params.put("vnp_Command", command);
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_Amount", String.valueOf(amount * 100));
        params.put("vnp_CurrCode", currency);
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", info);
        params.put("vnp_OrderType", orderType);
        params.put("vnp_Locale", locale);
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", "127.0.0.1");
        params.put("vnp_CreateDate", createDate);
        params.put("vnp_ExpireDate", expireDate);

        String query = buildQuery(params);
        String hashData = buildHashData(params);
        String secureHash = hmacSHA512(hashSecret, hashData);
        String paymentUrl = payUrl + "?" + query + "&vnp_SecureHash=" + secureHash;

        return VnpayCreateResponse.builder().paymentUrl(paymentUrl).txnRef(txnRef).build();
    }

    @Override
    @Transactional
    public String handleReturn(Map<String, String> params) {
        String txnRef = params.get("vnp_TxnRef");
        if (!verifySignature(params)) {
            return frontendUrl + "/payment/vnpay/result?status=invalid-signature&txnRef=" + safe(txnRef);
        }
        VnpayTransaction txn = txnRepository.findByTxnRef(txnRef).orElse(null);
        if (txn == null) {
            return frontendUrl + "/payment/vnpay/result?status=error&txnRef=" + safe(txnRef);
        }
        String responseCode = params.get("vnp_ResponseCode");
        long paidAmount = parseAmount(params.get("vnp_Amount")) / 100;

        if ("00".equals(responseCode)) {
            if (txn.getStatus() != VnpayStatus.SUCCESS) {
                txn.setStatus(VnpayStatus.SUCCESS);
                txn.setResponseCode(responseCode);
                txn.setVnpTransactionNo(params.get("vnp_TransactionNo"));
                txn.setBankCode(params.get("vnp_BankCode"));
                txn.setPayDate(params.get("vnp_PayDate"));
                txnRepository.save(txn);

                // idempotent credit
                if (paidAmount == txn.getAmount()) {
                    User user = userRepository.findById(txn.getUserId()).orElse(null);
                    if (user != null) {
                        walletService.creditAvailable(user, txn.getAmount(), "VNPAY_TOPUP", "VNPAY",
                                txn.getId(), txn.getTxnRef(), "Nap tien qua VNPay " + txn.getTxnRef());
                    }
                }
            }
            return frontendUrl + "/payment/vnpay/result?status=success&txnRef=" + safe(txnRef) + "&responseCode=" + safe(responseCode);
        } else {
            if (txn.getStatus() == VnpayStatus.PENDING) {
                txn.setStatus(VnpayStatus.FAILED);
                txn.setResponseCode(responseCode);
                txnRepository.save(txn);
            }
            return frontendUrl + "/payment/vnpay/result?status=failed&txnRef=" + safe(txnRef) + "&responseCode=" + safe(responseCode);
        }
    }

    @Override
    @Transactional
    public Map<String, String> handleIpn(Map<String, String> params) {
        Map<String, String> result = new TreeMap<>();
        if (!verifySignature(params)) {
            result.put("RspCode", "97");
            result.put("Message", "Invalid signature");
            return result;
        }
        String txnRef = params.get("vnp_TxnRef");
        VnpayTransaction txn = txnRepository.findByTxnRef(txnRef).orElse(null);
        if (txn == null) {
            result.put("RspCode", "01");
            result.put("Message", "Order not found");
            return result;
        }
        long paidAmount = parseAmount(params.get("vnp_Amount")) / 100;
        if (paidAmount != txn.getAmount()) {
            result.put("RspCode", "04");
            result.put("Message", "Invalid amount");
            return result;
        }
        String responseCode = params.get("vnp_ResponseCode");

        if (txn.getStatus() == VnpayStatus.SUCCESS) {
            // already confirmed
            result.put("RspCode", "00");
            result.put("Message", "Confirm Success");
            return result;
        }

        if ("00".equals(responseCode)) {
            txn.setStatus(VnpayStatus.SUCCESS);
            txn.setResponseCode(responseCode);
            txn.setVnpTransactionNo(params.get("vnp_TransactionNo"));
            txn.setBankCode(params.get("vnp_BankCode"));
            txn.setPayDate(params.get("vnp_PayDate"));
            txnRepository.save(txn);

            User user = userRepository.findById(txn.getUserId()).orElse(null);
            if (user != null) {
                walletService.creditAvailable(user, txn.getAmount(), "VNPAY_TOPUP", "VNPAY",
                        txn.getId(), txn.getTxnRef(), "Nap tien qua VNPay " + txn.getTxnRef());
            }
        } else {
            txn.setStatus(VnpayStatus.FAILED);
            txn.setResponseCode(responseCode);
            txnRepository.save(txn);
        }

        result.put("RspCode", "00");
        result.put("Message", "Confirm Success");
        return result;
    }

    private boolean verifySignature(Map<String, String> params) {
        String provided = params.get("vnp_SecureHash");
        if (provided == null || provided.isBlank()) return false;
        if (hashSecret == null || hashSecret.isBlank()) return false;

        TreeMap<String, String> filtered = new TreeMap<>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            String k = e.getKey();
            if (k != null && k.startsWith("vnp_") && !k.equals("vnp_SecureHash") && !k.equals("vnp_SecureHashType")) {
                filtered.put(k, e.getValue() == null ? "" : e.getValue());
            }
        }
        String hashData = buildHashData(filtered);
        String computed = hmacSHA512(hashSecret, hashData);
        return MessageDigest.isEqual(
                computed.toLowerCase().getBytes(StandardCharsets.UTF_8),
                provided.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    private String buildQuery(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (i > 0) sb.append("&");
            sb.append(e.getKey()).append("=").append(urlEncode(e.getValue()));
            i++;
        }
        return sb.toString();
    }

    private String buildHashData(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (i > 0) sb.append("&");
            sb.append(e.getKey()).append("=").append(urlEncode(e.getValue()));
            i++;
        }
        return sb.toString();
    }

    private String urlEncode(String s) {
        if (s == null) return "";
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return s;
        }
    }

    private String hmacSHA512(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("HMAC SHA512 error", e);
        }
    }

    private long parseAmount(String v) {
        if (v == null || v.isBlank()) return 0L;
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private String safe(String s) {
        if (s == null) return "";
        return urlEncode(s);
    }
}