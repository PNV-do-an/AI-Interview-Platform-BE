package com.aiinterview.platform.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MomoCreateResponse {
    /** Trang thanh toán MoMo; frontend redirect người dùng tới đây. */
    private String paymentUrl;
    /** URL ảnh QR do MoMo trả về (có thể dùng trực tiếp làm <img src>). */
    private String qrCodeUrl;
    /** Deeplink mở app MoMo trên điện thoại. */
    private String deeplink;
    private String orderId;
    private String requestId;
}
