package com.aiinterview.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Không exclude FlywayAutoConfiguration: schema do flyway quản lý (V1..V12), Hibernate chỉ validate.
@SpringBootApplication
public class AiInterviewPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiInterviewPlatformApplication.class, args);
    }
}
