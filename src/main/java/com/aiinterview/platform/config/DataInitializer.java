package com.aiinterview.platform.config;

import com.aiinterview.platform.model.entity.ServicePackage;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.ServicePackageRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seed một tài khoản Admin mặc định khi DB chưa có user nào,
 * và các gói dịch vụ mẫu khi chưa có gói nào.
 * Chỉ chạy một lần lúc startup; nếu đã có dữ liệu thì bỏ qua.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ServicePackageRepository servicePackageRepository;

    private static final String DEFAULT_ADMIN_EMAIL    = "admin@aiinterview.com";
    private static final String DEFAULT_ADMIN_NAME     = "Super Admin";

    @Value("${app.admin.default-password:Admin@12345}")
    private String defaultAdminPassword;

    /** Chỉ bật ở stack local/docker (xem docker-compose.yml); production để false. */
    @Value("${app.seed.test-accounts:false}")
    private boolean seedTestAccounts;

    @Value("${app.seed.test-password:Test@12345}")
    private String testPassword;

    @Override
    public void run(ApplicationArguments args) {
        seedAdmin();
        seedLocalAccounts();
        seedServicePackages();
    }

    private void seedAdmin() {
        if (userRepository.existsByEmail(DEFAULT_ADMIN_EMAIL)) {
            log.info("Admin account already exists — skipping seed.");
            return;
        }

        User admin = User.builder()
                .email(DEFAULT_ADMIN_EMAIL)
                .password(passwordEncoder.encode(defaultAdminPassword))
                .fullName(DEFAULT_ADMIN_NAME)
                .role(Role.ROLE_ADMIN)
                .enabled(true)
                .locked(false)
                .build();

        userRepository.save(admin);
        log.info("=== Default admin created: {} ===", DEFAULT_ADMIN_EMAIL);
    }

    /**
     * Tài khoản test đã xác thực sẵn, để chạy Postman mà không cần Gmail API.
     *
     * <p>Luồng register thật tạo user ở trạng thái {@code enabled=false} rồi gửi mail xác thực;
     * thiếu {@code GMAIL_*} thì {@code EmailServiceImpl} ném {@code MailDeliveryException} và cả
     * transaction rollback (client nhận 502), nên chưa cấu hình mail thì không có đường nào tạo
     * được tài khoản {@code ROLE_USER} qua HTTP. Seed ở đây lấp đúng khoảng đó và mặc định tắt.</p>
     */
    private void seedLocalAccounts() {
        if (!seedTestAccounts) {
            return;
        }
        seedAccount("candidate@aiinterview.com", "Ứng viên Test", Role.ROLE_USER);
        seedAccount("staff@aiinterview.com", "Staff Test", Role.ROLE_STAFF);
        seedAccount("interviewer@aiinterview.com", "Interviewer Test", Role.ROLE_INTERVIEWER);
    }

    private void seedAccount(String email, String fullName, Role role) {
        if (userRepository.existsByEmail(email)) {
            return;
        }
        userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(testPassword))
                .fullName(fullName)
                .role(role)
                // enabled=true sẵn: bỏ qua bước verify email nên đăng nhập được ngay.
                .enabled(true)
                .locked(false)
                .build());
        log.info("=== Seeded test account: {} ({}) ===", email, role);
    }

    private void seedServicePackages() {
        if (!servicePackageRepository.findAll().isEmpty()) {
            return;
        }

        List<ServicePackage> packages = List.of(
                ServicePackage.builder()
                        .name("Cơ bản")
                        .description("Dành cho doanh nghiệp nhỏ mới bắt đầu tuyển dụng.")
                        .price(499_000L)
                        .durationDays(30)
                        .jobPostLimit(3)
                        .features(List.of(
                                "Đăng tối đa 3 tin tuyển dụng",
                                "Nhận hồ sơ ứng tuyển không giới hạn",
                                "Gợi ý ứng viên phù hợp bằng AI",
                                "Hỗ trợ qua email"))
                        .active(true)
                        .build(),
                ServicePackage.builder()
                        .name("Chuyên nghiệp")
                        .description("Dành cho doanh nghiệp cần tuyển nhanh với công cụ AI mạnh hơn.")
                        .price(1_299_000L)
                        .durationDays(30)
                        .jobPostLimit(10)
                        .features(List.of(
                                "Đăng tối đa 10 tin tuyển dụng",
                                "Tin được ưu tiên hiển thị",
                                "Phỏng vấn thử AI 20 lượt/tháng",
                                "Phân tích CV ứng viên bằng AI",
                                "Hỗ trợ ưu tiên 24/7"))
                        .active(true)
                        .build(),
                ServicePackage.builder()
                        .name("Doanh nghiệp")
                        .description("Gói toàn diện cho doanh nghiệp tuyển dụng quy mô lớn.")
                        .price(2_999_000L)
                        .durationDays(90)
                        .jobPostLimit(50)
                        .features(List.of(
                                "Đăng tối đa 50 tin tuyển dụng",
                                "Tin nổi bật trang chủ",
                                "Phỏng vấn thử AI không giới hạn",
                                "Phân tích CV & thư xin việc AI",
                                "Quản lý đội ngũ phỏng vấn",
                                "Chuyên viên hỗ trợ riêng"))
                        .active(true)
                        .build());

        servicePackageRepository.saveAll(packages);
        log.info("=== Seeded {} service packages ===", packages.size());
    }
}
