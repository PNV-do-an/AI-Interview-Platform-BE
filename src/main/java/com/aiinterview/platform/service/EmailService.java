package com.aiinterview.platform.service;

public interface EmailService {

    void sendVerificationEmail(String to, String fullName, String token);

    void sendResendVerificationEmail(String to, String fullName, String token);

    void sendResetPasswordEmail(String to, String fullName, String token);

    void sendPasswordResetConfirmation(String to, String fullName);

    void sendAccountLockedEmail(String to, String fullName);

    void sendAccountUnlockedEmail(String to, String fullName);

    void sendAccountDeletedEmail(String to, String fullName);

    void sendApplicationReceivedEmail(String to, String candidateName, String jobTitle);

    void sendApplicationStatusEmail(String to, String candidateName, String jobTitle, String statusLabel);

    void sendSlotBookedEmail(String to, String name, String jobTitle, String startTime, String role);

    /** FR-SCH-03: nhắc trước giờ hẹn, nội dung khác email xác nhận vì lịch đã xác nhận từ lần đặt. */
    void sendInterviewReminderEmail(String to, String name, String jobTitle, String startTime, String role);

    void sendOrderConfirmationEmail(String to, String fullName, String packageName, Long amount, String orderCode);
}
