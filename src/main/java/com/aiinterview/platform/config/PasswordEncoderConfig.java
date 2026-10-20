package com.aiinterview.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * PasswordEncoder để ở class riêng thay vì khai trong JwtSecurityConfig: JwtSecurityConfig
 * phụ thuộc UserService mà UserService lại cần encoder — gộp chung thành vòng tròn khởi tạo,
 * Spring không load được context.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
