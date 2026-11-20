package com.aiinterview.platform.service.admin.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.model.dto.request.TopCvBatchImportRequest;
import com.aiinterview.platform.model.dto.response.TopCvBatchImportResult;
import com.aiinterview.platform.model.dto.response.TopCvImportResult;
import com.aiinterview.platform.model.entity.JobDescription;
import com.aiinterview.platform.model.entity.JobModerationRecord;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.JobStatus;
import com.aiinterview.platform.model.enums.ModerationAction;
import com.aiinterview.platform.model.enums.ModerationActorRole;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.JobDescriptionRepository;
import com.aiinterview.platform.model.repository.JobModerationRecordRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.service.ai.JdPolicyScreener;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TopCvImportService — cào JD từ TopCV/ITviec rồi đăng thẳng lên job board. Test chạy offline bằng
 * một {@link HttpClient} giả (thay cho HttpClient thật của JDK) nên không phụ thuộc mạng hay
 * Cloudflare.
 * <p>
 * Ba nhóm hợp đồng được kiểm chứng:
 * <ul>
 *   <li>chặn SSRF/URL rác trước khi gọi mạng: chỉ nhận http(s) + đúng 2 tên miền;</li>
 *   <li>bóc tách: ưu tiên JSON-LD (JobPosting), thiếu thì lùi về OpenGraph/H1/&lt;title&gt;,
 *       và tự suy ra kỹ năng khi trang không khai báo;</li>
 *   <li>batch: khử trùng link, tôn trọng limit, một URL lỗi không làm hỏng cả lô, và có fallback
 *       TopCV → ITviec.</li>
 * </ul>
 */
class TopCvImportServiceImplTest {

    private static final String TOPCV_LISTING =
            "https://www.topcv.vn/tim-viec-lam-cong-nghe-thong-tin-cr257";
    private static final String ITVIEC_LISTING = "https://itviec.com/it-jobs";
    private static final String ADMIN_EMAIL = "admin@aiinterview.vn";
    private static final String JOB_URL = "https://www.topcv.vn/viec-lam/java-developer-123456";

    private StubHttpClient http;
    private JobDescriptionRepository jobRepository;
    private JobModerationRecordRepository moderationRecordRepository;
    private UserRepository userRepository;
    private TopCvImportServiceImpl service;

    @BeforeEach
    void setUp() {
        http = new StubHttpClient();
        jobRepository = mock(JobDescriptionRepository.class);
        moderationRecordRepository = mock(JobModerationRecordRepository.class);
        userRepository = mock(UserRepository.class);

        service = new TopCvImportServiceImpl(new ObjectMapper(), jobRepository,
                moderationRecordRepository, userRepository);
        // @PostConstruct + @Value không chạy trong unit test nên bơm tay.
        ReflectionTestUtils.setField(service, "httpClient", http);
        ReflectionTestUtils.setField(service, "itListingUrl", TOPCV_LISTING);
        ReflectionTestUtils.setField(service, "itviecListingUrl", ITVIEC_LISTING);
        ReflectionTestUtils.setField(service, "defaultLimit", 10);
        ReflectionTestUtils.setField(service, "userAgent", "test-agent");

        when(jobRepository.save(any(JobDescription.class))).thenAnswer(call -> {
            JobDescription saved = call.getArgument(0);
            saved.setId(99L);
            return saved;
        });

        User admin = User.builder().id(7L).email(ADMIN_EMAIL).fullName("Admin")
                .role(Role.ROLE_ADMIN).enabled(true).build();
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                ADMIN_EMAIL, null, List.of(new SimpleGrantedAuthority(Role.ROLE_ADMIN.name()))));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ── kiểm tra URL trước khi gọi mạng (chống SSRF) ─────────────────────────

    @Test
    @DisplayName("URL rỗng bị chặn, không phát request nào")
    void importJob_rejectsBlankUrl() {
        assertThatThrownBy(() -> service.importJob("   "))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("URL không được để trống");
        assertThat(http.requested).isEmpty();
    }

    @Test
    @DisplayName("Chỉ nhận http/https: ftp:// bị chặn")
    void importJob_rejectsNonHttpScheme() {
        assertThatThrownBy(() -> service.importJob("ftp://www.topcv.vn/viec-lam/x"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("URL phải bắt đầu bằng http:// hoặc https://");
        assertThat(http.requested).isEmpty();
    }

    @Test
    @DisplayName("Tên miền lạ bị chặn: chỉ topcv.vn và itviec.com được cào")
    void importJob_rejectsForeignHost() {
        assertThatThrownBy(() -> service.importJob("https://evil.example.com/viec-lam/x"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Chỉ nhận URL thuộc tên miền topcv.vn hoặc itviec.com");
        assertThat(http.requested).isEmpty();
    }

    @Test
    @DisplayName("Chuỗi không phải URI hợp lệ bị chặn")
    void importJob_rejectsMalformedUrl() {
        assertThatThrownBy(() -> service.importJob("khong phai url"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("URL không hợp lệ");
    }

    @Test
    @DisplayName("Batch không có URL nào thì báo rõ thay vì trả lô rỗng")
    void importJobs_rejectsEmptyRequest() {
        assertThatThrownBy(() -> service.importJobs(new TopCvBatchImportRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Không tìm thấy tin tuyển dụng nào trong trang danh sách này");
    }

    // ── bóc tách + đăng tin ──────────────────────────────────────────────────

    @Test
    @DisplayName("JSON-LD JobPosting: đủ trường, đăng ngay OPEN và ghi lịch sử kiểm duyệt APPROVED")
    void importJob_publishesJobFromJsonLd() {
        // Lưu ý: trong JSON-LD, thẻ đóng script phải viết "<\\/script>". Nếu viết nguyên "</script>"
        // thì regex non-greedy sẽ cắt ngang block JSON (đây cũng là lỗi thật khi cào trang).
        http.respond(JOB_URL, 200, page("""
                <script type="application/ld+json">
                {"@context":"https://schema.org","@type":"JobPosting",
                 "title":"  Senior Java Developer  ",
                 "hiringOrganization":{"name":"Acme VN"},
                 "occupationalCategory":"Backend",
                 "jobLocation":{"@type":"Place","address":{"streetAddress":"12 Ly Thuong Kiet",
                   "addressLocality":"Ha Noi","addressCountry":"VN"}},
                 "baseSalary":{"currency":"USD","value":{"minValue":20,"maxValue":30,"unitText":"HOUR"}},
                 "description":"<p>Build <b>microservices</b></p><script>alert(1)<\\/script>",
                 "qualifications":"3+ years Java",
                 "experienceRequirements":"Spring Boot",
                 "skills":"Java, Spring Boot"}
                </script>
                """));

        TopCvImportResult result = service.importJob(JOB_URL);

        assertThat(result.getJobId()).isEqualTo(99L);
        assertThat(result.getTitle()).isEqualTo("Senior Java Developer");
        assertThat(result.getCompanyName()).isEqualTo("Acme VN");
        assertThat(result.getPosition()).isEqualTo("Backend");
        assertThat(result.getSalaryRange()).isEqualTo("20 - 30 USD / HOUR");
        assertThat(result.getLocation()).contains("Ha Noi").contains("VN");
        assertThat(result.getDescription()).contains("microservices").doesNotContain("<b>").doesNotContain("alert(1)");
        assertThat(result.getRequirements()).isEqualTo("3+ years Java\n\nSpring Boot");
        assertThat(result.getRequiredSkills()).isEqualTo("Java, Spring Boot");

        ArgumentCaptor<JobDescription> jobCaptor = ArgumentCaptor.forClass(JobDescription.class);
        verify(jobRepository).save(jobCaptor.capture());
        JobDescription saved = jobCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(JobStatus.OPEN);
        assertThat(saved.getEmployer().getEmail()).isEqualTo(ADMIN_EMAIL);
        // Điểm an toàn được tính lại từ chính nội dung vừa lưu -> phải khớp, không phải số cứng.
        assertThat(saved.getAiSafetyScore()).isEqualTo(JdPolicyScreener.screen(saved).score());

        ArgumentCaptor<JobModerationRecord> recordCaptor =
                ArgumentCaptor.forClass(JobModerationRecord.class);
        verify(moderationRecordRepository).save(recordCaptor.capture());
        JobModerationRecord record = recordCaptor.getValue();
        assertThat(record.getAction()).isEqualTo(ModerationAction.APPROVED);
        assertThat(record.getActorRole()).isEqualTo(ModerationActorRole.ADMIN);
        assertThat(record.getActor().getEmail()).isEqualTo(ADMIN_EMAIL);
        assertThat(record.getNote()).isEqualTo("Đồng bộ JD IT tự động");
    }

    @Test
    @DisplayName("Không có JSON-LD: lùi về OpenGraph, company suy ra từ tên miền và tự dò kỹ năng")
    void importJob_fallsBackToOpenGraphMeta() {
        http.respond(JOB_URL, 200, page("""
                <meta property="og:title" content="React Developer">
                <meta property="og:description" content="Build UI with React &amp; TypeScript">
                <h1>Ignored headline</h1>
                """));

        TopCvImportResult result = service.importJob(JOB_URL);

        assertThat(result.getTitle()).isEqualTo("React Developer");
        assertThat(result.getCompanyName()).isEqualTo("TopCV");
        assertThat(result.getDescription()).isEqualTo("Build UI with React & TypeScript");
        assertThat(result.getRequiredSkills()).contains("react").contains("typescript");
    }

    @Test
    @DisplayName("Trang không có tiêu đề nào: báo lỗi kèm tên miền để admin biết phải xem ở đâu")
    void importJob_rejectsPageWithoutTitle() {
        http.respond(JOB_URL, 200, "<html><body><div>khong co gi</div></body></html>");

        assertThatThrownBy(() -> service.importJob(JOB_URL))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Không bóc tách được tiêu đề tin tuyển dụng từ URL www.topcv.vn này");
        verify(jobRepository, org.mockito.Mockito.never()).save(any(JobDescription.class));
    }

    // ── lỗi HTTP ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("HTTP 403 (Cloudflare) được giải thích kèm 3 cách khắc phục, không phải 'lỗi 500'")
    void importJob_explainsCloudflare403() {
        http.respond(JOB_URL, 403, "");

        assertThatThrownBy(() -> service.importJob(JOB_URL))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cloudflare HTTP 403")
                .hasMessageContaining("TOPCV_COOKIE")
                .hasMessageContaining("TOPCV_PROXY_URL")
                .hasMessageContaining("TOPCV_SCRAPER_API_URL");
    }

    @Test
    @DisplayName("HTTP 503 là lỗi tạm thời: thử lại và thành công ở lần 2")
    void importJob_retriesTransientStatus() {
        http.respond(JOB_URL, 503, "");
        http.respond(JOB_URL, 200, "<html><head><title>Java Dev</title></head></html>");

        TopCvImportResult result = service.importJob(JOB_URL);

        assertThat(result.getTitle()).isEqualTo("Java Dev");
        assertThat(http.requested).hasSize(2);
    }

    @Test
    @DisplayName("Bị giới hạn tần suất 3 lần liên tiếp: dừng và hướng dẫn giảm số tin mỗi lần")
    void importJob_givesUpAfterThreeRetries() {
        http.respond(JOB_URL, 429, "");
        http.respond(JOB_URL, 429, "");
        http.respond(JOB_URL, 429, "");

        assertThatThrownBy(() -> service.importJob(JOB_URL))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("HTTP 429")
                .hasMessageContaining("giảm số lượng tin mỗi lần");
        assertThat(http.requested).hasSize(3);
    }

    @Test
    @DisplayName("Trang trả về rỗng là lỗi dữ liệu, không phải lỗi mạng")
    void importJob_rejectsEmptyBody() {
        http.respond(JOB_URL, 200, "   ");

        assertThatThrownBy(() -> service.importJob(JOB_URL))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Trang www.topcv.vn trả về nội dung rỗng");
    }

    // ── batch ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Trang danh sách: chỉ lấy link chi tiết, khử trùng, bỏ query và tôn trọng limit")
    void importJobs_discoversDetailLinksFromListing() {
        String javaUrl = "https://www.topcv.vn/viec-lam/java-developer-123456";
        String itviecUrl = "https://itviec.com/it-jobs/nodejs-developer-12345";
        http.respond(TOPCV_LISTING, 200, """
                <html><body>
                <a href="/viec-lam/java-developer-123456">Java</a>
                <a href="/viec-lam/java-developer-123456?utm_source=home">trung lap</a>
                <a href="#top">len dau</a>
                <a href="javascript:void(0)">js</a>
                <a href="https://itviec.com/it-jobs/nodejs-developer-12345">Node</a>
                <a href="/tuyen-dung-cong-ty">khong phai chi tiet</a>
                </body></html>
                """);
        http.respond(javaUrl, 200, "<html><head><title>Java Developer</title></head></html>");
        http.respond(itviecUrl, 200, "<html><head><title>Node Developer</title></head></html>");

        TopCvBatchImportResult result = service.importJobs(request(TOPCV_LISTING, 10));

        assertThat(result.getSourceUrl()).isEqualTo(TOPCV_LISTING);
        assertThat(result.getRequested()).isEqualTo(2);
        assertThat(result.getImported()).isEqualTo(2);
        assertThat(result.getFailed()).isZero();
        assertThat(result.getItems()).extracting(TopCvImportResult::getTitle)
                .containsExactly("Java Developer", "Node Developer");
    }

    @Test
    @DisplayName("Limit cắt bớt số link lấy từ trang danh sách")
    void importJobs_respectsLimit() {
        http.respond(TOPCV_LISTING, 200, """
                <html><body>
                <a href="/viec-lam/java-developer-111">A</a>
                <a href="/viec-lam/java-developer-222">B</a>
                <a href="/viec-lam/java-developer-333">C</a>
                </body></html>
                """);
        http.respond("https://www.topcv.vn/viec-lam/java-developer-111", 200, title("Tin A"));

        TopCvBatchImportResult result = service.importJobs(request(TOPCV_LISTING, 1));

        assertThat(result.getRequested()).isEqualTo(1);
        assertThat(result.getImported()).isEqualTo(1);
        assertThat(result.getItems().get(0).getTitle()).isEqualTo("Tin A");
    }

    @Test
    @DisplayName("Một URL lỗi chỉ rơi vào danh sách failures; URL còn lại vẫn được đăng")
    void importJobs_isolatesPerUrlFailures() {
        String brokenUrl = "https://www.topcv.vn/viec-lam/blocked-999999";
        http.respond(JOB_URL, 200, title("Java Developer"));
        http.respond(brokenUrl, 403, "");

        TopCvBatchImportRequest request = new TopCvBatchImportRequest();
        request.setUrls(List.of(JOB_URL, brokenUrl));
        request.setLimit(10);

        TopCvBatchImportResult result = service.importJobs(request);

        assertThat(result.getRequested()).isEqualTo(2);
        assertThat(result.getImported()).isEqualTo(1);
        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getFailures().get(0).getUrl()).isEqualTo(brokenUrl);
        assertThat(result.getFailures().get(0).getMessage()).contains("Cloudflare HTTP 403");
    }

    @Test
    @DisplayName("TopCV không trả về tin nào: tự chuyển sang ITviec và báo đúng nguồn đã dùng")
    void importAutoItJobs_fallsBackToItviec() {
        http.respond(TOPCV_LISTING, 200, "<html><body>khong co tin</body></html>");
        http.respond(ITVIEC_LISTING, 200, """
                <html><body><a href="https://itviec.com/it-jobs/java-dev-12345">Java</a></body></html>
                """);
        http.respond("https://itviec.com/it-jobs/java-dev-12345", 200, title("ITviec Java"));

        TopCvBatchImportResult result = service.importAutoItJobs(5);

        assertThat(result.getSourceUrl()).isEqualTo(ITVIEC_LISTING);
        assertThat(result.getImported()).isEqualTo(1);
        assertThat(result.getItems().get(0).getTitle()).isEqualTo("ITviec Java");
    }

    @Test
    @DisplayName("limit <= 0 dùng app.topcv.default-limit thay vì cào hết trang")
    void importDefaultItJobs_usesConfiguredDefaultLimit() {
        ReflectionTestUtils.setField(service, "defaultLimit", 1);
        http.respond(TOPCV_LISTING, 200, """
                <html><body>
                <a href="/viec-lam/java-developer-111">A</a>
                <a href="/viec-lam/java-developer-222">B</a>
                </body></html>
                """);
        http.respond("https://www.topcv.vn/viec-lam/java-developer-111", 200, title("Tin A"));

        TopCvBatchImportResult result = service.importDefaultItJobs(0);

        assertThat(result.getRequested()).isEqualTo(1);
        assertThat(result.getImported()).isEqualTo(1);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String page(String headAndBody) {
        return "<html><head>" + headAndBody + "</head><body></body></html>";
    }

    private static String title(String text) {
        return "<html><head><title>" + text + "</title></head></html>";
    }

    private static TopCvBatchImportRequest request(String url, int limit) {
        TopCvBatchImportRequest request = new TopCvBatchImportRequest();
        request.setUrl(url);
        request.setLimit(limit);
        return request;
    }

    private record StubResponse(int status, String body) {
    }

    /**
     * HttpClient giả: cùng một URL có thể nạp nhiều response theo thứ tự (dùng để test retry),
     * hết kịch bản thì lặp lại response cuối.
     */
    private static final class StubHttpClient extends HttpClient {

        private final Map<String, Deque<StubResponse>> scripted = new ConcurrentHashMap<>();
        private final List<String> requested = new CopyOnWriteArrayList<>();

        void respond(String url, int status, String body) {
            scripted.computeIfAbsent(url, key -> new ConcurrentLinkedDeque<>())
                    .add(new StubResponse(status, body));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            String url = request.uri().toString();
            requested.add(url);
            Deque<StubResponse> queue = scripted.get(url);
            if (queue == null || queue.isEmpty()) {
                throw new IllegalStateException("Không nạp response cho " + url);
            }
            StubResponse next = queue.size() > 1 ? queue.poll() : queue.peek();
            return (HttpResponse<T>) responseOf(request, next);
        }

        private static HttpResponse<String> responseOf(HttpRequest request, StubResponse stub) {
            return new HttpResponse<>() {
                @Override
                public int statusCode() {
                    return stub.status();
                }

                @Override
                public HttpRequest request() {
                    return request;
                }

                @Override
                public Optional<HttpResponse<String>> previousResponse() {
                    return Optional.empty();
                }

                @Override
                public HttpHeaders headers() {
                    return HttpHeaders.of(Map.of(), (name, value) -> true);
                }

                @Override
                public String body() {
                    return stub.body();
                }

                @Override
                public Optional<javax.net.ssl.SSLSession> sslSession() {
                    return Optional.empty();
                }

                @Override
                public URI uri() {
                    return request.uri();
                }

                @Override
                public HttpClient.Version version() {
                    return HttpClient.Version.HTTP_1_1;
                }
            };
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            throw new UnsupportedOperationException("không dùng trong test");
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> handler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException("không dùng trong test");
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.of(Duration.ofSeconds(1));
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NORMAL;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            try {
                return SSLContext.getDefault();
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<java.util.concurrent.Executor> executor() {
            return Optional.empty();
        }

        @Override
        public Optional<java.net.Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public WebSocket.Builder newWebSocketBuilder() {
            throw new UnsupportedOperationException("không dùng trong test");
        }
    }
}
