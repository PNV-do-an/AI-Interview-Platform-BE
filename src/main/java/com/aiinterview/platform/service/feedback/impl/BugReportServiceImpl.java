package com.aiinterview.platform.service.feedback.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.model.dto.request.BugReportRequest;
import com.aiinterview.platform.model.dto.request.BugReportUpdateRequest;
import com.aiinterview.platform.model.dto.response.BugReportResponse;
import com.aiinterview.platform.model.entity.BugReport;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.ReportCategory;
import com.aiinterview.platform.model.enums.ReportStatus;
import com.aiinterview.platform.model.repository.BugReportRepository;
import com.aiinterview.platform.service.feedback.BugReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BugReportServiceImpl implements BugReportService {

    private final BugReportRepository bugReportRepository;

    @Override
    @Transactional
    public BugReportResponse create(User reporter, BugReportRequest request) {
        ReportCategory category;
        try {
            category = ReportCategory.valueOf(request.getCategory().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Loại báo cáo không hợp lệ (BUG, FEATURE, OTHER)");
        }

        BugReport report = BugReport.builder()
                .reporter(reporter)
                .title(request.getTitle().trim())
                .category(category)
                .description(request.getDescription())
                .status(ReportStatus.OPEN)
                .build();
        return BugReportResponse.from(bugReportRepository.save(report));
    }

    @Override
    public List<BugReportResponse> listMine(User reporter) {
        return bugReportRepository.findByReporterOrderByCreatedAtDesc(reporter).stream()
                .map(BugReportResponse::from)
                .toList();
    }

    @Override
    public List<BugReportResponse> listAll() {
        return bugReportRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(BugReportResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public BugReportResponse update(User staff, Long id, BugReportUpdateRequest request) {
        BugReport report = bugReportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy báo cáo"));

        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            try {
                report.setStatus(ReportStatus.valueOf(request.getStatus().trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Trạng thái không hợp lệ (OPEN, IN_PROGRESS, RESOLVED)");
            }
        }
        if (request.getReply() != null && !request.getReply().isBlank()) {
            report.setReply(request.getReply());
            report.setRepliedBy(staff);
            report.setRepliedAt(LocalDateTime.now());
        }
        return BugReportResponse.from(bugReportRepository.save(report));
    }
}
