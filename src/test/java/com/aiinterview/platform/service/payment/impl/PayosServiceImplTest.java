package com.aiinterview.platform.service.payment.impl;

import com.aiinterview.platform.model.repository.PayosTransactionRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.service.payment.WalletService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Regression test cho lỗi "thanh toán xong không cộng tiền / không về server chính":
 * return URL payOS phải là URL public của backend, không được rơi về localhost khi deploy.
 */
class PayosServiceImplTest {

    private static final String CHECKSUM_KEY =
            "1a54716c8f0efb2744fb28b6e38b25da7f67a925d98bc1c18bd8faaecadd7675";
    private static final String PAID_QUERY_DATA =
            "{\"id\":\"0c1f0b5b8a7c4a3fa3e5d1c2b4a69788\",\"orderCode\":1730000000123,"
                    + "\"amount\":10000,\"amountPaid\":10000,\"amountRemaining\":0,\"status\":\"PAID\","
                    + "\"createdAt\":\"2025-10-01T10:15:30+07:00\",\"transactions\":["
                    + "{\"reference\":\"FT25274123456\",\"amount\":10000,\"accountNumber\":\"12345678\","
                    + "\"description\":\"CSWM5R0Y Thanh toan don hang 123\","
                    + "\"transactionDateTime\":\"2025-10-01 10:16:02\",\"virtualAccountName\":null,"
                    + "\"virtualAccountNumber\":null,\"counterAccountBankId\":\"970422\","
                    + "\"counterAccountBankName\":\"MB Bank\",\"counterAccountName\":\"NGUYEN VAN A\","
                    + "\"counterAccountNumber\":\"0123456789\"}],"
                    + "\"cancellationReason\":null,\"canceledAt\":null}";
    private static final String WEBHOOK_SAMPLE_DATA =
            "{\"orderCode\":123,\"amount\":3000,\"description\":\"VQRIO123\","
                    + "\"accountNumber\":\"12345678\",\"reference\":\"TF230204212323\","
                    + "\"transactionDateTime\":\"2023-02-04 18:25:00\",\"currency\":\"VND\","
                    + "\"paymentLinkId\":\"124c33293c43417ab7879e14c8d9eb18\",\"code\":\"00\","
                    + "\"desc\":\"Thành công\",\"counterAccountBankId\":\"\","
                    + "\"counterAccountBankName\":\"\",\"counterAccountName\":\"\","
                    + "\"counterAccountNumber\":\"\",\"virtualAccountName\":\"\","
                    + "\"virtualAccountNumber\":\"\"}";

    @Test
    void applicationYmlResolvesReturnUrlFromPublicBase() throws Exception {
        PropertySourcesPropertyResolver resolver = resolverFor(Map.of(
                "APP_PUBLIC_BASE_URL", "https://api.example.com"));

        assertThat(resolver.getProperty("app.payos.return-url"))
                .isEqualTo("https://api.example.com/api/v1/payment/payos/return");
        assertThat(resolver.getProperty("app.payos.cancel-url"))
                .isEqualTo("https://api.example.com/api/v1/payment/payos/cancel");
    }

    @Test
    void explicitReturnUrlWinsOverPublicBase() throws Exception {
        PropertySourcesPropertyResolver resolver = resolverFor(Map.of(
                "APP_PUBLIC_BASE_URL", "https://api.example.com",
                "PAYOS_RETURN_URL", "https://payments.example.com/payos/return"));

        assertThat(resolver.getProperty("app.payos.return-url"))
                .isEqualTo("https://payments.example.com/payos/return");
    }

    @Test
    void staleLocalhostConfigIsReplacedByPublicBase() {
        PayosServiceImpl service = newService();
        ReflectionTestUtils.setField(service, "publicBaseUrl", "https://api.example.com");
        ReflectionTestUtils.setField(service, "frontendUrl", "https://app.example.com");

        String resolved = ReflectionTestUtils.invokeMethod(service, "resolvePublicUrl",
                "http://localhost:8081/api/v1/payment/payos/return",
                "/api/v1/payment/payos/return");

        assertThat(resolved).isEqualTo("https://api.example.com/api/v1/payment/payos/return");
    }

    @Test
    void returnUrlPointingAtFrontendIsReplacedByPublicBase() {
        PayosServiceImpl service = newService();
        ReflectionTestUtils.setField(service, "publicBaseUrl", "https://api.example.com");
        ReflectionTestUtils.setField(service, "frontendUrl", "https://app.example.com");

        String resolved = ReflectionTestUtils.invokeMethod(service, "resolvePublicUrl",
                "https://app.example.com/payment/payos/result",
                "/api/v1/payment/payos/return");

        assertThat(resolved).isEqualTo("https://api.example.com/api/v1/payment/payos/return");
    }

    @Test
    void localDevelopmentKeepsConfiguredLocalhostUrl() {
        PayosServiceImpl service = newService();
        ReflectionTestUtils.setField(service, "publicBaseUrl", "http://localhost:8081");
        ReflectionTestUtils.setField(service, "frontendUrl", "http://localhost:3000");

        String resolved = ReflectionTestUtils.invokeMethod(service, "resolvePublicUrl",
                "http://localhost:8081/api/v1/payment/payos/return",
                "/api/v1/payment/payos/return");

        assertThat(resolved).isEqualTo("http://localhost:8081/api/v1/payment/payos/return");
    }

    /**
     * payOS trả transactions[].virtualAccountName = null trên giao dịch PAID. Nếu đổi null thành ""
     * khi serialize thì HMAC lệch -> queryPayment() trả null -> return/status/reconcile đều treo.
     * Dữ liệu này lấy từ test chính thức của payos-lib-java (SignedResponseDataTest).
     */
    @Test
    void paidQuerySignatureKeepsNestedNullAsJsonNull() throws Exception {
        PayosServiceImpl service = newService();
        ReflectionTestUtils.setField(service, "checksumKey", CHECKSUM_KEY);
        JsonNode data = new ObjectMapper().readTree(PAID_QUERY_DATA);

        String signable = ReflectionTestUtils.invokeMethod(service, "signable", data);
        String signature = ReflectionTestUtils.invokeMethod(service, "hmacSHA256", CHECKSUM_KEY, signable);

        assertThat(signable).contains("\"virtualAccountName\":null");
        assertThat(signable).doesNotContain("\"virtualAccountName\":\"\"");
        assertThat(signature).as("signable=%s", signable)
                .isEqualTo("ef982c0748eaf7f2845f83537eb6d6ab974b39e143a785a64710fe666514ec99");
    }

    @Test
    void webhookSignatureSampleStillMatches() throws Exception {
        PayosServiceImpl service = newService();
        ReflectionTestUtils.setField(service, "checksumKey", CHECKSUM_KEY);
        JsonNode data = new ObjectMapper().readTree(WEBHOOK_SAMPLE_DATA);

        String signable = ReflectionTestUtils.invokeMethod(service, "signable", data);
        String signature = ReflectionTestUtils.invokeMethod(service, "hmacSHA256", CHECKSUM_KEY, signable);

        assertThat(signature).isEqualTo("412e915d2871504ed31be63c8f62a149a4410d34c4c42affc9006ef9917eaa03");
    }

    private PropertySourcesPropertyResolver resolverFor(Map<String, Object> environment) throws Exception {
        List<PropertySource<?>> yamlSources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        MutablePropertySources sources = new MutablePropertySources();
        sources.addFirst(new MapPropertySource("testEnvironment", environment));
        yamlSources.forEach(sources::addLast);

        return new PropertySourcesPropertyResolver(sources);
    }

    private PayosServiceImpl newService() {
        return new PayosServiceImpl(
                mock(PayosTransactionRepository.class),
                mock(WalletService.class),
                mock(UserRepository.class),
                new ObjectMapper(),
                new RestTemplateBuilder(),
                mock(PlatformTransactionManager.class));
    }
}
