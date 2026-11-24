package com.aiinterview.platform.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Trạng thái giao dịch payOS do backend xác thực.
 * FE dùng endpoint này thay vì tin tưởng query param trên URL redirect.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayosStatusResponse {
    private Long orderCode;
    /** PENDING | SUCCESS | FAILED */
    private String status;
    private Long amount;
    private String paymentLinkId;
}
