package com.aiinterview.platform.service.ai;

import com.aiinterview.platform.model.entity.InterviewAnswer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Chấm rubric 5 chiều dự phòng ngay trên BE khi phiên AVATAR kết thúc.
 *
 * <p>Điểm suy ra tất định từ chính transcript đã chốt: số lượt trả lời, độ dài/độ chi tiết,
 * thời lượng và dấu hiệu dẫn chứng dự án / lập luận. Nhờ vậy ứng viên luôn có báo cáo ngay sau
 * khi kết thúc phiên, kể cả khi AI service không sẵn sàng. Rubric do AI service gửi lên vẫn có
 * thể thay thế báo cáo tự động này một lần qua {@code POST /{id}/evaluation}.</p>
 */
@Component
public class AvatarReportEvaluator {

    /** Đánh dấu báo cáo hệ thống tự tạo; rubric AI gửi lên được phép thay thế đúng một lần. */
    public static final String AUTO_RUBRIC_VERSION = "v1-auto-be-2026-11";

    private static final List<String> PROJECT_HINTS = List.of(
            "dự án", "project", "triển khai", "hệ thống", "sản phẩm", "team", "nhóm", "khách hàng");
    private static final List<String> REASONING_HINTS = List.of(
            "vì", "do đó", "nguyên nhân", "giải pháp", "phân tích", "đánh đổi", "trade-off", "kết quả");

    public Evaluation evaluate(List<InterviewAnswer> answers) {
        // Chỉ chấm lượt ứng viên đã chốt; bản nháp STT còn sửa được thì không tính.
        List<InterviewAnswer> confirmed = answers.stream()
                .filter(a -> a.getConfirmedAt() != null)
                .filter(a -> a.getAnswerText() != null && !a.getAnswerText().isBlank())
                .toList();
        int count = confirmed.size();
        List<Integer> words = confirmed.stream().map(a -> wordCount(a.getAnswerText())).toList();
        double avgWords = words.stream().mapToInt(Integer::intValue).average().orElse(0);
        int totalSec = confirmed.stream()
                .mapToInt(a -> a.getDurationSec() == null ? 0 : a.getDurationSec())
                .sum();
        long detailed = words.stream().filter(n -> n >= 25).count();
        double detailRatio = count == 0 ? 0 : (double) detailed / count;
        String joined = confirmed.stream()
                .map(a -> a.getAnswerText().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));

        // 40..85 điểm theo độ sâu câu trả lời; càng dài và chi tiết càng cao.
        int depth = clamp(40 + (Math.min(avgWords, 90) / 90) * 45);
        // 35..85 điểm theo tỷ lệ lượt trả lời đủ chi tiết (>= 25 từ).
        int coverage = clamp(35 + detailRatio * 50);
        boolean hasProject = hasAny(joined, PROJECT_HINTS);
        boolean hasReasoning = hasAny(joined, REASONING_HINTS);

        int clarityBase = avgWords >= 15 && avgWords <= 120 ? 76 : avgWords > 120 ? 66 : 48;
        int clarity = clamp(clarityBase + Math.min(count, 10));

        int chuyenMon = clamp((depth + coverage) / 2.0);
        int duAn = clamp(depth * 0.6 + coverage * 0.4 + (hasProject ? 8 : 0));
        int tuDuy = clamp(depth * 0.6 + coverage * 0.4 + (hasReasoning ? 8 : 0));
        int truyenDat = clarity;
        int mucKhop = clamp(coverage * 0.6 + depth * 0.4 + Math.min(count, 10));

        return new Evaluation(chuyenMon, duAn, tuDuy, truyenDat, mucKhop,
                buildStrengths(count, avgWords, totalSec, detailRatio, hasProject, hasReasoning),
                buildImprovements(count, avgWords, detailRatio, hasProject, hasReasoning),
                AUTO_RUBRIC_VERSION);
    }

    private static String buildStrengths(int count, double avgWords, int totalSec, double detailRatio,
                                         boolean hasProject, boolean hasReasoning) {
        List<String> lines = new ArrayList<>();
        if (count > 0) {
            lines.add("Hoàn thành " + count + " lượt trả lời với tổng thời lượng "
                    + Math.max(1, Math.round(totalSec / 60f)) + " phút.");
        }
        if (detailRatio >= 0.5) {
            lines.add("Phần lớn câu trả lời đủ chi tiết (trung bình ~" + Math.round(avgWords) + " từ).");
        }
        if (hasProject) {
            lines.add("Có dẫn chứng từ dự án/hệ thống thực tế thay vì chỉ nêu lý thuyết.");
        }
        if (hasReasoning) {
            lines.add("Thể hiện cách phân tích nguyên nhân, giải pháp và đánh đổi khi trình bày.");
        }
        if (lines.isEmpty()) {
            lines.add("Đã duy trì được hội thoại với AI trong suốt phiên phỏng vấn.");
        }
        return String.join("\n", lines);
    }

    private static String buildImprovements(int count, double avgWords, double detailRatio,
                                            boolean hasProject, boolean hasReasoning) {
        List<String> lines = new ArrayList<>();
        if (count == 0) {
            lines.add("Chưa ghi nhận lượt trả lời nào; hãy luyện lại và trả lời ít nhất vài câu "
                    + "để báo cáo sát hơn.");
        } else if (avgWords < 25) {
            lines.add("Câu trả lời còn ngắn (trung bình ~" + Math.round(avgWords)
                    + " từ); hãy mở rộng theo STAR và nêu kết quả đo được.");
        }
        if (count > 0 && detailRatio < 0.6) {
            lines.add("Nên bổ sung bối cảnh, hành động cụ thể và kết quả cho từng câu trả lời.");
        }
        if (!hasProject) {
            lines.add("Thêm ví dụ dự án thật: vai trò của bạn, công nghệ dùng và kết quả đạt được.");
        }
        if (!hasReasoning) {
            lines.add("Nêu rõ lý do chọn giải pháp và đánh đổi để thể hiện tư duy giải quyết vấn đề.");
        }
        if (lines.isEmpty()) {
            lines.add("Tiếp tục luyện với câu hỏi khó hơn để duy trì phong độ và độ sâu câu trả lời.");
        }
        return String.join("\n", lines);
    }

    private static int clamp(double value) {
        return (int) Math.round(Math.max(0, Math.min(100, value)));
    }

    private static int wordCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    private static boolean hasAny(String text, List<String> keywords) {
        return keywords.stream().anyMatch(text::contains);
    }

    public record Evaluation(int chuyenMon, int duAn, int tuDuy, int truyenDat, int mucKhop,
                             String strengths, String improvements, String rubricVersion) {

        public int overallScore() {
            return (int) Math.round(
                    (chuyenMon + duAn + tuDuy + truyenDat + mucKhop) / 5.0);
        }
    }
}
