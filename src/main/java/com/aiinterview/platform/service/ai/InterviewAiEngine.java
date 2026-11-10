package com.aiinterview.platform.service.ai;

import com.aiinterview.platform.model.entity.JobDescription;
import com.aiinterview.platform.model.entity.QuizQuestion;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Rule-based interview engine: generates questions from a job's skills and
 * scores free-text answers by keyword coverage, detail level and structure.
 */
@Component
public class InterviewAiEngine {

    private static final List<String> BEHAVIORAL_QUESTIONS = List.of(
            "Hãy giới thiệu ngắn gọn về bản thân và kinh nghiệm liên quan đến vị trí này.",
            "Kể về một dự án khó bạn từng gặp. Bạn đã giải quyết nó như thế nào?",
            "Bạn làm việc nhóm hay làm việc độc lập hiệu quả hơn? Cho ví dụ cụ thể.",
            "Khi deadline gấp và yêu cầu thay đổi liên tục, bạn ưu tiên xử lý thế nào?",
            "Điểm mạnh và điểm cần cải thiện lớn nhất của bạn trong công việc là gì?",
            "Tại sao bạn muốn làm việc tại công ty này và vị trí này?"
    );

    private static final List<String> KNOWLEDGE_TEMPLATES = List.of(
            "Hãy giải thích các khái niệm cốt lõi của {topic} mà bạn nắm vững nhất.",
            "So sánh ưu và nhược điểm giữa các công nghệ/phương pháp phổ biến trong {topic}.",
            "Trong {topic}, bạn thường xử lý lỗi (debug) và tối ưu hiệu năng như thế nào?",
            "Những best practices quan trọng nhất khi làm việc với {topic} là gì?",
            "Mô tả quy trình xây dựng một tính năng hoàn chỉnh bằng {topic} từ đầu đến cuối.",
            "Những câu hỏi phỏng vấn phổ biến về {topic} và cách bạn trả lời chúng?"
    );

    /** Generates multiple-choice questions for a MOCK interview based on the job's required skills. */
    public List<QuizQuestion> generateMockQuestions(JobDescription job, int count) {
        List<QuizQuestion> questions = new ArrayList<>();
        List<String> skills = parseSkills(job.getRequiredSkills());
        String jobTitle = job.getTitle() == null ? "vị trí này" : job.getTitle();

        for (String skill : skills) {
            if (questions.size() >= count) break;
            questions.add(QuizQuestion.of(
                    "Kỹ năng nào sau đây là yêu cầu chính của vị trí " + jobTitle + "?",
                    List.of(skill, "Microsoft Word", "Excel cơ bản", "Gõ bàn phím"),
                    0));
            if (questions.size() < count) {
                questions.add(QuizQuestion.of(
                        "Điều nào sau đây thường là thách thức khi làm việc với " + skill + "?",
                        List.of("Cần hiểu sâu khái niệm và debug", "Không cần học thêm", "Chỉ cần giao diện đẹp", "Không có khó khăn"),
                        0));
            }
        }
        List<String> behavioralQuestions = List.of(
                "Khi làm việc nhóm, điều nào sau đây là best practice?",
                "Khi deadline gấp, bạn nên ưu tiên điều gì?",
                "Điểm mạnh trong công việc nên được thể hiện như thế nào?");
        List<List<String>> behavioralOptions = List.of(
                List.of("Giao tiếp rõ ràng và phân chia nhiệm vụ", "Làm một mình không báo cáo", "Tránh nhận feedback", "Bỏ qua testing"),
                List.of("Ưu tiên task quan trọng và có thể bàn giao sớm", "Làm tất cả cùng lúc", "Bỏ qua review", "Chờ deadline rồi mới bắt đầu"),
                List.of("Bằng ví dụ cụ thể và kết quả đo được", "Chỉ nói chung chung", "Phóng đại thành tích", "Không cần dẫn chứng"));
        for (int i = 0; i < behavioralQuestions.size() && questions.size() < count; i++) {
            questions.add(QuizQuestion.of(behavioralQuestions.get(i), behavioralOptions.get(i), 0));
        }
        if (questions.size() > count) {
            questions = new ArrayList<>(questions.subList(0, count));
        }
        // Đảm bảo đủ số câu bằng câu hỏi tổng quát
        while (questions.size() < count) {
            questions.add(QuizQuestion.of(
                    "Vị trí " + jobTitle + " đòi hỏi điều gì nhất?",
                    List.of("Kỹ năng chuyên môn phù hợp", "Không cần kinh nghiệm", "Chỉ cần ngoại hình", "Không cần làm việc nhóm"),
                    0));
        }
        return questions.subList(0, Math.min(count, questions.size()));
    }

    /** Generates multiple-choice questions for a KNOWLEDGE practice session on a topic. */
    public List<QuizQuestion> generateKnowledgeQuestions(String topic, int count) {
        List<QuizQuestion> questions = new ArrayList<>();
        String t = topic.trim();
        String[] templates = {
                "Khái niệm nào sau đây đúng về {topic}?",
                "Đâu là best practice khi làm việc với {topic}?",
                "Khi debug trong {topic}, bước nào nên làm trước?",
                "Điều nào giúp tối ưu hiệu năng trong {topic}?",
                "Quy trình xây dựng tính năng bằng {topic} bắt đầu từ đâu?",
                "Câu hỏi phỏng vấn nào sau đây liên quan đến {topic}?"
        };
        List<List<String>> optionSets = List.of(
                List.of(t + " là một công nghệ/phương pháp để giải quyết bài toán", "Không liên quan đến phần mềm", "Chỉ dùng để vẽ giao diện", "Chỉ dùng để lưu trữ dữ liệu"),
                List.of("Viết code rõ ràng, có test và review", "Bỏ qua tài liệu", "Không cần version control", "Code càng ngắn càng tốt"),
                List.of("Tái hiện lỗi và đọc log", "Đoán mò và sửa ngẫu nhiên", "Xóa toàn bộ code", "Bỏ qua vì không quan trọng"),
                List.of("Đo lường trước, tối ưu chỗ nghẽn", "Tối ưu tất cả mọi nơi", "Dùng thuật toán phức tạp nhất", "Bỏ qua cache"),
                List.of("Thu thập yêu cầu và thiết kế giải pháp", "Viết code ngay không plan", "Bỏ qua kiểm thử", "Triển khai rồi mới thiết kế"),
                List.of("Giải thích khái niệm cốt lõi và ví dụ thực tế", "Chỉ trả lời một từ", "Đổi chủ đề", "Từ chối trả lời")
        );
        for (int i = 0; i < templates.length && questions.size() < count; i++) {
            questions.add(QuizQuestion.of(
                    templates[i].replace("{topic}", t),
                    optionSets.get(i),
                    0));
        }
        while (questions.size() < count) {
            questions.add(QuizQuestion.of(
                    "Điều nào sau đây là đúng về " + t + "?",
                    List.of(t + " có các khái niệm và best practices riêng", "Không cần học", "Chỉ dành cho chuyên gia", "Không có ứng dụng thực tế"),
                    0));
        }
        return questions.subList(0, Math.min(count, questions.size()));
    }

    /** Extracts scoring keywords from job data for MOCK mode. */
    public Set<String> extractKeywords(JobDescription job, String question) {
        Set<String> keywords = new LinkedHashSet<>();
        keywords.addAll(parseSkills(job.getRequiredSkills()).stream()
                .map(String::toLowerCase).collect(Collectors.toList()));
        addLongWords(keywords, job.getTitle());
        addLongWords(keywords, job.getRequirements());
        addLongWords(keywords, job.getPosition());
        addLongWords(keywords, question);
        return keywords;
    }

    /** Extracts scoring keywords from topic for KNOWLEDGE mode. */
    public Set<String> extractKeywords(String topic, String question) {
        Set<String> keywords = new LinkedHashSet<>();
        addLongWords(keywords, topic);
        addLongWords(keywords, question);
        return keywords;
    }

    public ScoreResult evaluate(String answerText, Set<String> keywords) {
        String normalized = answerText.toLowerCase();
        Set<String> matched = new LinkedHashSet<>();
        Set<String> missing = new LinkedHashSet<>();
        for (String k : keywords) {
            if (normalized.contains(k.toLowerCase())) {
                matched.add(k);
            } else {
                missing.add(k);
            }
        }

        int wordCount = normalized.trim().isEmpty() ? 0 : normalized.trim().split("\\s+").length;

        // Keyword coverage: up to 60 points
        double coverage = keywords.isEmpty() ? 0.5 : (double) matched.size() / keywords.size();
        int keywordPoints = (int) Math.round(coverage * 60);

        // Detail level: full 30 points at >= 60 words, scaled below
        int detailPoints = Math.min(30, wordCount * 30 / 60);

        // Structure bonus: concrete example markers
        boolean hasExample = normalized.contains("ví dụ") || normalized.contains("vd:")
                || normalized.contains("dự án") || normalized.contains("project");
        int bonus = hasExample ? 10 : 0;

        int score = Math.min(100, keywordPoints + detailPoints + bonus);
        return new ScoreResult(score, buildFeedback(wordCount, matched, missing, hasExample));
    }

    private String buildFeedback(int wordCount, Set<String> matched, Set<String> missing, boolean hasExample) {
        StringBuilder fb = new StringBuilder();
        if (wordCount < 20) {
            fb.append("Câu trả lời còn ngắn (").append(wordCount)
              .append(" từ) — cần trình bày chi tiết hơn, khoảng 60 từ trở lên. ");
        } else {
            fb.append("Mức độ chi tiết tốt (").append(wordCount).append(" từ). ");
        }
        if (!matched.isEmpty()) {
            fb.append("Điểm mạnh: đề cập ").append(String.join(", ", matched)).append(". ");
        }
        if (!missing.isEmpty()) {
            List<String> topMissing = missing.stream().limit(5).collect(Collectors.toList());
            fb.append("Nên bổ sung: ").append(String.join(", ", topMissing)).append(". ");
        }
        fb.append(hasExample
                ? "Có ví dụ minh họa cụ thể — điểm cộng."
                : "Nên thêm ví dụ thực tế (dự án, tình huống) để câu trả lời thuyết phục hơn.");
        return fb.toString();
    }

    private List<String> parseSkills(String requiredSkills) {
        if (requiredSkills == null || requiredSkills.isBlank()) {
            return List.of();
        }
        return Arrays.stream(requiredSkills.split("[,;\\n]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private void addLongWords(Set<String> keywords, String text) {
        if (text == null) return;
        for (String w : text.toLowerCase().split("[^a-z0-9à-ỹ+#.]+")) {
            if (w.length() >= 4) {
                keywords.add(w);
            }
        }
    }

    public record ScoreResult(int score, String feedback) {
    }
}
