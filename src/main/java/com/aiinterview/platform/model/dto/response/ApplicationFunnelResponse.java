package com.aiinterview.platform.model.dto.response;

import com.aiinterview.platform.model.enums.ApplicationStatus;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Phễu ứng tuyển (FR-ASM-03). Mỗi bước đếm số đơn *đã từng đạt* bước đó, không đếm trạng thái
 * hiện tại — một đơn đã bị từ chối ở vòng hẹn lịch vẫn tính vào PENDING và SHORTLISTED.
 */
@Data
@Builder
public class ApplicationFunnelResponse {

    private Long jobId;
    private String jobTitle;
    private Long totalApplications;
    private List<Stage> stages;

    @Data
    @Builder
    public static class Stage {
        private ApplicationStatus status;
        /** Số đơn đã đạt bước này. */
        private Long reached;
        /** Số đơn đi tiếp sang bước kế; null ở bước cuối. */
        private Long advanced;
        /** BR-19: advanced / reached; null khi reached = 0 (không bịa số 0). */
        private Double passRate;
    }
}
