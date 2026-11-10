package com.aiinterview.platform.model.dto.response;

import lombok.Builder;
import lombok.Data;

/** Dữ liệu JD bóc tách từ một bài đăng TopCV; jobId có khi BE đã tạo bản nháp. */
@Data
@Builder
public class TopCvImportResult {
    private String sourceUrl;
    private Long jobId;
    private String title;
    private String companyName;
    private String position;
    private String location;
    private String salaryRange;
    private String description;
    private String requirements;
    private String requiredSkills;
}
