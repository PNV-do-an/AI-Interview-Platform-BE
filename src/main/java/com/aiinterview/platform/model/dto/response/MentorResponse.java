package com.aiinterview.platform.model.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * Thông tin hiển thị công khai của một mentor (nha tuyen dung / chuyen gia)
 * trên trang /mentors. Điểm trung bình và số đánh giá lấy từ bảng reviews.
 */
@Data
@Builder
public class MentorResponse {
    private Long id;
    private String fullName;
    private String avatar;
    private Double rating;
    private Long reviewCount;
}
