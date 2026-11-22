package com.aiinterview.platform.service.ai;

import com.aiinterview.platform.model.entity.InterviewAnswer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chấm báo cáo phỏng vấn kiểu "rubric tự động" — không gọi AI, chỉ đếm và đo văn bản.
 * Bất biến quan trọng: điểm luôn nằm trong 0..100, không NaN, và báo cáo rỗng vẫn phải
 * có nội dung để FE hiển thị thay vì màn hình trắng.
 *
 * <p>Mỗi test chia 3 khối comment: <b>Arrange</b> → <b>Act</b> → <b>Assert</b>.</p>
 */
class AvatarReportEvaluatorTest {

    private final AvatarReportEvaluator evaluator = new AvatarReportEvaluator();

    @Test
    @DisplayName("chỉ chấm lượt đã chốt, bỏ bản nháp và câu trả lời rỗng")
    void confirmedAnswersOnly() {
        // Arrange: 4 lượt — 2 lượt đã chốt và có nội dung, 1 bản nháp, 1 câu trả lời chỉ có khoảng trắng
        List<InterviewAnswer> answers = List.of(
                answer("Em đã triển khai dự án thanh toán cho khách hàng. Em phân tích nguyên nhân "
                        + "và đề xuất giải pháp, kết quả giảm 40% thời gian xử lý. Em cũng phối hợp "
                        + "với nhóm kiểm thử để đo lường kết quả trước và sau khi tối ưu.", 65, true),
                answer("Trong dự án trước, em phụ trách hệ thống đăng nhập. Em phân tích trade-off "
                        + "giữa bảo mật và trải nghiệm, sau đó chọn giải pháp token ngắn hạn và "
                        + "refresh token. Kết quả là giảm đáng kể số phiên bị lỗi.", 70, true),
                answer("Bản nháp chưa chốt", 10, false),
                answer("   ", 5, true));

        // Act
        AvatarReportEvaluator.Evaluation evaluation = evaluator.evaluate(answers);

        // Assert: chỉ 2 lượt hợp lệ được tính (nháp và rỗng bị loại)
        assertTrue(evaluation.strengths().contains("2 lượt trả lời"));
        assertTrue(evaluation.strengths().contains("dự án"));

        // Assert: dù câu trả lời đã khá đầy đủ, báo cáo vẫn phải có ít nhất một góp ý
        assertFalse(evaluation.improvements().isBlank(),
                "báo cáo luôn có ít nhất một góp ý, kể cả khi câu trả lời đã khá đầy đủ");

        // Assert: điểm tổng = trung bình 5 tiêu chí (làm tròn), luôn trong 0..100
        assertInRange(evaluation);
        assertEquals(expectedOverall(evaluation), evaluation.overallScore());
        assertEquals(AvatarReportEvaluator.AUTO_RUBRIC_VERSION, evaluation.rubricVersion());
    }

    @Test
    @DisplayName("phiên không có lượt chốt vẫn trả báo cáo hợp lệ, không NaN hay điểm âm")
    void emptyTranscriptStillProducesReport() {
        // Arrange: phiên chưa có lượt trả lời nào

        // Act
        AvatarReportEvaluator.Evaluation evaluation = evaluator.evaluate(List.of());

        // Assert: không chia cho 0 — mọi điểm trong 0..100, điểm tổng vẫn là trung bình 5 tiêu chí
        assertInRange(evaluation);
        assertEquals(expectedOverall(evaluation), evaluation.overallScore());

        // Assert: màn hình rỗng không được rỗng thật — vẫn có điểm mạnh và góp ý cụ thể
        assertFalse(evaluation.strengths().isBlank());
        assertTrue(evaluation.improvements().contains("Chưa ghi nhận lượt trả lời nào"));
    }

    @Test
    @DisplayName("câu trả lời rất dài vẫn bị chặn ở 100 điểm")
    void scoresNeverExceedHundred() {
        // Arrange: một câu trả lời lặp từ khoá "ăn điểm" 200 lần và dài 600 giây
        String longAnswer = "dự án giải pháp phân tích đánh đổi kết quả "
                .repeat(200);

        // Act
        AvatarReportEvaluator.Evaluation evaluation = evaluator.evaluate(
                List.of(answer(longAnswer, 600, true)));

        // Assert: cộng điểm theo từ khoá không được vượt trần 100
        assertInRange(evaluation);
    }

    /** Tạo một lượt trả lời; confirmed=false nghĩa là confirmedAt=null, tức bản nháp chưa chốt. */
    private static InterviewAnswer answer(String text, Integer durationSec, boolean confirmed) {
        return InterviewAnswer.builder()
                .questionIndex(0)
                .question("Câu hỏi")
                .answerText(text)
                .durationSec(durationSec)
                .confirmedAt(confirmed ? LocalDateTime.now() : null)
                .build();
    }

    /** Điểm tổng theo đúng công thức của evaluator: trung bình 5 tiêu chí, làm tròn. */
    private static int expectedOverall(AvatarReportEvaluator.Evaluation e) {
        return (int) Math.round((e.chuyenMon() + e.duAn() + e.tuDuy() + e.truyenDat() + e.mucKhop()) / 5.0);
    }

    /** Bất biến áp cho mọi báo cáo: 5 tiêu chí và điểm tổng đều nằm trong 0..100. */
    private static void assertInRange(AvatarReportEvaluator.Evaluation e) {
        for (int score : new int[]{e.chuyenMon(), e.duAn(), e.tuDuy(), e.truyenDat(), e.mucKhop(),
                e.overallScore()}) {
            assertTrue(score >= 0 && score <= 100, "điểm phải nằm trong 0..100, actual=" + score);
        }
    }
}
