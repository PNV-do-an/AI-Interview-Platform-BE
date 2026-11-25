package com.aiinterview.platform.model.enums;

/**
 * Nguồn gốc của một subtopic. Identity của subtopic là cặp (skill_key, subtopic_key);
 * source chỉ là thuộc tính, dùng ở đúng 3 chỗ (spec §4.8):
 * 1. Quyết định AI có bị ràng buộc vào danh sách này không — chỉ ADMIN mới ràng buộc.
 * 2. Hiện cờ "cần xem lại" ở màn coverage.
 * 3. Ghi nhận khi admin promote AI_SUGGESTED thành ADMIN.
 */
public enum SubtopicSource {

    /** Admin khai báo: là ràng buộc, AI phải chọn trong danh sách. */
    ADMIN,

    /** AI tự đặt nhãn khi skill chưa có taxonomy: chỉ là đề xuất. */
    AI_SUGGESTED
}
