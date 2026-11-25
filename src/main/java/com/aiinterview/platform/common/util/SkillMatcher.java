package com.aiinterview.platform.common.util;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Chuẩn hóa tên kỹ năng để so khớp giữa CV và catalog vị trí.
 * <p>
 * Dùng lại {@link SkillKeys#normalize(String)} (lowercase, gộp khoảng trắng, đổi '-'/'_' thành space,
 * alias chung) rồi áp thêm: bỏ hậu tố phiên bản ("Java 17" -> "java"), alias chỉ dùng cho matching,
 * chuẩn hóa danh từ số nhiều về số ít ("RESTful APIs" -> "rest api") và tách kỹ năng viết gộp
 * ("HTML/CSS" -> "html", "css"). Tách riêng khỏi SkillKeys để không làm thay đổi fingerprint của
 * question cache.
 */
public final class SkillMatcher {

    /** Cụm có chứa dấu phân cách nhưng là một kỹ năng duy nhất - không được tách ra. */
    private static final Set<String> COMPOUND_TERMS = Set.of("ci/cd", "ui/ux", "ai/ml");

    /** Từ không phải danh từ số nhiều dù kết thúc bằng 's'. */
    private static final Set<String> SINGULAR_GUARD = Set.of(
            "aws", "css", "less", "sass", "ios", "redis", "express", "jenkins", "kubernetes", "devops", "mlops",
            "analysis", "basis", "crisis");

    private static final Map<String, String> MATCH_ALIASES = buildAliases();

    /** Hậu tố phiên bản: "java 17", "spring boot 3", "rest api 2.0". */
    private static final Pattern VERSION_SUFFIX = Pattern.compile("\\s+\\d+(\\.\\d+)*$");

    private static final Pattern TRAILING_PUNCT = Pattern.compile("[\\s.,;:!?]+$");

    /** Kỹ năng viết gộp: "HTML/CSS", "React, Redux", "Java & Spring". */
    private static final Pattern COMPOUND_SPLIT = Pattern.compile("[/&,|]");

    /** Chỉ chuẩn hóa số ít khi từ cuối gồm toàn chữ cái (giữ "node.js", "c#", "asp.net" nguyên vẹn). */
    private static final Pattern LETTERS_ONLY = Pattern.compile("[a-z]+");

    /** Chặn vòng lặp alias -> số ít -> alias. */
    private static final int MAX_ALIAS_PASSES = 3;

    private SkillMatcher() {
    }

    /**
     * Khóa chuẩn hóa duy nhất của một tên kỹ năng.
     * Đây cũng là giá trị ghi vào cột {@code position_required_skill.skill_key}.
     */
    public static String normalize(String raw) {
        String key = SkillKeys.normalize(raw);
        if (key.isEmpty()) {
            return key;
        }
        key = VERSION_SUFFIX.matcher(TRAILING_PUNCT.matcher(key).replaceAll("")).replaceAll("");
        for (int pass = 0; pass < MAX_ALIAS_PASSES; pass++) {
            String aliased = MATCH_ALIASES.get(key);
            if (aliased != null) {
                key = aliased;
                continue;
            }
            String singular = singularize(key);
            if (singular.equals(key)) {
                break;
            }
            key = singular;
        }
        return key;
    }

    /**
     * Tất cả khóa dùng để so khớp của một tên kỹ năng: khóa chuẩn hóa + các phần của kỹ năng viết gộp.
     * VD {@code "HTML/CSS"} -> {@code [html/css, html, css]}.
     */
    public static Set<String> keys(String raw) {
        String canonical = normalize(raw);
        if (canonical.isEmpty()) {
            return Set.of();
        }
        Set<String> keys = new LinkedHashSet<>();
        keys.add(canonical);
        if (COMPOUND_TERMS.contains(canonical) || !COMPOUND_SPLIT.matcher(canonical).find()) {
            return keys;
        }
        for (String part : COMPOUND_SPLIT.split(canonical)) {
            String partKey = normalize(part);
            if (!partKey.isEmpty()) {
                keys.add(partKey);
            }
        }
        return keys;
    }

    /** True nếu một trong các tên ứng viên (skill_key / skill_name) có khóa nằm trong tập khóa của CV. */
    public static boolean matches(Set<String> cvKeys, String... candidates) {
        if (cvKeys == null || cvKeys.isEmpty() || candidates == null) {
            return false;
        }
        for (String candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            for (String key : keys(candidate)) {
                if (cvKeys.contains(key)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Chỉ chuẩn hóa từ cuối cùng; từ không phải danh từ số nhiều được giữ nguyên. */
    private static String singularize(String key) {
        int space = key.lastIndexOf(' ');
        String head = space < 0 ? "" : key.substring(0, space + 1);
        String last = space < 0 ? key : key.substring(space + 1);
        if (last.length() < 4 || SINGULAR_GUARD.contains(last) || !LETTERS_ONLY.matcher(last).matches()
                || !last.endsWith("s")) {
            return key;
        }
        if (last.endsWith("ss") || last.endsWith("us") || last.endsWith("ous")) {
            return key;
        }
        if (last.endsWith("sses")) {
            return head + last.substring(0, last.length() - 2);
        }
        if (last.endsWith("ies")) {
            return head + last.substring(0, last.length() - 3) + "y";
        }
        return head + last.substring(0, last.length() - 1);
    }

    private static Map<String, String> buildAliases() {
        Map<String, String> aliases = new HashMap<>();
        // REST / API
        aliases.put("restful api", "rest api");
        aliases.put("restful", "rest api");
        aliases.put("rest", "rest api");
        aliases.put("rest service", "rest api");
        aliases.put("restful service", "rest api");
        // Node / Spring
        aliases.put("nodejs", "node.js");
        aliases.put("node js", "node.js");
        aliases.put("node", "node.js");
        aliases.put("spring framework", "spring");
        aliases.put("spring mvc", "spring");
        // Database
        aliases.put("mongo", "mongodb");
        aliases.put("mssql", "sql server");
        aliases.put("ms sql", "sql server");
        aliases.put("microsoft sql server", "sql server");
        // Cloud
        aliases.put("amazon web services", "aws");
        aliases.put("amazon web service", "aws");
        aliases.put("amazon aws", "aws");
        aliases.put("gcp", "google cloud");
        aliases.put("google cloud platform", "google cloud");
        aliases.put("microsoft azure", "azure");
        // DevOps / CI
        aliases.put("ci cd", "ci/cd");
        aliases.put("cicd", "ci/cd");
        aliases.put("continuous integration", "ci/cd");
        aliases.put("containerization", "docker");
        aliases.put("containerisation", "docker");
        // Frontend
        aliases.put("react js", "react");
        aliases.put("reactjs", "react");
        aliases.put("react.js", "react");
        aliases.put("vue js", "vue");
        aliases.put("vuejs", "vue");
        aliases.put("angularjs", "angular");
        aliases.put("next js", "next.js");
        aliases.put("nextjs", "next.js");
        aliases.put("html5", "html");
        aliases.put("css3", "css");
        aliases.put("tailwind css", "tailwind");
        aliases.put("ecmascript", "javascript");
        aliases.put("es6", "javascript");
        // Ngôn ngữ
        aliases.put("golang", "go");
        aliases.put("c sharp", "c#");
        aliases.put("csharp", "c#");
        aliases.put("c plus plus", "c++");
        aliases.put("cpp", "c++");
        aliases.put("dotnet", ".net");
        aliases.put("net core", ".net");
        aliases.put(".net core", ".net");
        // Testing
        aliases.put("unit test", "unit testing");
        aliases.put("selenium webdriver", "selenium");
        return Map.copyOf(aliases);
    }
}
