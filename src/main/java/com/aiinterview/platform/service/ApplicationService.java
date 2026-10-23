package com.aiinterview.platform.service;

import com.aiinterview.platform.model.dto.request.ApplyJobRequest;
import com.aiinterview.platform.model.dto.request.BookSlotRequest;
import com.aiinterview.platform.model.dto.request.EvaluateApplicationRequest;
import com.aiinterview.platform.model.dto.response.ApplicationDetailResponse;
import com.aiinterview.platform.model.dto.response.ApplicationFunnelResponse;
import com.aiinterview.platform.model.dto.response.ApplicationResponse;
import org.springframework.data.domain.Page;

public interface ApplicationService {

    ApplicationResponse apply(Long jobId, ApplyJobRequest request);

    Page<ApplicationResponse> getMyApplications(int page, int size);

    void cancelApplication(Long applicationId);

    Page<ApplicationResponse> getJobApplications(Long jobId, int page, int size);

    ApplicationResponse evaluate(Long applicationId, EvaluateApplicationRequest request);

    ApplicationResponse bookSlot(BookSlotRequest request);

    /** FR-SCH-02: hủy riêng lịch đã đặt, đơn quay về chờ xếp lịch, khung giờ mở lại cho người khác. */
    ApplicationResponse unschedule(Long applicationId);

    /** FR-SCH-02: đổi sang khung giờ khác còn trống của cùng người phỏng vấn, vẫn ở vòng SCHEDULED. */
    ApplicationResponse reschedule(Long applicationId, Long slotId);

    Page<ApplicationResponse> adminListApplications(int page, int size);

    /** Chi tiết đơn ứng tuyển + timeline trạng thái (FR-APP-02, BR-09 lọc ghi chú nội bộ). */
    ApplicationDetailResponse getApplicationDetail(Long applicationId);

    /** Phễu ứng tuyển theo từng vòng của một tin (FR-ASM-03). */
    ApplicationFunnelResponse getApplicationFunnel(Long jobId);
}
