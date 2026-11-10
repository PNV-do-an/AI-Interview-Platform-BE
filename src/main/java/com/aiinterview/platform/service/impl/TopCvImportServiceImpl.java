package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.model.dto.response.TopCvImportResult;
import com.aiinterview.platform.model.entity.JobDescription;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.JobStatus;
import com.aiinterview.platform.model.repository.JobDescriptionRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.security.utils.SecurityUtils;
import com.aiinterview.platform.service.TopCvImportService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.net.URI;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Cào JD từ một URL bài đăng TopCV rồi lưu thành bản nháp để admin kiểm duyệt. */
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
    private final UserRepository userRepository;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    @Transactional
    public TopCvImportResult importJob(String rawUrl) {
        URI uri = validateUrl(rawUrl);
        String html = fetch(uri);
        ParsedJd jd = parse(html);

        if (jd.title == null || jd.title.isBlank()) {
            throw new BadRequestException("Không bóc tách được tiêu đề tin tuyển dụng từ URL TopCV này");
        }

        User actor = SecurityUtils.getCurrentUser(userRepository);
        JobDescription job = new JobDescription();
        job.setEmployer(actor);
        job.setTitle(jd.title.trim());
        job.setCompanyName(jd.companyName == null || jd.companyName.isBlank() ? "TopCV" : jd.companyName.trim());
        job.setPosition(jd.position == null || jd.position.isBlank() ? jd.title.trim() : jd.position.trim());
        job.setLocation(jd.location);
        job.setSalaryRange(jd.salaryRange);
        job.setDescription(jd.description);
        job.setRequirements(jd.requirements);
        job.setRequiredSkills(jd.requiredSkills);
        job.setStatus(JobStatus.DRAFT);
        job = jobRepository.save(job);

        log.info("Imported TopCV JD into draft job {} from {}", job.getId(), uri);
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
        if (!normalized.equals("topcv.vn") && !normalized.equals("www.topcv.vn")) {
            throw new BadRequestException("Chỉ nhận URL thuộc tên miền topcv.vn");
        }
        return uri;
    }

    private String fetch(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "vi,en;q=0.9")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new BadRequestException("TopCV trả về HTTP " + status + ", không tải được tin tuyển dụng");
            }
            if (response.body() == null || response.body().isBlank()) {
                throw new BadRequestException("Trang TopCV trả về nội dung rỗng");
            }
            return response.body();
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Cannot fetch TopCV URL {}: {}", uri, e.getMessage());
            throw new BadRequestException("Không tải được trang TopCV. Vui lòng kiểm tra lại link hoặc thử lại sau.");
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
}
