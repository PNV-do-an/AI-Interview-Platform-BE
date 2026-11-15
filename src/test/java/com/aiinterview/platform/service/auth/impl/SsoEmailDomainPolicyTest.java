package com.aiinterview.platform.service.auth.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Domain allowlist là chốt kiểm soát duy nhất ngăn user ngoài công ty dùng SSO vào hệ thống. */
class SsoEmailDomainPolicyTest {

    @Test
    void emptyAllowlistAcceptsAnyVerifiedEmail() {
        assertTrue(SsoAuthInterfaceImpl.isEmailDomainAllowed("hr@acme.com", ""));
        assertTrue(SsoAuthInterfaceImpl.isEmailDomainAllowed("hr@anyvendor.io", null));
    }

    @Test
    void acceptsOnlyListedDomains() {
        String allowed = "acme.com, huy.io ";
        assertTrue(SsoAuthInterfaceImpl.isEmailDomainAllowed("hr@acme.com", allowed));
        assertTrue(SsoAuthInterfaceImpl.isEmailDomainAllowed("CFO@HUY.IO", allowed));
    }

    @Test
    void rejectsOtherDomains() {
        String allowed = "acme.com";
        // domain giả dạng "acme.com.evil.io" không được lọt
        assertFalse(SsoAuthInterfaceImpl.isEmailDomainAllowed("hr@acme.com.evil.io", allowed));
        assertFalse(SsoAuthInterfaceImpl.isEmailDomainAllowed("hr@gmail.com", allowed));
        assertFalse(SsoAuthInterfaceImpl.isEmailDomainAllowed("not-an-email", allowed));
        assertFalse(SsoAuthInterfaceImpl.isEmailDomainAllowed("hr@sub.acme.com", allowed));
    }
}
