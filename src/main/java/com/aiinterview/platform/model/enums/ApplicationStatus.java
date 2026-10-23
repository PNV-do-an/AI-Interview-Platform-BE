package com.aiinterview.platform.model.enums;

public enum ApplicationStatus {
    PENDING,        // map: SUBMITTED (Chờ duyệt)
    SHORTLISTED,    // map: Sơ loại đạt
    INTERVIEWED,    // map: Đã phỏng vấn
    ACCEPTED,       // map: Đạt
    REJECTED,       // map: Không đạt
    CANCELLED,      // map: Đã rút
    SCHEDULED       // map: Hẹn lịch (dùng khi có slot booking)
}
