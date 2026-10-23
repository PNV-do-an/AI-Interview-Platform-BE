package com.aiinterview.platform.model.dto.request;

import com.aiinterview.platform.model.enums.ApplicationStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EvaluateApplicationRequest {

    @NotNull(message = "Trạng thái không được để trống")
    private ApplicationStatus status;

    @Min(0)
    @Max(100)
    private Integer score;

    private String feedback;

    /** Ghi chú nội bộ cho vòng duyệt, chỉ employer/admin thấy (BR-09). */
    @Size(max = 2000, message = "Ghi chú tối đa 2000 ký tự")
    private String note;
}
