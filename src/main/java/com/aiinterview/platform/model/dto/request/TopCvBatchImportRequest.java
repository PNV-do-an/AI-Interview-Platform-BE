package com.aiinterview.platform.model.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Cào nhiều JD TopCV trong một lần.
 * - {@code url}: link bài đăng cụ thể hoặc link trang danh sách/tìm kiếm TopCV.
 * - {@code urls}: danh sách link bài đăng cụ thể (tuỳ chọn).
 */
@Data
public class TopCvBatchImportRequest {

    @Size(max = 2000, message = "URL tối đa 2000 ký tự")
    private String url;

    @Size(max = 100, message = "Tối đa 100 URL mỗi lần")
    private List<@Size(max = 2000, message = "Mỗi URL tối đa 2000 ký tự") String> urls;

    @Min(value = 1, message = "limit phải ≥ 1")
    @Max(value = 50, message = "limit tối đa là 50")
    private int limit = 10;
}
