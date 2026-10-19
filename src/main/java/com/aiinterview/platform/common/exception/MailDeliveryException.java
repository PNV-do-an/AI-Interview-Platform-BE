package com.aiinterview.platform.common.exception;

/**
 * Email bắt buộc (xác thực tài khoản, đặt lại mật khẩu) không gửi đi được.
 * Controller trả 502: upstream Resend từ chối hoặc không phản hồi.
 */
public class MailDeliveryException extends RuntimeException {
    public MailDeliveryException(String message) {
        super(message);
    }

    public MailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
