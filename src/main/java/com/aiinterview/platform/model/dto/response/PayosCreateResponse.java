package com.aiinterview.platform.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayosCreateResponse {
    /** URL trang checkout của payOS; frontend redirect người dùng tới đây. */
    private String checkoutUrl;
    private Long orderCode;
    /** Nội dung QR (optional, có thể render thành ảnh QR). */
    private String qrCode;
}
