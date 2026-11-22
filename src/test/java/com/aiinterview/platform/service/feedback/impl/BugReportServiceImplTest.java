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
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.BugReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Bug report rules (Postman folder 12): the category whitelist is closed, every new report starts
 * OPEN, and only staff may move the status or attach a reply.
 *
 * <p>Mỗi test chia 3 khối comment: <b>Arrange</b> → <b>Act</b> → <b>Assert</b>.</p>
 */
class BugReportServiceImplTest {

    private static final Long REPORT_ID = 44L;

    private BugReportServiceImpl service;
    private BugReportRepository bugReportRepository;

    private User reporter;
    private User staff;

    @BeforeEach
    void setUp() {
        bugReportRepository = mock(BugReportRepository.class);
        service = new BugReportServiceImpl(bugReportRepository);

        // 2 vai: nguoi gui bao cao va staff xu ly
        reporter = user(20L, "candidate@example.com", "Candidate User", Role.ROLE_USER);
        staff = user(2L, "staff@example.com", "Staff User", Role.ROLE_STAFF);

        when(bugReportRepository.save(any(BugReport.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Loai bao cao ngoai BUG/FEATURE/OTHER bi chan 400")
    void create_invalidCategory_returnsBadRequest() {
        // Arrange: category "BUGGY" khong nam trong whitelist
        BugReportRequest request = request("BUGGY");

        // Act & Assert: validate chay truoc khi cham repository
        assertThatThrownBy(() -> service.create(reporter, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Loại báo cáo không hợp lệ (BUG, FEATURE, OTHER)");

        // Assert: input sai thi khong dong toi DB
        verifyNoInteractions(bugReportRepository);
    }

    @Test
    @DisplayName("Loai bao cao duoc chuan hoa chu thuong va khoang trang")
    void create_normalisesCategoryCase() {
        // Arrange: client gui "  feature  " (chu thuong + khoang trang)

        // Act
        service.create(reporter, request("  feature  "));

        // Assert: duoc trim + uppercase thanh enum FEATURE truoc khi luu
        ArgumentCaptor<BugReport> saved = ArgumentCaptor.forClass(BugReport.class);
        verify(bugReportRepository).save(saved.capture());
        assertThat(saved.getValue().getCategory()).isEqualTo(ReportCategory.FEATURE);
    }

    @Test
    @DisplayName("Bao cao moi luon o trang thai OPEN va tieu de duoc trim")
    void create_alwaysStartsOpenAndTrimsTitle() {
        // Arrange: tieu de gui len co khoang trang hai dau

        // Act
        BugReportResponse response = service.create(reporter, request("BUG"));

        // Assert: client khong duoc tu chon status — luon OPEN
        assertThat(response.getStatus()).isEqualTo(ReportStatus.OPEN);

        // Assert: tieu de duoc trim va reporter lay tu nguoi dang nhap; reply con null
        assertThat(response.getTitle()).isEqualTo("Khong dang nhap duoc");
        assertThat(response.getReporterId()).isEqualTo(reporter.getId());
        assertThat(response.getReply()).isNull();
    }

    @Test
    @DisplayName("GET /bug-reports/my chi tra bao cao do chinh minh gui")
    void listMine_queriesCurrentReporter() {
        // Arrange: reporter co 1 bao cao
        when(bugReportRepository.findByReporterOrderByCreatedAtDesc(reporter))
                .thenReturn(List.of(report(ReportStatus.OPEN)));

        // Act
        List<BugReportResponse> reports = service.listMine(reporter);

        // Assert: query loc theo reporter dang dang nhap
        assertThat(reports).hasSize(1);
        assertThat(reports.get(0).getId()).isEqualTo(REPORT_ID);
        verify(bugReportRepository).findByReporterOrderByCreatedAtDesc(reporter);
    }

    @Test
    @DisplayName("GET /bug-reports (staff) sap xep moi nhat truoc")
    void listAll_sortsNewestFirst() {
        // Arrange: repository tra ve thu tu cu -> moi
        BugReport older = report(ReportStatus.OPEN);
        older.setCreatedAt(LocalDateTime.now().minusDays(1));
        older.setTitle("Cu");
        BugReport newer = report(ReportStatus.IN_PROGRESS);
        newer.setCreatedAt(LocalDateTime.now());
        newer.setTitle("Moi");
        when(bugReportRepository.findAll()).thenReturn(List.of(older, newer));

        // Act
        List<BugReportResponse> reports = service.listAll();

        // Assert: service tu sap xep lai — bao cao moi dung dau
        assertThat(reports).extracting(BugReportResponse::getTitle).containsExactly("Moi", "Cu");
    }

    @Test
    @DisplayName("Trang thai ngoai whitelist bi chan 400 va khong luu")
    void update_invalidStatus_returnsBadRequest() {
        // Arrange: bao cao ton tai, staff gui status "DONE" (khong co trong enum)
        when(bugReportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report(ReportStatus.OPEN)));
        BugReportUpdateRequest request = update("DONE", null);

        // Act & Assert: whitelist dong, khong luu status la
        assertThatThrownBy(() -> service.update(staff, REPORT_ID, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Trạng thái không hợp lệ (OPEN, IN_PROGRESS, RESOLVED)");

        // Assert: khong ghi gi
        verify(bugReportRepository, never()).save(any(BugReport.class));
    }

    @Test
    @DisplayName("Chuyen trang thai hop le duoc luu lai")
    void update_validStatus_persistsTransition() {
        // Arrange: bao cao dang OPEN, staff gui "in_progress" (chu thuong)
        BugReport report = report(ReportStatus.OPEN);
        when(bugReportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report));

        // Act
        BugReportResponse response = service.update(staff, REPORT_ID, update("in_progress", null));

        // Assert: chuan hoa thanh enum va luu lai
        assertThat(response.getStatus()).isEqualTo(ReportStatus.IN_PROGRESS);
        verify(bugReportRepository).save(report);
    }

    @Test
    @DisplayName("Reply rong bi bo qua, khong ghi de nguoi phan hoi")
    void update_blankReply_isIgnored() {
        // Arrange: chi gui reply la khoang trang, khong gui status
        BugReport report = report(ReportStatus.OPEN);
        when(bugReportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report));

        // Act
        BugReportResponse response = service.update(staff, REPORT_ID, update(null, "   "));

        // Assert: ca 3 cot reply deu de nguyen null (khong danh dau da phan hoi)
        assertThat(response.getReply()).isNull();
        assertThat(response.getRepliedByName()).isNull();
        assertThat(response.getRepliedAt()).isNull();
    }

    @Test
    @DisplayName("Reply hop le ghi kem nguoi phan hoi va thoi diem")
    void update_reply_recordsStaffMetadata() {
        // Arrange: staff chuyen RESOLVED kem loi nhan
        BugReport report = report(ReportStatus.OPEN);
        when(bugReportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report));

        // Act
        BugReportResponse response = service.update(staff, REPORT_ID, update("RESOLVED", "Da sua o ban 1.2"));

        // Assert: reply + ten nguoi phan hoi + moc thoi gian + status moi
        assertThat(response.getReply()).isEqualTo("Da sua o ban 1.2");
        assertThat(response.getRepliedByName()).isEqualTo(staff.getFullName());
        assertThat(response.getRepliedAt()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(ReportStatus.RESOLVED);
    }

    @Test
    @DisplayName("Cap nhat bao cao khong ton tai tra 404")
    void update_unknownReport_returnsNotFound() {
        // Arrange: reportId khong co trong DB
        when(bugReportRepository.findById(REPORT_ID)).thenReturn(Optional.empty());

        // Act & Assert: 404 va khong ghi gi
        assertThatThrownBy(() -> service.update(staff, REPORT_ID, update("OPEN", null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy báo cáo");
        verify(bugReportRepository, never()).save(any(BugReport.class));
    }

    private BugReportRequest request(String category) {
        BugReportRequest request = new BugReportRequest();
        request.setTitle("  Khong dang nhap duoc  ");
        request.setCategory(category);
        request.setDescription("Bam dang nhap thi quay vong");
        return request;
    }

    private BugReportUpdateRequest update(String status, String reply) {
        BugReportUpdateRequest request = new BugReportUpdateRequest();
        request.setStatus(status);
        request.setReply(reply);
        return request;
    }

    private BugReport report(ReportStatus status) {
        return BugReport.builder()
                .id(REPORT_ID)
                .reporter(reporter)
                .title("Khong dang nhap duoc")
                .category(ReportCategory.BUG)
                .description("Bam dang nhap thi quay vong")
                .status(status)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private User user(Long id, String email, String fullName, Role role) {
        return User.builder()
                .id(id)
                .email(email)
                .fullName(fullName)
                .password("secret")
                .role(role)
                .build();
    }
}
