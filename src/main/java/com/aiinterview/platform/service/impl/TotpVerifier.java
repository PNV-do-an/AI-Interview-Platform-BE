package com.aiinterview.platform.service.impl;

import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import org.springframework.stereotype.Component;

/**
 * RFC 6238 TOTP (SHA1, 30s, 6 số) — cửa sổ ±1 step chịu lệch đồng hồ giữa điện thoại và server.
 */
@Component
public class TotpVerifier {

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator(40);
    private final TimeProvider timeProvider = new SystemTimeProvider();

    public String newSecret() {
        return secretGenerator.generate();
    }

    public boolean verify(String secret, String code) {
        return verifier().isValidCode(secret, code);
    }

    /** Mã hiện tại của secret — test tích hợp cần giá trị này để vượt bước verify mà không mock. */
    public String currentCode(String secret) {
        try {
            return new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6)
                    .generate(secret, timeProvider.getTime() / 30L);
        } catch (dev.samstevens.totp.exceptions.CodeGenerationException e) {
            throw new IllegalStateException("Không sinh được OTP", e);
        }
    }

    private DefaultCodeVerifier verifier() {
        CodeGenerator generator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6);
        DefaultCodeVerifier verifier = new DefaultCodeVerifier(generator, timeProvider);
        verifier.setAllowedTimePeriodDiscrepancy(1);
        return verifier;
    }
}
