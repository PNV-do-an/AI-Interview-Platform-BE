package com.aiinterview.platform.config;

import com.aiinterview.platform.common.util.SkillMatcher;
import com.aiinterview.platform.model.entity.PositionCatalog;
import com.aiinterview.platform.model.repository.PositionCatalogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seed catalog vi tri cho recommendation theo CV.
 * Chay mot lan luc startup: neu bang da co du lieu thi bo qua.
 * <p>
 * Seed nam o Java (khong nam trong V9) vi {@code spring.flyway.enabled = false} — cau lenh SQL
 * trong migration se khong duoc thuc thi, trong khi schema van duoc tao boi {@code ddl-auto: update}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PositionCatalogSeeder implements ApplicationRunner {

    private final PositionCatalogRepository positionCatalogRepository;

    @Override
    public void run(ApplicationArguments args) {
        List<PositionCatalog> catalog = List.of(
                position("Backend Java Engineer", "backend-java-engineer", "MID",
                        "Phát triển backend bằng Java/Spring Boot: thiết kế REST API, làm việc với cơ sở dữ liệu quan hệ, cache và triển khai bằng Docker.",
                        "Tuyển Backend Java Engineer (2-4 năm kinh nghiệm). Yêu cầu: Java, Spring Boot, REST API, MySQL, "
                                + "Hibernate, Redis, Docker. Ưu tiên ứng viên có kinh nghiệm microservices và CI/CD.",
                        "Java", "Spring Boot", "MySQL", "REST API", "Hibernate", "Docker", "Redis"),
                position("Frontend React Engineer", "frontend-react-engineer", "MID",
                        "Xây dựng giao diện web bằng React/TypeScript: quản lý state, tối ưu hiệu năng và viết test cho component.",
                        "Tuyển Frontend React Engineer. Yêu cầu: JavaScript, TypeScript, React, Redux, HTML/CSS, "
                                + "làm việc với REST API và viết test bằng Jest.",
                        "JavaScript", "TypeScript", "React", "Redux", "HTML", "CSS", "REST API", "Jest"),
                position("Fullstack Engineer", "fullstack-engineer", "SENIOR",
                        "Phụ trách cả backend Spring Boot và frontend React, làm việc xuyên suốt từ API đến giao diện người dùng.",
                        "Tuyển Fullstack Engineer (5+ năm). Yêu cầu: Java, Spring Boot, React, TypeScript, MySQL, Docker, "
                                + "thiết kế REST API và triển khai CI/CD.",
                        "Java", "Spring Boot", "React", "TypeScript", "MySQL", "Docker", "REST API"),
                position("Data Engineer", "data-engineer", "MID",
                        "Xây dựng và vận hành pipeline dữ liệu: ingest, biến đổi, mô hình hóa dữ liệu và orchestration trên cloud.",
                        "Tuyển Data Engineer. Yêu cầu: Python, SQL, Apache Spark, Kafka, Airflow, data modeling "
                                + "và kinh nghiệm với AWS.",
                        "Python", "SQL", "Apache Spark", "Kafka", "Airflow", "Data Modeling", "AWS"),
                position("DevOps Engineer", "devops-engineer", "SENIOR",
                        "Tự động hóa CI/CD, quản lý hạ tầng bằng IaC, vận hành container và giám sát hệ thống.",
                        "Tuyển DevOps Engineer. Yêu cầu: Docker, Kubernetes, CI/CD, Linux, Terraform, AWS "
                                + "và kinh nghiệm monitoring (Prometheus/Grafana).",
                        "Docker", "Kubernetes", "CI/CD", "Linux", "Terraform", "AWS", "Monitoring"),
                position("AI/ML Engineer", "ai-ml-engineer", "SENIOR",
                        "Huấn luyện và triển khai mô hình machine learning, xử lý ngôn ngữ tự nhiên và vận hành mô hình trong production.",
                        "Tuyển AI/ML Engineer. Yêu cầu: Python, PyTorch hoặc TensorFlow, Machine Learning, NLP, "
                                + "Docker và quy trình MLOps.",
                        "Python", "PyTorch", "TensorFlow", "Machine Learning", "NLP", "Docker", "MLOps"),
                position("Android Engineer", "android-engineer", "MID",
                        "Phát triển ứng dụng Android hiện đại bằng Kotlin, Jetpack Compose và kiến trúc sạch.",
                        "Tuyển Android Engineer. Yêu cầu: Kotlin, Android SDK, Jetpack Compose, Room, REST API và Git.",
                        "Kotlin", "Android SDK", "Jetpack Compose", "REST API", "Room", "Git"),
                position("QA Automation Engineer", "qa-automation-engineer", "MID",
                        "Xây dựng bộ test tự động cho API và giao diện, tích hợp vào pipeline CI/CD.",
                        "Tuyển QA Automation Engineer. Yêu cầu: Java, Selenium, TestNG, kiểm thử REST API, SQL "
                                + "và tích hợp test vào CI/CD.",
                        "Java", "Selenium", "TestNG", "REST API", "CI/CD", "SQL")
        );

        if (positionCatalogRepository.count() == 0) {
            positionCatalogRepository.saveAll(catalog);
            log.info("=== Position catalog seeded: {} positions ===", catalog.size());
            return;
        }

        // Catalog da co tu lan chay truoc: chi bo sung JD con thieu cho cac vi tri seed.
        int backfilled = 0;
        for (PositionCatalog seed : catalog) {
            backfilled += positionCatalogRepository.backfillJdText(seed.getSlug(), seed.getJdText());
        }
        log.info("Position catalog already seeded - {} JD texts backfilled.", backfilled);
    }

    private PositionCatalog position(String title, String slug, String level, String description,
                                     String jdText, String... requiredSkills) {
        PositionCatalog position = PositionCatalog.builder()
                .title(title)
                .slug(slug)
                .level(level)
                .description(description)
                .jdText(jdText)
                .active(true)
                .build();
        for (String skill : requiredSkills) {
            position.addRequiredSkill(skill, SkillMatcher.normalize(skill));
        }
        return position;
    }
}
