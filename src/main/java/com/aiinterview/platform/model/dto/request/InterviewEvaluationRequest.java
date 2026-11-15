package com.aiinterview.platform.model.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class InterviewEvaluationRequest {

    /**
     * Đúng 5 chiều: chuyenMon, duAn, tuDuy, truyenDat, mucKhop — mỗi chiều integer 0..100.
     * Để là Map thay vì 5 field để BE phân biệt được "thiếu chiều" với "thừa chiều" và
     * "giá trị không nguyên" với chuỗi; mỗi sai đều 400 kèm danh sách chiều.
     */
    private Map<String, Object> rubric;

    /** Bắt buộc: sau này đổi thang chấm phải ghi version mới, dữ liệu cũ đọc lại vẫn đúng. */
    @NotBlank(message = "rubricVersion không được để trống")
    private String rubricVersion;

    // Cột là TEXT (65.535 byte) nên phải chặn từ lúc ghi: payload quá dài sẽ chết ở DB, trả 500.
    @Size(max = 4000, message = "strengths tối đa 4000 ký tự")
    private String strengths;

    @Size(max = 4000, message = "improvements tối đa 4000 ký tự")
    private String improvements;

    /**
     * Nhận xét từng câu do AI service chấm từ transcript thật. BE ghi vào
     * interview_answers.feedback/score để trang báo cáo không phải tự bịa nhận xét.
     */
    @Valid
    private List<AnswerEvaluation> answers;

    @Data
    public static class AnswerEvaluation {

        /** 0-based, khớp questionIndex của interview_answers. */
        @NotNull(message = "index của câu trả lời không được để trống")
        @Min(value = 0, message = "index của câu trả lời phải >= 0")
        private Integer index;

        @Min(value = 0, message = "score phải >= 0")
        @Max(value = 100, message = "score phải <= 100")
        private Integer score;

        @Size(max = 2000, message = "feedback tối đa 2000 ký tự")
        private String feedback;
    }
}
