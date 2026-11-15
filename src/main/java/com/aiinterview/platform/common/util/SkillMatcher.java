package com.aiinterview.platform.common.util;

import java.util.Locale;
import java.util.Map;

/**
 * Chuan hoa ten ky nang de so khop giua CV va catalog vi tri.
 * <p>
 * Dung lai {@link SkillKeys#normalize(String)} (lowercase, gop khoang trang, doi '-'/'_' thanh space,
 * alias chung) roi ap them alias chi dung cho matching. Tach rieng khoi SkillKeys de khong lam thay doi
 * fingerprint cua question cache.
 */
public final class SkillMatcher {

    private static final Map<String, String> MATCH_ALIASES = Map.ofEntries(
            Map.entry("restful api", "rest api"),
            Map.entry("rest", "rest api"),
            Map.entry("amazon web services", "aws"),
            Map.entry("gcp", "google cloud"),
            Map.entry("google cloud platform", "google cloud"),
            Map.entry("ci cd", "ci/cd"),
            Map.entry("unit test", "unit testing"),
            Map.entry("unit tests", "unit testing"),
            Map.entry("spring framework", "spring")
    );

    private SkillMatcher() {
    }

    public static String normalize(String raw) {
        String key = SkillKeys.normalize(raw);
        if (key.isEmpty()) {
            return key;
        }
        String aliased = MATCH_ALIASES.get(key);
        return aliased == null ? key : aliased.toLowerCase(Locale.ROOT);
    }
}
