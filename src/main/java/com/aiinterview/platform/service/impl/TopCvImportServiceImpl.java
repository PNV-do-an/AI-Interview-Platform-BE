package com.aiinterview.platform.service.impl;

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
import com.aiinterview.platform.model.repository.JobDescriptionRepository;
import com.aiinterview.platform.model.repository.JobModerationRecordRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.security.utils.SecurityUtils;
import com.aiinterview.platform.service.TopCvImportService;
import com.aiinterview.platform.service.ai.JdPolicyScreener;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Cào JD từ TopCV/ITviec, lưu và đăng ngay (OPEN) để tin lên job board. */
@Slf4j
@Service
@RequiredArgsConstructor
public class TopCvImportServiceImpl implements TopCvImportService {

    private static final Pattern JSON_LD_SCRIPT = Pattern.compile(
            "<script[^>]*type\\s*=\\s*[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern META_TAG = Pattern.compile(
            "<meta\\s+[^>]*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern H1_TAG = Pattern.compile(
            "<h1[^>]*>(.*?)</h1>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HREF = Pattern.compile(
            "href\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern ITVIEC_JOB_PATH = Pattern.compile("^/it-jobs/[^/]+-\\d{3,6}$");
    private static final Pattern TAG = Pattern.compile("<[^>]+>", Pattern.DOTALL);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(25);

    private static final Set<String> KNOWN_SKILLS = new LinkedHashSet<>(List.of(
            "java", "spring", "spring boot", "spring mvc", "hibernate", "jpa", "maven", "gradle",
            "javascript", "typescript", "react", "reactjs", "vue", "vuejs", "angular", "nextjs",
            "nodejs", "node.js", "nestjs", "express", "php", "laravel", "python", "django", "flask",
            "fastapi", "c#", ".net", "dotnet", "golang", "go", "kotlin", "swift", "flutter",
            "dart", "android", "ios", "html", "css", "sass", "tailwind", "bootstrap", "sql",
            "mysql", "postgresql", "sql server", "oracle", "mongodb", "redis", "elasticsearch",
            "docker", "kubernetes", "aws", "azure", "gcp", "git", "ci/cd", "jenkins", "linux",
            "restful", "graphql", "microservices", "rabbitmq", "kafka", "terraform", "figma",
            "machine learning", "deep learning", "ai", "data analysis", "pandas", "numpy",
            "tensorflow", "pytorch", "selenium", "jest", "junit", "cypress"));

    private final ObjectMapper objectMapper;
    private final JobDescriptionRepository jobRepository;
    private final JobModerationRecordRepository moderationRecordRepository;
    private final UserRepository userRepository;

    @Value("${app.topcv.it-listing-url:https://www.topcv.vn/tim-viec-lam-cong-nghe-thong-tin-cr257}")
    private String itListingUrl;

    @Value("${app.topcv.itviec-listing-url:https://itviec.com/it-jobs}")
    private String itviecListingUrl;

    /**
     * URL của dịch vụ scraping có xử lý Cloudflare, dùng {@code {url}} làm chỗ chèn
     * link cần tải (đã URL-encode). Ví dụ:
     * {@code https://api.scraperapi.com/?api_key=KEY&url={url}}.
     */
    @Value("${app.topcv.scraper-api-url:}")
    private String scraperApiUrl;

    @Value("${app.topcv.default-limit:10}")
    private int defaultLimit;

    @Value("${app.topcv.proxy-url:}")
    private String proxyUrl;

    @Value("${app.topcv.cookie:}")
    private String cookie;

    @Value("${app.topcv.user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36}")
    private String userAgent;

    private HttpClient httpClient;

    @PostConstruct
    void initHttpClient() {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (proxyUrl != null && !proxyUrl.isBlank()) {
            try {
                URI proxy = URI.create(proxyUrl.trim());
                int port = proxy.getPort() > 0 ? proxy.getPort() : 8080;
                builder.proxy(ProxySelector.of(new InetSocketAddress(proxy.getHost(), port)));
                log.info("TopCV import uses proxy {}:{}", proxy.getHost(), port);
            } catch (Exception e) {
                log.warn("Ignoring invalid app.topcv.proxy-url '{}': {}", proxyUrl, e.getMessage());
            }
        }
        httpClient = builder.build();
    }

    @Override
    @Transactional
    public TopCvImportResult importJob(String rawUrl) {
        return createJob(prepare(rawUrl));
    }

    /** Tải và bóc tách JD, chưa đụng tới DB — an toàn để chạy song song. */
    private Draft prepare(String rawUrl) {
        URI uri = validateUrl(rawUrl);
        String html = fetch(uri);
        ParsedJd jd = parse(html);

        if (jd.title == null || jd.title.isBlank()) {
            throw new BadRequestException("Không bóc tách được tiêu đề tin tuyển dụng từ URL "
                    + uri.getHost() + " này");
        }
        return new Draft(uri, jd);
    }

    /**
     * Lưu một JD đã bóc tách và đăng ngay (OPEN) để tin xuất hiện trên job board;
     * phải chạy trên thread có SecurityContext.
     */
    private TopCvImportResult createJob(Draft draft) {
        URI uri = draft.uri;
        ParsedJd jd = draft.jd;
        User actor = SecurityUtils.getCurrentUser(userRepository);
        JobDescription job = new JobDescription();
        job.setEmployer(actor);
        job.setTitle(jd.title.trim());
        job.setCompanyName(resolveCompanyName(jd.companyName, uri));
        job.setPosition(jd.position == null || jd.position.isBlank() ? jd.title.trim() : jd.position.trim());
        job.setLocation(jd.location);
        job.setSalaryRange(jd.salaryRange);
        job.setDescription(jd.description);
        job.setRequirements(jd.requirements);
        job.setRequiredSkills(jd.requiredSkills);
        // Admin đã chủ động lấy tin về nên đăng luôn, không qua hàng chờ duyệt.
        job.setStatus(JobStatus.OPEN);
        job.setAiSafetyScore(JdPolicyScreener.screen(job).score());
        job = jobRepository.save(job);

        // Tin được admin chủ động đăng ngay, nên ghi một quyết định APPROVED vào lịch sử
        // kiểm duyệt để màn "Lịch sử kiểm duyệt" cũng thấy các JD đồng bộ này.
        moderationRecordRepository.save(JobModerationRecord.builder()
                .job(job)
                .actor(actor)
                .actorRole(ModerationActorRole.ADMIN)
                .action(ModerationAction.APPROVED)
                .note("Đồng bộ JD IT tự động")
                .processingSeconds(null)
                .build());

        log.info("Imported and published job {} from {}", job.getId(), uri);
        return TopCvImportResult.builder()
                .sourceUrl(uri.toString())
                .jobId(job.getId())
                .title(job.getTitle())
                .companyName(job.getCompanyName())
                .position(job.getPosition())
                .location(job.getLocation())
                .salaryRange(job.getSalaryRange())
                .description(job.getDescription())
                .requirements(job.getRequirements())
                .requiredSkills(job.getRequiredSkills())
                .build();
    }

    @Override
    @Transactional
    public TopCvBatchImportResult importJobs(TopCvBatchImportRequest request) {
        int limit = request.getLimit() <= 0 ? 10 : request.getLimit();
        List<String> targets = new ArrayList<>();
        String sourceUrl = null;

        if (request.getUrls() != null) {
            for (String item : request.getUrls()) {
                if (item != null && !item.isBlank()) {
                    targets.add(item.trim());
                }
            }
        }
        if (request.getUrl() != null && !request.getUrl().isBlank()) {
            sourceUrl = request.getUrl().trim();
            URI uri = validateUrl(sourceUrl);
            if (isJobDetailUrl(uri)) {
                targets.add(0, sourceUrl);
            } else {
                // Trang danh sách/tìm kiếm: lấy các link bài đăng cụ thể trong HTML.
                targets.addAll(discoverJobUrls(uri, limit));
            }
        }

        List<String> uniqueTargets = new ArrayList<>(new LinkedHashSet<>(targets));
        if (uniqueTargets.isEmpty()) {
            throw new BadRequestException("Không tìm thấy tin tuyển dụng nào trong trang danh sách này");
        }
        if (uniqueTargets.size() > limit) {
            uniqueTargets = new ArrayList<>(uniqueTargets.subList(0, limit));
        }

        List<TopCvImportResult> items = new ArrayList<>();
        List<TopCvBatchImportResult.Failure> failures = new ArrayList<>();

        // Tải & bóc tách song song để 20 tin vẫn xong trong vài giây; lưu DB tuần tự.
        List<Callable<Draft>> tasks = new ArrayList<>();
        for (String url : uniqueTargets) {
            tasks.add(() -> prepare(url));
        }
        int threads = Math.min(4, Math.max(1, tasks.size()));
        ExecutorService pool = Executors.newFixedThreadPool(threads, runnable -> {
            Thread thread = new Thread(runnable, "job-import");
            thread.setDaemon(true);
            return thread;
        });
        List<Future<Draft>> futures;
        try {
            futures = pool.invokeAll(tasks);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("Quá trình cào dữ liệu bị gián đoạn");
        } finally {
            pool.shutdownNow();
        }

        for (int i = 0; i < futures.size(); i++) {
            String url = uniqueTargets.get(i);
            try {
                items.add(createJob(futures.get(i).get()));
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                String message = cause instanceof BadRequestException
                        ? cause.getMessage()
                        : "Không cào được tin tuyển dụng này";
                failures.add(TopCvBatchImportResult.Failure.builder()
                        .url(url)
                        .message(message)
                        .build());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failures.add(TopCvBatchImportResult.Failure.builder()
                        .url(url)
                        .message("Bị gián đoạn khi cào tin")
                        .build());
            }
        }

        return TopCvBatchImportResult.builder()
                .sourceUrl(sourceUrl)
                .requested(uniqueTargets.size())
                .imported(items.size())
                .failed(failures.size())
                .items(items)
                .failures(failures)
                .build();
    }

    @Override
    @Transactional
    public TopCvBatchImportResult importDefaultItJobs(int limit) {
        int effective = limit <= 0 ? defaultLimit : Math.min(limit, 50);
        TopCvBatchImportRequest request = new TopCvBatchImportRequest();
        request.setUrl(itListingUrl);
        request.setLimit(effective);
        return importJobs(request);
    }

    @Override
    @Transactional
    public TopCvBatchImportResult importItviecJobs(int limit) {
        int effective = limit <= 0 ? defaultLimit : Math.min(limit, 50);
        TopCvBatchImportRequest request = new TopCvBatchImportRequest();
        request.setUrl(itviecListingUrl);
        request.setLimit(effective);
        return importJobs(request);
    }

    /**
     * Một nút bấm, ưu tiên TopCV; nếu TopCV chặn (Cloudflare 403) thì tự chuyển
     * sang ITviec để vẫn lấy được JD IT. Nguồn thực tế nằm ở {@code sourceUrl}.
     */
    @Override
    @Transactional
    public TopCvBatchImportResult importAutoItJobs(int limit) {
        int effective = limit <= 0 ? defaultLimit : Math.min(limit, 50);
        try {
            TopCvBatchImportResult topCv = importDefaultItJobs(effective);
            if (topCv.getImported() > 0) {
                return topCv;
            }
            log.warn("TopCV import returned no jobs, falling back to ITviec");
        } catch (BadRequestException e) {
            log.warn("TopCV import failed ({}), falling back to ITviec", e.getMessage());
        }
        return importItviecJobs(effective);
    }

    private List<String> discoverJobUrls(URI listingUri, int limit) {
        String html = fetch(listingUri);
        Set<String> urls = new LinkedHashSet<>();
        Matcher matcher = HREF.matcher(html);
        while (matcher.find() && urls.size() < limit) {
            String href = HtmlUtils.htmlUnescape(matcher.group(1)).trim();
            if (href.isEmpty() || href.startsWith("#") || href.startsWith("javascript:")) {
                continue;
            }
            try {
                URI resolved = listingUri.resolve(href);
                if (isJobDetailUrl(resolved)) {
                    urls.add(stripQuery(resolved).toString());
                }
            } catch (IllegalArgumentException ignored) {
                // href rác trên trang — bỏ qua
            }
        }
        return new ArrayList<>(urls);
    }

    private static boolean isJobDetailUrl(URI uri) {
        String host = uri.getHost();
        String path = uri.getPath();
        if (host == null || path == null) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        if (normalized.equals("topcv.vn") || normalized.equals("www.topcv.vn")) {
            return path.startsWith("/viec-lam/") && path.length() > "/viec-lam/".length();
        }
        if (normalized.equals("itviec.com") || normalized.equals("www.itviec.com")) {
            return ITVIEC_JOB_PATH.matcher(path).matches();
        }
        return false;
    }

    private static URI stripQuery(URI uri) {
        try {
            return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null, null);
        } catch (Exception e) {
            return uri;
        }
    }

    private URI validateUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new BadRequestException("URL không được để trống");
        }
        String value = rawUrl.trim();
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("URL không hợp lệ");
        }
        if (uri.getScheme() == null
                || (!uri.getScheme().equalsIgnoreCase("http") && !uri.getScheme().equalsIgnoreCase("https"))) {
            throw new BadRequestException("URL phải bắt đầu bằng http:// hoặc https://");
        }
        String host = uri.getHost();
        if (host == null) {
            throw new BadRequestException("URL không hợp lệ");
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        if (!isAllowedHost(normalized)) {
            throw new BadRequestException("Chỉ nhận URL thuộc tên miền topcv.vn hoặc itviec.com");
        }
        return uri;
    }

    private static boolean isAllowedHost(String host) {
        return host.equals("topcv.vn") || host.equals("www.topcv.vn")
                || host.equals("itviec.com") || host.equals("www.itviec.com");
    }

    private static String resolveCompanyName(String companyName, URI uri) {
        if (companyName != null && !companyName.isBlank()) {
            return companyName.trim();
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        return host.contains("itviec") ? "ITviec" : "TopCV";
    }

    private String fetch(URI uri) {
        int maxAttempts = 3;
        for (int attempt = 1; ; attempt++) {
            try {
                return fetchOnce(uri);
            } catch (RetryableStatusException e) {
                if (attempt >= maxAttempts) {
                    throw new BadRequestException(uri.getHost() + " trả về HTTP " + e.status
                            + " (bị giới hạn tần suất). Thử lại sau hoặc giảm số lượng tin mỗi lần.");
                }
                try {
                    Thread.sleep(500L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new BadRequestException("Bị gián đoạn khi tải trang " + uri.getHost());
                }
            }
        }
    }

    private String fetchOnce(URI uri) {
        URI requestUri = uri;
        if (scraperApiUrl != null && !scraperApiUrl.isBlank()) {
            String encoded = URLEncoder.encode(uri.toString(), StandardCharsets.UTF_8);
            requestUri = URI.create(scraperApiUrl.trim().replace("{url}", encoded));
        }
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(requestUri)
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "vi,en;q=0.9");
        if (cookie != null && !cookie.isBlank()) {
            requestBuilder.header("Cookie", cookie.trim());
        }
        HttpRequest request = requestBuilder.GET().build();
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status == 429 || status == 503) {
                throw new RetryableStatusException(status);
            }
            if (status < 200 || status >= 300) {
                if (status == 403) {
                    throw new BadRequestException(
                            "Trang " + uri.getHost() + " đang chặn truy cập tự động (Cloudflare HTTP 403). "
                                    + "Cấu hình TOPCV_COOKIE (cookie cf_clearance từ trình duyệt), "
                                    + "TOPCV_PROXY_URL (proxy IP khác) hoặc TOPCV_SCRAPER_API_URL "
                                    + "(dịch vụ scraping) rồi thử lại.");
                }
                throw new BadRequestException(uri.getHost() + " trả về HTTP " + status
                        + ", không tải được tin tuyển dụng");
            }
            if (response.body() == null || response.body().isBlank()) {
                throw new BadRequestException("Trang " + uri.getHost() + " trả về nội dung rỗng");
            }
            return response.body();
        } catch (BadRequestException | RetryableStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Cannot fetch URL {}: {}", uri, e.getMessage());
            throw new BadRequestException("Không tải được trang " + uri.getHost()
                    + ". Vui lòng kiểm tra lại link hoặc thử lại sau.");
        }
    }

    /** HTTP 429/503 — tạm thời, có thể thử lại. */
    private static final class RetryableStatusException extends RuntimeException {
        private final int status;

        private RetryableStatusException(int status) {
            super("HTTP " + status);
            this.status = status;
        }
    }

    private ParsedJd parse(String html) {
        ParsedJd jd = parseJsonLd(html);
        if (jd.title != null && !jd.title.isBlank()) {
            fillSkills(jd);
            return jd;
        }

        ParsedJd fallback = new ParsedJd();
        fallback.title = firstNonBlank(
                metaContent(html, "og:title"),
                stripHtml(firstGroup(H1_TAG, html)),
                stripHtml(firstGroup(Pattern.compile("<title[^>]*>(.*?)</title>",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL), html)));
        fallback.companyName = firstNonBlank(metaContent(html, "og:site_name"));
        fallback.description = firstNonBlank(stripHtml(metaContent(html, "og:description")),
                stripHtml(metaContent(html, "description")));
        fillSkills(fallback);
        return fallback;
    }

    private ParsedJd parseJsonLd(String html) {
        Matcher matcher = JSON_LD_SCRIPT.matcher(html);
        while (matcher.find()) {
            String json = matcher.group(1).trim();
            if (json.isEmpty()) {
                continue;
            }
            try {
                JsonNode root = objectMapper.readTree(json);
                JsonNode jobPosting = findJobPosting(root);
                if (jobPosting != null) {
                    return extractJsonLd(jobPosting);
                }
            } catch (Exception e) {
                log.debug("Skip invalid JSON-LD block: {}", e.getMessage());
            }
        }
        return new ParsedJd();
    }

    private JsonNode findJobPosting(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode found = findJobPosting(child);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        if (node.isObject()) {
            if (isJobPosting(typeOf(node.get("@type")))) {
                return node;
            }
            for (JsonNode child : node) {
                JsonNode found = findJobPosting(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static boolean isJobPosting(String type) {
        return type != null && List.of(type.split("[,\\s]+")).stream()
                .anyMatch(t -> t.equalsIgnoreCase("JobPosting"));
    }

    private static String typeOf(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isArray()) {
            List<String> types = new ArrayList<>();
            node.forEach(n -> {
                if (n.isTextual()) {
                    types.add(n.asText());
                }
            });
            return String.join(",", types);
        }
        return node.asText();
    }

    private ParsedJd extractJsonLd(JsonNode node) {
        ParsedJd jd = new ParsedJd();
        jd.title = text(node.get("title"));
        jd.companyName = text(path(node, "hiringOrganization", "name"));
        jd.position = firstNonBlank(text(node.get("occupationalCategory")), jd.title);
        jd.location = locationOf(node.get("jobLocation"));
        jd.salaryRange = salaryOf(node.get("baseSalary"));
        jd.description = stripHtml(text(node.get("description")));
        jd.requirements = joinTexts(
                stripHtml(text(node.get("qualifications"))),
                stripHtml(text(node.get("experienceRequirements"))),
                stripHtml(text(node.get("responsibilities"))),
                stripHtml(text(node.get("educationRequirements"))));

        String skills = text(node.get("skills"));
        if (skills == null || skills.isBlank()) {
            skills = text(node.get("occupationalCategory"));
        }
        jd.requiredSkills = skills;
        return jd;
    }

    private static JsonNode path(JsonNode node, String... fields) {
        JsonNode current = node;
        for (String field : fields) {
            if (current == null) {
                return null;
            }
            current = current.get(field);
        }
        return current;
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            node.forEach(child -> {
                String value = text(child);
                if (value != null && !value.isBlank()) {
                    values.add(value);
                }
            });
            return values.isEmpty() ? null : String.join(", ", values);
        }
        if (node.isValueNode()) {
            return node.asText();
        }
        return null;
    }

    private static String locationOf(JsonNode jobLocation) {
        if (jobLocation == null) {
            return null;
        }
        JsonNode location = jobLocation.isArray() ? jobLocation.get(0) : jobLocation;
        if (location == null) {
            return null;
        }
        JsonNode address = location.get("address");
        if (address == null) {
            return text(location);
        }
        return joinTexts(
                text(address.get("streetAddress")),
                text(address.get("addressLocality")),
                text(address.get("addressRegion")),
                text(address.get("addressCountry")));
    }

    private static String salaryOf(JsonNode baseSalary) {
        if (baseSalary == null) {
            return null;
        }
        if (baseSalary.isValueNode()) {
            return baseSalary.asText();
        }
        JsonNode value = baseSalary.get("value");
        String currency = text(baseSalary.get("currency"));
        String unit = text(value == null ? null : value.get("unitText"));
        if (value != null && value.isObject()) {
            String min = text(value.get("minValue"));
            String max = text(value.get("maxValue"));
            String amount = min != null && max != null && !min.equals(max) ? min + " - " + max : firstNonBlank(max, min);
            if (amount != null) {
                return amount + (currency == null ? "" : " " + currency) + (unit == null ? "" : " / " + unit);
            }
        }
        return text(value);
    }

    private void fillSkills(ParsedJd jd) {
        if (jd.requiredSkills != null && !jd.requiredSkills.isBlank()) {
            jd.requiredSkills = jd.requiredSkills.trim();
            return;
        }
        String haystack = ((jd.title == null ? "" : jd.title) + " "
                + (jd.description == null ? "" : jd.description) + " "
                + (jd.requirements == null ? "" : jd.requirements)).toLowerCase(Locale.ROOT);
        if (haystack.isBlank()) {
            jd.requiredSkills = null;
            return;
        }
        Set<String> found = new LinkedHashSet<>();
        for (String skill : KNOWN_SKILLS) {
            if (containsSkill(haystack, skill)) {
                found.add(skill);
                if (found.size() >= 25) {
                    break;
                }
            }
        }
        jd.requiredSkills = found.isEmpty() ? null : String.join(", ", found);
    }

    private static boolean containsSkill(String haystack, String skill) {
        if (skill.contains(".") || skill.contains("#") || skill.contains("/") || skill.contains("+")) {
            return haystack.contains(skill);
        }
        return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(skill) + "(?![a-z0-9])")
                .matcher(haystack).find();
    }

    private static String joinTexts(String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                parts.add(value.trim());
            }
        }
        return parts.isEmpty() ? null : String.join("\n\n", parts);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String firstGroup(Pattern pattern, String input) {
        if (input == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(input);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String metaContent(String html, String property) {
        if (html == null) {
            return null;
        }
        Matcher matcher = META_TAG.matcher(html);
        while (matcher.find()) {
            String tag = matcher.group();
            String key = attribute(tag, "property");
            if (key == null) {
                key = attribute(tag, "name");
            }
            if (key != null && key.equalsIgnoreCase(property)) {
                return attribute(tag, "content");
            }
        }
        return null;
    }

    private static String attribute(String tag, String name) {
        Matcher matcher = Pattern.compile("\\b" + Pattern.quote(name)
                + "\\s*=\\s*(\"([^\"]*)\"|'([^']*)')", Pattern.CASE_INSENSITIVE).matcher(tag);
        if (matcher.find()) {
            return HtmlUtils.htmlUnescape(firstNonBlank(matcher.group(2), matcher.group(3)));
        }
        return null;
    }

    private static String stripHtml(String value) {
        if (value == null) {
            return null;
        }
        String withoutScripts = value
                .replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ");
        String text = TAG.matcher(withoutScripts).replaceAll("\n");
        text = HtmlUtils.htmlUnescape(text);
        text = text.replace('\u00a0', ' ').replaceAll("[ \\t\\x0B\\f\\r]+", " ");
        text = text.replaceAll(" *\\n *", "\n").replaceAll("\\n{3,}", "\n\n");
        return text.trim();
    }

    private static class ParsedJd {
        private String title;
        private String companyName;
        private String position;
        private String location;
        private String salaryRange;
        private String description;
        private String requirements;
        private String requiredSkills;
    }

    /** JD đã tải & bóc tách, chờ lưu DB. */
    private static final class Draft {
        private final URI uri;
        private final ParsedJd jd;

        private Draft(URI uri, ParsedJd jd) {
            this.uri = uri;
            this.jd = jd;
        }
    }
}
