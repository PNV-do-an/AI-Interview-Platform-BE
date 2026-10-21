package com.aiinterview.platform.controller;

import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.response.JobModerationHistoryResponse;
import com.aiinterview.platform.model.dto.response.ModerationHistoryStatsResponse;
import com.aiinterview.platform.model.dto.response.ModerationJobDetailResponse;
import com.aiinterview.platform.model.dto.response.ModerationJobResponse;
import com.aiinterview.platform.model.dto.response.ModerationQueueStatsResponse;
import com.aiinterview.platform.model.enums.JobStatus;
import com.aiinterview.platform.model.enums.ModerationAction;
import com.aiinterview.platform.service.JobService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Moderation console (spec 004), opened to ROLE_STAFF by 009 FR-007 because staff already
 * decides approvals through PATCH /jobs/{id}/review. The URL rule under
 * /api/v1/admin/jobs/moderation/** carries the same pair; this annotation is the second layer.
 */
@RestController
@RequestMapping("/api/v1/admin/jobs/moderation")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
@Validated
@RequiredArgsConstructor
public class AdminJobModerationController {

    private static final int MAX_PAGE_SIZE = 100;

    private final JobService jobService;

    /** GET /api/v1/admin/jobs/moderation/stats — the four queue chips (009 FR-004). */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<ModerationQueueStatsResponse>> moderationStats() {
        return ResponseEntity.ok(ApiResponse.success(jobService.moderationQueueStats()));
    }

    /** GET /api/v1/admin/jobs/moderation?status=PENDING_APPROVAL&search=... */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ModerationJobResponse>>> moderationQueue(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page không được âm") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size phải ≥ 1")
            @Max(value = MAX_PAGE_SIZE, message = "size tối đa là 100") int size,
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(ApiResponse.success(
                jobService.moderationQueue(page, size, status, search)));
    }

    /** GET /api/v1/admin/jobs/moderation/{id} — full detail + moderation timeline. */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ModerationJobDetailResponse>> moderationDetail(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(jobService.moderationDetail(id)));
    }

    /**
     * GET /api/v1/admin/jobs/moderation/history?decision=APPROVED&from=2026-10-01&to=2026-10-31
     * &moderatorId=&search=
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<Page<JobModerationHistoryResponse>>> moderationHistory(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page không được âm") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size phải ≥ 1")
            @Max(value = MAX_PAGE_SIZE, message = "size tối đa là 100") int size,
            @RequestParam(required = false) ModerationAction decision,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long moderatorId,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(ApiResponse.success(
                jobService.moderationHistory(page, size, decision, from, to, moderatorId, search)));
    }

    /** GET /api/v1/admin/jobs/moderation/history/stats — the four KPI cards. */
    @GetMapping("/history/stats")
    public ResponseEntity<ApiResponse<ModerationHistoryStatsResponse>> moderationHistoryStats() {
        return ResponseEntity.ok(ApiResponse.success(jobService.moderationHistoryStats()));
    }
}
