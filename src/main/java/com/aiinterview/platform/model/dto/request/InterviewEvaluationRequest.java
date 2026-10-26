package com.aiinterview.platform.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class InterviewEvaluationRequest {

    /**
     * Đúng 5 chiều: chuyenMon, duAn, tuDuy, truyenDat, mucKhop — mỗi chiều integer 0..100.
     * Để là Map thay vì 5 field để BE phân biệt được "thiếu chiều" với "thừa chiều" và
     * "giá trị không nguyên" với chuỗi; mọi sai đều 400 kèm danh sách chiều.
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
}
