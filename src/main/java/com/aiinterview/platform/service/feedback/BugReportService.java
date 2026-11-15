package com.aiinterview.platform.service.feedback;

import com.aiinterview.platform.model.dto.request.BugReportRequest;
import com.aiinterview.platform.model.dto.request.BugReportUpdateRequest;
import com.aiinterview.platform.model.dto.response.BugReportResponse;
import com.aiinterview.platform.model.entity.User;

import java.util.List;

public interface BugReportService {

    BugReportResponse create(User reporter, BugReportRequest request);

    List<BugReportResponse> listMine(User reporter);

    List<BugReportResponse> listAll();

    BugReportResponse update(User staff, Long id, BugReportUpdateRequest request);
}
