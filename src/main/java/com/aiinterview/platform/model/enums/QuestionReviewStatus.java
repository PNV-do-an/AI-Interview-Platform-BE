package com.aiinterview.platform.model.enums;

/**
 * Trạng thái hậu kiểm của một câu hỏi.
 * Không có gate duyệt: AI sinh xong là phục vụ được ngay (spec D5).
 */
public enum QuestionReviewStatus {

    /** AI sinh, đang phục vụ. */
    AUTO_APPROVED,

    /** Admin đã xác nhận. */
    APPROVED,

    /** Ngừng cấp cho bộ mới; câu đã giao vẫn còn trong lịch sử study_answer. */
    REJECTED;

    /** Dùng thay cho {@code status != REJECTED} ở tầng chọn câu và đếm set hiệu dụng. */
    public boolean isServable() {
        return this != REJECTED;
    }
}
