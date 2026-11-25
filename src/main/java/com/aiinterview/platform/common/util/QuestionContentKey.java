package com.aiinterview.platform.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Sinh khoá chống trùng ngữ nghĩa cho câu hỏi (spec 2026-11-24 §5.1).
 *
 * <p>Cùng một ý hỏi nhưng diễn đạt khác nhau phải ra cùng khoá, nên phép chuẩn hoá là:
 * lowercase → bỏ dấu câu → tách token → bỏ stopword → thay synonym → sort → join → sha256.
 *
 * <p>Ràng buộc quan trọng: từ phủ định và từ so sánh TUYỆT ĐỐI không được coi là stopword.
 * "When should you use X?" và "When should you NOT use X?" chỉ khác nhau một token; bỏ
 * {@code not} sẽ gộp hai câu trái nghĩa thành một khoá.
 *
 * <p>Lớp không phụ thuộc Spring để test được thuần.
 */
public final class QuestionContentKey {

    /**
     * Từ đồng nghĩa: token gốc → token chuẩn. Không khai báo ánh xạ no-op (ví dụ compare → compare):
     * token chuẩn tự nó đã giữ nguyên.
     *
     * <p>Mọi động từ dẫn câu hỏi đều quy về "describe" chứ KHÔNG bỏ: nếu bỏ thì "List the advantages"
     * mất "list" trong khi "What are the benefits" giữ "describe", và hai câu cùng ý sẽ lệch khoá.
     * "name" cố ý không nằm trong bảng vì nó còn là danh từ mang nghĩa (tên bản ghi, tên trường).
     */
    private static final Map<String, String> SYNONYMS = Map.ofEntries(
            Map.entry("define", "describe"),
            Map.entry("explain", "describe"),
            Map.entry("state", "describe"),
            Map.entry("what's", "describe"),
            Map.entry("list", "describe"),
            Map.entry("give", "describe"),
            Map.entry("provide", "describe"),
            Map.entry("tell", "describe"),
            Map.entry("mention", "describe"),
            Map.entry("enumerate", "describe"),
            Map.entry("advantage", "pro"),
            Map.entry("benefit", "pro"),
            Map.entry("strength", "pro"),
            Map.entry("disadvantage", "con"),
            Map.entry("drawback", "con"),
            Map.entry("limitation", "con"),
            Map.entry("weakness", "con"),
            Map.entry("comparison", "compare"),
            Map.entry("difference", "compare"),
            Map.entry("differ", "compare"),
            Map.entry("compromise", "tradeoff"));

    /**
     * Cụm nhiều từ phải thay TRƯỚC khi tách token, vì "trade-off" và "tradeoff" là hai cách
     * viết của một khái niệm. Thứ tự quan trọng: cụm dài phải đứng trước cụm ngắn.
     */
    private static final Map<String, String> PHRASES = buildPhrases();

    /**
     * Từ chức năng: bỏ đi để hai cách diễn đạt cùng ý ra cùng khoá.
     * Trị tuyệt đối không chứa bất kỳ từ nào trong {@link #PROTECTED} — xem test bất biến.
     */
    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "of", "to", "in", "on", "at", "by", "for", "from", "with",
            "into", "about", "as", "and", "or", "but", "if", "then", "than",
            "is", "are", "was", "were", "be", "been", "being", "am",
            "do", "does", "did", "done", "has", "have", "had", "having",
            "will", "would", "should", "could", "can", "may", "might", "must", "shall",
            "i", "me", "my", "we", "us", "our", "you", "your", "he", "she", "it", "its",
            "they", "them", "their", "this", "that", "these", "those", "there", "here",
            "what", "how",
            "please", "briefly", "one", "ones");

    /**
     * Từ bảo vệ: mang nghĩa phủ định, so sánh hoặc hướng biến thiên nên không được bỏ.
     * Tập này được kiểm tra TRƯỚC stopword trong {@link #of} nên nó thực sự chặn việc bỏ từ,
     * không chỉ là tài liệu. Test vẫn kiểm tra thêm nó không chồng lấn {@link #STOPWORDS}.
     */
    private static final Set<String> PROTECTED = Set.of(
            "not", "no", "never", "without", "cannot", "can't", "shouldn't",
            "versus", "vs", "more", "less", "before", "after", "best", "worst");

    private QuestionContentKey() {
    }

    /**
     * Khoá nội dung của một câu hỏi: 64 ký tự hex, hoặc {@code null} khi câu rỗng.
     *
     * <p>{@code null} nghĩa là "không sinh được khoá" — cột {@code question.content_key} để NULL
     * và bản ghi đó không tham gia chống trùng, thay vì gán một khoá rỗng dùng chung.
     */
    public static String of(String questionText) {
        if (questionText == null || questionText.isBlank()) {
            return null;
        }
        List<String> tokens = tokenize(normalize(questionText));
        if (tokens.isEmpty()) {
            // Câu không còn ký tự ASCII sau khi bỏ dấu câu (ví dụ viết hoàn toàn bằng tiếng Việt
            // có dấu). Rơi về hash của chính câu gốc để không sinh khoá rỗng trùng nhau.
            return sha256(questionText.trim().toLowerCase(Locale.ROOT));
        }
        List<String> kept = new ArrayList<>();
        for (String token : tokens) {
            String canonical = canonical(token);
            // Từ bảo vệ được ưu tiên trước stopword: kể cả khi ai đó thêm nhầm "not" vào
            // STOPWORDS thì câu trái nghĩa vẫn không bị gộp. Đây là hàng rào thật, không phải
            // danh sách chỉ để đọc.
            if (PROTECTED.contains(canonical) || !STOPWORDS.contains(canonical)) {
                kept.add(canonical);
            }
        }
        // Câu toàn từ chức năng: giữ lại token gốc, vì bỏ hết sẽ biến mọi câu dạng
        // "What is it?" thành cùng một khoá.
        if (kept.isEmpty()) {
            kept = tokens;
        }
        Collections.sort(kept);
        return sha256(String.join(" ", kept));
    }

    /** Chỉ dùng cho test bất biến: từ bảo vệ không được nằm trong danh sách stopword. */
    static Set<String> stopwords() {
        return STOPWORDS;
    }

    static Set<String> protectedWords() {
        return PROTECTED;
    }

    private static Map<String, String> buildPhrases() {
        Map<String, String> phrases = new LinkedHashMap<>();
        phrases.put("what is", "describe");
        phrases.put("what are", "describe");
        phrases.put("trade offs", "tradeoff");
        phrases.put("trade off", "tradeoff");
        return Collections.unmodifiableMap(phrases);
    }

    private static String normalize(String raw) {
        String text = raw.toLowerCase(Locale.ROOT)
                .replace('\u2019', '\'')
                .replace('\u2018', '\'')
                .replaceAll("[^a-z0-9']", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (text.isEmpty()) {
            return text;
        }
        // Đệm khoảng trắng hai đầu để mọi cụm đều khớp được ở cả đầu và cuối câu.
        String padded = " " + text + " ";
        for (Map.Entry<String, String> phrase : PHRASES.entrySet()) {
            padded = padded.replace(" " + phrase.getKey() + " ", " " + phrase.getValue() + " ");
        }
        return padded.replaceAll("\\s+", " ").trim();
    }

    private static List<String> tokenize(String normalized) {
        if (normalized.isEmpty()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String token : normalized.split(" ")) {
            String trimmed = trimApostrophes(token);
            if (!trimmed.isEmpty()) {
                tokens.add(trimmed);
            }
        }
        return tokens;
    }

    private static String trimApostrophes(String token) {
        int start = 0;
        int end = token.length();
        while (start < end && token.charAt(start) == '\'') {
            start++;
        }
        while (end > start && token.charAt(end - 1) == '\'') {
            end--;
        }
        return token.substring(start, end);
    }

    /** Tra synonym, có thử dạng số nhiều: advantages → advantage, weaknesses → weakness. */
    private static String canonical(String token) {
        String exact = SYNONYMS.get(token);
        if (exact != null) {
            return exact;
        }
        if (token.endsWith("s")) {
            String single = SYNONYMS.get(token.substring(0, token.length() - 1));
            if (single != null) {
                return single;
            }
        }
        if (token.endsWith("es")) {
            String single = SYNONYMS.get(token.substring(0, token.length() - 2));
            if (single != null) {
                return single;
            }
        }
        return token;
    }

    private static String sha256(String base) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(base.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
