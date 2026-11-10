package com.aiinterview.platform.service;

import com.aiinterview.platform.model.dto.request.TopCvBatchImportRequest;
import com.aiinterview.platform.model.dto.response.TopCvBatchImportResult;
import com.aiinterview.platform.model.dto.response.TopCvImportResult;

public interface TopCvImportService {

    /** Tải một bài đăng TopCV, bóc tách JD và tạo bản nháp tin tuyển dụng. */
    TopCvImportResult importJob(String url);

    /**
     * Cào nhiều JD trong một lần: nhận danh sách URL bài đăng, hoặc một URL trang
     * danh sách/tìm kiếm TopCV để tự phát hiện các bài đăng bên trong.
     */
    TopCvBatchImportResult importJobs(TopCvBatchImportRequest request);

    /** Một nút bấm: cào danh sách JD IT mặc định (app.topcv.it-listing-url). */
    TopCvBatchImportResult importDefaultItJobs(int limit);

    /** Cào danh sách JD IT từ ITviec (app.topcv.itviec-listing-url). */
    TopCvBatchImportResult importItviecJobs(int limit);

    /** Ưu tiên TopCV, tự chuyển sang ITviec nếu TopCV bị chặn. */
    TopCvBatchImportResult importAutoItJobs(int limit);
}
