package com.aiinterview.platform.service.payment.impl;

import com.aiinterview.platform.model.repository.PayosTransactionRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.service.payment.WalletService;
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
