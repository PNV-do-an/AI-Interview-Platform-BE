package com.aiinterview.platform.security.jwt;

import com.aiinterview.platform.model.entity.ErrorModel;
import com.aiinterview.platform.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class JwtSecurityConfig {

    private final UserService userService;
    private final ObjectMapper objectMapper;

    @Value("${cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // Second layer: @PreAuthorize on the controller still applies.
                        // 009 FR-007: staff quyết định duyệt JD (PATCH /jobs/{id}/review) nên phải đọc
                        // được hàng chờ, trong khi mọi endpoint admin khác vẫn ADMIN-only.
                        .requestMatchers("/api/v1/admin/jobs/moderation/**").hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().permitAll()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeError(response, 401, "Yêu cầu đăng nhập", request.getRequestURL().toString()))
                        .accessDeniedHandler((request, response, deniedException) ->
                                writeError(response, 403, "Bạn không có quyền truy cập chức năng quản trị",
                                        request.getRequestURL().toString()))
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .addFilterBefore(
                        new JwtFilter(userService),
                        UsernamePasswordAuthenticationFilter.class
                )
                .build();
    }

    private void writeError(HttpServletResponse response, int status,
                            String message, String path) throws IOException {
        response.setStatus(status);
        // Không có charset thì getWriter() dùng ISO-8859-1, dấu tiếng Việt trong thông lỗi bị vỡ.
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(),
                new ErrorModel(status, message, path, LocalDateTime.now(), new HashMap<>()));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}