package com.aiinterview.platform.model.enums;

/**
 * Vòng đời của một subtopic. MERGED nghĩa là đã gộp vào subtopic khác, không còn
 * được chọn làm đích sinh bộ mới nhưng row vẫn giữ để truy vết question_set cũ.
 */
public enum SubtopicStatus {

    /** Đang dùng: được tính vào trần hiệu dụng và được chọn làm đích sinh. */
    ACTIVE,

    /** Đã gộp vào subtopic khác: không chọn làm đích, không tính coverage. */
    MERGED
}
