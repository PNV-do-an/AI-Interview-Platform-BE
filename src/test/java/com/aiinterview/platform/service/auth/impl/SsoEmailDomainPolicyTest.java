package com.aiinterview.platform.service.auth.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Domain allowlist là chốt kiểm soát duy nhất ngăn user ngoài công ty dùng SSO vào hệ thống.
 *
 * <p>Mỗi test chia 3 khối comment: <b>Arrange</b> → <b>Act</b> → <b>Assert</b>.</p>
 */
class SsoEmailDomainPolicyTest {

    @Test
    void emptyAllowlistAcceptsAnyVerifiedEmail() {
        // Arrange: cau hinh rong ("") va null = khong gioi han domain
        // Act & Assert: ca hai deu phai cho qua, neu khong thi bat SSO se khoa luon nguoi dung
        assertTrue(SsoAuthInterfaceImpl.isEmailDomainAllowed("hr@acme.com", ""));
        assertTrue(SsoAuthInterfaceImpl.isEmailDomainAllowed("hr@anyvendor.io", null));
    }

    @Test
    void acceptsOnlyListedDomains() {
        // Arrange: danh sach co khoang trang thua ("acme.com, huy.io ")
        String allowed = "acme.com, huy.io ";

        // Act & Assert: phai trim tung phan va so sanh khong phan biet hoa/thuong
        assertTrue(SsoAuthInterfaceImpl.isEmailDomainAllowed("hr@acme.com", allowed));
        assertTrue(SsoAuthInterfaceImpl.isEmailDomainAllowed("CFO@HUY.IO", allowed));
    }

    @Test
    void rejectsOtherDomains() {
        // Arrange: chi cho phep acme.com
        String allowed = "acme.com";

        // Act & Assert: day la nhung cach lach allowlist phai bi chan
        // domain giả dạng "acme.com.evil.io" không được lọt
        assertFalse(SsoAuthInterfaceImpl.isEmailDomainAllowed("hr@acme.com.evil.io", allowed));
        assertFalse(SsoAuthInterfaceImpl.isEmailDomainAllowed("hr@gmail.com", allowed));
        assertFalse(SsoAuthInterfaceImpl.isEmailDomainAllowed("not-an-email", allowed));
        // subdomain khong duoc tu dong duoc chap nhan (phai liet ke ro)
        assertFalse(SsoAuthInterfaceImpl.isEmailDomainAllowed("hr@sub.acme.com", allowed));
    }
}
