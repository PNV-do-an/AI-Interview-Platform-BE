package com.aiinterview.platform.service.ai;

import com.aiinterview.platform.model.entity.InterviewAnswer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvatarReportEvaluatorTest {

    private final AvatarReportEvaluator evaluator = new AvatarReportEvaluator();

    @Test
    @DisplayName("chỉ chấm lượt đã chốt, bỏ bản nháp và câu trả lời rỗng")
    void confirmedAnswersOnly() {
        List<InterviewAnswer> answers = List.of(
                answer("Em đã triển khai dự án thanh toán cho khách hàng. Em phân tích nguyên nhân "
                        + "và đề xuất giải pháp, kết quả giảm 40% thời gian xử lý. Em cũng phối hợp "
                        + "với nhóm kiểm thử để đo lường kết quả trước và sau khi tối ưu.", 65, true),
                answer("Trong dự án trước, em phụ trách hệ thống đăng nhập. Em phân tích trade-off "
                        + "giữa bảo mật và trải nghiệm, sau đó chọn giải pháp token ngắn hạn và "
                        + "refresh token. Kết quả là giảm đáng kể số phiên bị lỗi.", 70, true),
                answer("Bản nháp chưa chốt", 10, false),
                answer("   ", 5, true));

        AvatarReportEvaluator.Evaluation evaluation = evaluator.evaluate(answers);

        assertTrue(evaluation.strengths().contains("2 lượt trả lời"));
        assertTrue(evaluation.strengths().contains("dự án"));
        assertFalse(evaluation.improvements().isBlank(),
                "báo cáo luôn có ít nhất một góp ý, kể cả khi câu trả lời đã khá đầy đủ");
        assertInRange(evaluation);
        assertEquals(expectedOverall(evaluation), evaluation.overallScore());
        assertEquals(AvatarReportEvaluator.AUTO_RUBRIC_VERSION, evaluation.rubricVersion());
    }

    @Test
    @DisplayName("phiên không có lượt chốt vẫn trả báo cáo hợp lệ, không NaN hay điểm âm")
    void emptyTranscriptStillProducesReport() {
        AvatarReportEvaluator.Evaluation evaluation = evaluator.evaluate(List.of());

        assertInRange(evaluation);
        assertEquals(expectedOverall(evaluation), evaluation.overallScore());
        assertFalse(evaluation.strengths().isBlank());
        assertTrue(evaluation.improvements().contains("Chưa ghi nhận lượt trả lời nào"));
    }

    @Test
    @DisplayName("câu trả lời rất dài vẫn bị chặn ở 100 điểm")
    void scoresNeverExceedHundred() {
        String longAnswer = "dự án giải pháp phân tích đánh đổi kết quả "
                .repeat(200);
        AvatarReportEvaluator.Evaluation evaluation = evaluator.evaluate(
                List.of(answer(longAnswer, 600, true)));

        assertInRange(evaluation);
    }

    private static InterviewAnswer answer(String text, Integer durationSec, boolean confirmed) {
        return InterviewAnswer.builder()
                .questionIndex(0)
                .question("Câu hỏi")
                .answerText(text)
                .durationSec(durationSec)
                .confirmedAt(confirmed ? LocalDateTime.now() : null)
                .build();
    }

    private static int expectedOverall(AvatarReportEvaluator.Evaluation e) {
        return (int) Math.round((e.chuyenMon() + e.duAn() + e.tuDuy() + e.truyenDat() + e.mucKhop()) / 5.0);
    }

    private static void assertInRange(AvatarReportEvaluator.Evaluation e) {
        for (int score : new int[]{e.chuyenMon(), e.duAn(), e.tuDuy(), e.truyenDat(), e.mucKhop(),
                e.overallScore()}) {
            assertTrue(score >= 0 && score <= 100, "điểm phải nằm trong 0..100, actual=" + score);
        }
    }
}
