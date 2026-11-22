package com.aiinterview.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Một HttpClient dùng chung cho các lời gọi OAuth/OIDC (Google, GitHub, SSO).
 *
 * <p>Trước đây mỗi service tự {@code new HttpClient} trong class nên unit test không có điểm tiêm và
 * toàn bộ nhánh sau lời gọi HTTP (kiểm tra aud/iss/email, GitHub từ chối code, OIDC discovery) không
 * phủ được. Đưa ra bean thì test tiêm được bản giả, còn production giữ nguyên timeout 10 giây và
 * không đổi hành vi redirect.</p>
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public HttpClient oauthHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }
}
