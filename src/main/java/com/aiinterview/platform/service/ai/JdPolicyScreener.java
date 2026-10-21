package com.aiinterview.platform.service.ai;

import com.aiinterview.platform.model.entity.JobDescription;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Sàng lọc chính sách tin tuyển dụng bằng rule tất định — KHÔNG gọi mô hình AI nào.
 * Tên trường giữ "aiSafety*" theo hợp đồng FE (spec 004 FR-005 để trống cột này,
 * 009 FR-006 fill bằng rule). Vì mọi kiểm tra chỉ đọc dữ liệu đã lưu trên job,
 * điểm ghi lúc nộp và điểm tính lại lúc đọc luôn khớp nhau.
 */
public final class JdPolicyScreener {

    public static final int PENALTY_PER_CHECK = 25;

    private static final List<String> IT_TERMS = List.of(
            "java", "javascript", "typescript", "python", "go", "golang", "rust", "php", ".net",
            "node", "react", "angular", "vue", "spring", "hibernate", "sql", "mysql", "postgres",
            "mongodb", "redis", "kafka", "docker", "kubernetes", "aws", "azure", "gcp", "devops",
            "backend", "frontend", "fullstack", "full-stack", "software", "engineer", "developer",
            "programmer", "coder", "tester", "qa", "qc", "data", "ai", "ml", "machine learning",
            "deep learning", "mobile", "android", "ios", "flutter", "react native", "embedded",
            "system", "network", "security", "cloud", "api", "web", "game", "blockchain",
            "kỹ sư", "lập trình", "phần mềm", "công nghệ thông tin", "dữ liệu", "triển khai",
            "kiểm thử", "phát triển", "crawler", "sap", "odoo", "erp", "crm", "laravel", "django",
            "fastapi", "graphql", "grpc", "microservice", "linux", "terraform", "ansible", "jenkins",
            "ci/cd", "etl", "spark", "hadoop", "tableau", "power bi", "puppeteer", "playwright",
            "selenium", "cypress", "appium", "iot", "firmware", "rtos");

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?84|0)(?:\\d[\\s.]?){8,10}\\d(?![\\d.])");
    private static final Pattern URL = Pattern.compile("(?i)(https?://|www\\.[^\\s]+|\\b[^\\s]+\\.(vn|com|net|io|ai)/)");

    private static final int MIN_DESCRIPTION_CHARS = 200;

    private JdPolicyScreener() {
    }

    public record Check(String key, String label, boolean passed, String note) {
    }

    public record Screening(int score, List<Check> checks) {
    }

    public static Screening screen(JobDescription job) {
        List<Check> checks = List.of(
                itTitle(job),
                descriptionLength(job),
                requirementsPresent(job),
                contactLeaks(job));
        int failed = (int) checks.stream().filter(c -> !c.passed()).count();
        return new Screening(100 - failed * PENALTY_PER_CHECK, checks);
    }

    private static Check itTitle(JobDescription job) {
        String title = job.getTitle() == null ? "" : job.getTitle().toLowerCase(Locale.ROOT);
        String hit = IT_TERMS.stream().filter(title::contains).findFirst().orElse(null);
        return new Check("IT_RELEVANT_TITLE", "Tiêu đề đúng lĩnh vực công nghệ",
                hit != null,
                hit == null
                        ? "Tiêu đề không chứa từ khóa kỹ thuật nào, có thể là tin không thuộc lĩnh vực IT."
                        : "Tiêu đề khớp từ khóa kỹ thuật \"" + hit + "\".");
    }

    private static Check descriptionLength(JobDescription job) {
        int length = job.getDescription() == null ? 0 : job.getDescription().trim().length();
        boolean passed = length >= MIN_DESCRIPTION_CHARS;
        return new Check("DESCRIPTION_LONG_ENOUGH", "Mô tả đủ chi tiết",
                passed,
                passed
                        ? "Mô tả có " + length + " ký tự (ngưỡng " + MIN_DESCRIPTION_CHARS + ")."
                        : "Mô tả chỉ có " + length + " ký tự, cần tối thiểu " + MIN_DESCRIPTION_CHARS + ".");
    }

    private static Check requirementsPresent(JobDescription job) {
        boolean requirements = notBlank(job.getRequirements());
        boolean skills = notBlank(job.getRequiredSkills());
        boolean passed = requirements && skills;
        String missing = !requirements && !skills
                ? "thiếu cả phần yêu cầu lẫn danh sách kỹ năng"
                : !requirements ? "thiếu phần yêu cầu" : "thiếu danh sách kỹ năng";
        return new Check("REQUIREMENTS_PRESENT", "Có yêu cầu và kỹ năng bắt buộc",
                passed,
                passed ? "Ứng viên biết rõ tin này đòi hỏi gì." : "Tin bị loại vì " + missing + ".");
    }

    private static Check contactLeaks(JobDescription job) {
        String body = String.join(" ",
                job.getDescription() == null ? "" : job.getDescription(),
                job.getRequirements() == null ? "" : job.getRequirements(),
                job.getTitle() == null ? "" : job.getTitle());
        String found = null;
        if (EMAIL.matcher(body).find()) {
            found = "địa chỉ email";
        } else if (URL.matcher(body).find()) {
            found = "đường link";
        } else if (PHONE.matcher(body).find()) {
            found = "số điện thoại";
        }
        return new Check("NO_CONTACT_LEAKS", "Không lộ thông tin liên lạc ngoài nền tảng",
                found == null,
                found == null
                        ? "Không phát hiện email, số điện thoại hay link trong nội dung tin."
                        : "Nội dung tin chứa " + found + " — ứng viên có thể bị dẫn ra ngoài nền tảng.");
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
