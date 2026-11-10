package com.aiinterview.platform.service;

import com.aiinterview.platform.model.dto.response.TopCvImportResult;

public interface TopCvImportService {

    /** Tải một bài đăng TopCV, bóc tách JD và tạo bản nháp tin tuyển dụng. */
    TopCvImportResult importJob(String url);
}
