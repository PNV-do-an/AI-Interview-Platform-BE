package com.aiinterview.platform.service.job.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.model.dto.request.ApplyJobRequest;
import com.aiinterview.platform.model.dto.request.BookSlotRequest;
import com.aiinterview.platform.model.dto.request.EvaluateApplicationRequest;
import com.aiinterview.platform.model.dto.response.ApplicationDetailResponse;
import com.aiinterview.platform.model.dto.response.ApplicationFunnelResponse;
import com.aiinterview.platform.model.dto.response.ApplicationResponse;
import com.aiinterview.platform.model.entity.Application;
import com.aiinterview.platform.model.entity.ApplicationStatusHistory;
import com.aiinterview.platform.model.entity.InterviewSlot;
import com.aiinterview.platform.model.entity.JobDescription;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.ApplicationStatus;
import com.aiinterview.platform.model.enums.JobStatus;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.ApplicationRepository;
import com.aiinterview.platform.model.repository.ApplicationStatusHistoryRepository;
import com.aiinterview.platform.model.repository.InterviewSlotRepository;
import com.aiinterview.platform.model.repository.JobDescriptionRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.service.infra.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Rules of the application pipeline (FR-APP-*): who may apply, which transitions are legal,
 * how a cancelled booking frees its slot, and which timeline data a candidate may see.
 *
 * <p>Mỗi test chia 3 khối comment: <b>Arrange</b> → <b>Act</b> → <b>Assert</b>.</p>
 */
class ApplicationServiceImplTest {

    private static final Long APPLICATION_ID = 42L;
    private static final Long JOB_ID = 7L;
    private static final Long SLOT_ID = 5L;

    private ApplicationServiceImpl service;
    private ApplicationRepository applicationRepository;
    private ApplicationStatusHistoryRepository historyRepository;
    private JobDescriptionRepository jobRepository;
    private InterviewSlotRepository slotRepository;
    private UserRepository userRepository;
    private EmailService emailService;

    private User employer;
    private User candidate;
    private User otherEmployer;
    private User admin;

    @BeforeEach
    void setUp() {
        // 6 phu thuoc -> tao mock va service bang tay
        applicationRepository = mock(ApplicationRepository.class);
        historyRepository = mock(ApplicationStatusHistoryRepository.class);
        jobRepository = mock(JobDescriptionRepository.class);
        slotRepository = mock(InterviewSlotRepository.class);
        userRepository = mock(UserRepository.class);
        emailService = mock(EmailService.class);
        service = new ApplicationServiceImpl(applicationRepository, historyRepository, jobRepository,
                slotRepository, userRepository, emailService);

        // 4 danh tinh: chu tin, ung vien, chu tin khac (de test ownership), admin
        employer = user(10L, "employer@example.com", "Employer User", Role.ROLE_INTERVIEWER);
        candidate = user(20L, "candidate@example.com", "Candidate User", Role.ROLE_USER);
        otherEmployer = user(12L, "other@example.com", "Other Employer", Role.ROLE_INTERVIEWER);
        admin = user(1L, "admin@example.com", "Admin User", Role.ROLE_ADMIN);

        // Service doc nguoi dang nhap qua SecurityUtils -> tra duoc ca 4 email
        for (User user : List.of(employer, candidate, otherEmployer, admin)) {
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        }
        // save tra lai chinh doi tuong de assert doc duoc trang thai sau khi luu
        when(applicationRepository.save(any(Application.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(slotRepository.save(any(InterviewSlot.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clearSecurityContext() {
        // SecurityContext la ThreadLocal -> clear giua cac test
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Ung tuyen vao tin khong con OPEN bi chan 400")
    void apply_rejectsNonOpenJob() {
        // Arrange: ung vien dang nhap, tin da CLOSED
        signIn(candidate);
        givenJob(JobStatus.CLOSED);

        // Act & Assert: khong nhan don cho tin da dong
        assertThatThrownBy(() -> service.apply(JOB_ID, new ApplyJobRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Tin tuyển dụng này đã đóng");

        // Assert: khong tao don
        verify(applicationRepository, never()).save(any(Application.class));
    }

    @Test
    @DisplayName("Ung tuyen lan hai vao cung tin bi chan, tru khi don truoc da CANCELLED")
    void apply_rejectsDuplicateApplication() {
        // Arrange: da co don chua bi huy cho cung (candidate, job)
        signIn(candidate);
        givenJob(JobStatus.OPEN);
        when(applicationRepository.existsByCandidateAndJobAndStatusNot(
                any(User.class), any(JobDescription.class), any(ApplicationStatus.class)))
                .thenReturn(true);

        // Act & Assert: chan don trung (query loai tru status CANCELLED nen huy roi van nop lai duoc)
        assertThatThrownBy(() -> service.apply(JOB_ID, new ApplyJobRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Bạn đã ứng tuyển vào vị trí này");

        // Assert: khong tao don thu hai
        verify(applicationRepository, never()).save(any(Application.class));
    }

    @Test
    @DisplayName("Ung tuyen thanh cong ghi timeline PENDING va khong nem loi khi gui mail hong")
    void apply_recordsPendingHistoryAndToleratesEmailFailure() {
        // Arrange: SMTP chet — gui mail loi khong duoc lam hong giao dich ung tuyen
        signIn(candidate);
        givenJob(JobStatus.OPEN);
        doThrow(new IllegalStateException("smtp down"))
                .when(emailService).sendApplicationReceivedEmail(any(), any(), any());

        // Act
        ApplicationResponse response = service.apply(JOB_ID, new ApplyJobRequest());

        // Assert: don van duoc tao o PENDING
        assertThat(response.getStatus()).isEqualTo(ApplicationStatus.PENDING);

        // Assert: timeline ghi dong dau tien — fromStatus null (chua co trang thai truoc), actor = candidate
        ArgumentCaptor<ApplicationStatusHistory> history =
                ArgumentCaptor.forClass(ApplicationStatusHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getFromStatus()).isNull();
        assertThat(history.getValue().getToStatus()).isEqualTo("PENDING");
        assertThat(history.getValue().getActor()).isEqualTo(candidate);
    }

    @Test
    @DisplayName("Huy don o vong da phong van / da nhan bi chan 400")
    void cancelApplication_rejectsFinalStages() {
        // Arrange: don da o vong INTERVIEWED (da phong van xong)
        signIn(candidate);
        Application application = givenApplication(ApplicationStatus.INTERVIEWED, null);
        when(applicationRepository.findByIdAndCandidate(APPLICATION_ID, candidate))
                .thenReturn(Optional.of(application));

        // Act & Assert: qua muon de huy, message neu ro trang thai hien tai
        assertThatThrownBy(() -> service.cancelApplication(APPLICATION_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Không thể hủy đơn ở trạng thái INTERVIEWED");
    }

    @Test
    @DisplayName("Huy don tra khung gio ve trang thai trong va go lien ket slot")
    void cancelApplication_releasesBookedSlot() {
        // Arrange: don dang SCHEDULED voi 1 khung gio da dat
        signIn(candidate);
        InterviewSlot slot = slot(SLOT_ID, candidate, LocalDateTime.now().plusDays(2));
        Application application = givenApplication(ApplicationStatus.SCHEDULED, slot);
        when(applicationRepository.findByIdAndCandidate(APPLICATION_ID, candidate))
                .thenReturn(Optional.of(application));

        // Act
        service.cancelApplication(APPLICATION_ID);

        // Assert: don bi huy va khong con tro toi slot
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
        assertThat(application.getSlot()).isNull();

        // Assert: slot duoc tra ve trong (bookedBy/bookedAt = null) de nguoi khac dat duoc
        assertThat(slot.getBookedBy()).isNull();
        assertThat(slot.getBookedAt()).isNull();
        verify(slotRepository).save(slot);
    }

    @Test
    @DisplayName("Danh gia don cua tin khong phai cua minh bi chan 400")
    void evaluate_rejectsNonOwner() {
        // Arrange: employer khac danh gia don thuoc tin cua employer
        signIn(otherEmployer);
        Application application = givenApplication(ApplicationStatus.PENDING, null);
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));

        // Act & Assert: chi chu tin (hoac admin) moi duoc doi trang thai
        assertThatThrownBy(() -> service.evaluate(APPLICATION_ID, evaluate(ApplicationStatus.SHORTLISTED)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Bạn không có quyền đánh giá đơn ứng tuyển này");

        // Assert: chan truoc khi gui mail ket qua
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("Nhay coc trang thai (PENDING -> ACCEPTED) bi chan 400")
    void evaluate_rejectsIllegalTransition() {
        // Arrange: don dang PENDING, admin/employer muon nhay thang sang ACCEPTED
        signIn(employer);
        Application application = givenApplication(ApplicationStatus.PENDING, null);
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));

        // Act & Assert: phai qua SHORTLISTED -> INTERVIEWED -> ACCEPTED
        assertThatThrownBy(() -> service.evaluate(APPLICATION_ID, evaluate(ApplicationStatus.ACCEPTED)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Không thể chuyển trạng thái từ PENDING sang ACCEPTED");
    }

    @Test
    @DisplayName("Sua diem o cung vong khong sinh them dong timeline")
    void evaluate_sameStatusOnlyUpdatesScore() {
        // Arrange: van o PENDING nhung chi cap nhat diem + nhan xet
        signIn(employer);
        Application application = givenApplication(ApplicationStatus.PENDING, null);
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        EvaluateApplicationRequest request = evaluate(ApplicationStatus.PENDING);
        request.setScore(85);
        request.setFeedback("Gioi");

        // Act
        ApplicationResponse response = service.evaluate(APPLICATION_ID, request);

        // Assert: giu nguyen trang thai nhung cap nhat duoc diem va nhan xet
        assertThat(response.getStatus()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(response.getScore()).isEqualTo(85);
        assertThat(response.getFeedback()).isEqualTo("Gioi");

        // Assert: khong doi trang thai => khong ghi timeline (tranh nhieu dong rac)
        verify(historyRepository, never()).save(any(ApplicationStatusHistory.class));
    }

    @Test
    @DisplayName("Dat khung gio da co nguoi dat bi chan 400")
    void bookSlot_rejectsAlreadyBookedSlot() {
        // Arrange: slot con han nhung da co nguoi dat
        signIn(candidate);
        InterviewSlot slot = slot(SLOT_ID, otherEmployer, LocalDateTime.now().plusDays(1));
        slot.setBookedBy(otherEmployer);
        when(slotRepository.findByIdAndCancelledFalse(SLOT_ID)).thenReturn(Optional.of(slot));

        // Act & Assert: khong the dat chong
        assertThatThrownBy(() -> service.bookSlot(bookSlot(SLOT_ID)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Khung giờ này đã có người đặt");
    }

    @Test
    @DisplayName("Dat khung gio trong qua khu bi chan 400")
    void bookSlot_rejectsPastSlot() {
        // Arrange: slot bat dau cach day 1 gio
        signIn(candidate);
        InterviewSlot slot = slot(SLOT_ID, employer, LocalDateTime.now().minusHours(1));
        when(slotRepository.findByIdAndCancelledFalse(SLOT_ID)).thenReturn(Optional.of(slot));

        // Act & Assert: khong dat lich vao qua khu
        assertThatThrownBy(() -> service.bookSlot(bookSlot(SLOT_ID)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Khung giờ này đã qua");
    }

    @Test
    @DisplayName("Dat lich khong kem don ung tuyen bi chan 400")
    void bookSlot_requiresExistingApplication() {
        // Arrange: co slot hop le nhung request khong kem applicationId
        signIn(candidate);
        InterviewSlot slot = slot(SLOT_ID, employer, LocalDateTime.now().plusDays(1));
        when(slotRepository.findByIdAndCancelledFalse(SLOT_ID)).thenReturn(Optional.of(slot));
        BookSlotRequest request = bookSlot(SLOT_ID);

        // Act & Assert: phai ung tuyen truoc roi moi dat lich
        assertThatThrownBy(() -> service.bookSlot(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Vui lòng ứng tuyển trước khi đặt lịch");

        // Assert: slot khong bi chiem
        assertThat(slot.getBookedBy()).isNull();
    }

    @Test
    @DisplayName("Dat lich thanh cong chuyen don sang SCHEDULED va ghi timeline")
    void bookSlot_schedulesApplicationAndNotifiesBothSides() {
        // Arrange: slot con trong + don PENDING cua chinh candidate
        signIn(candidate);
        InterviewSlot slot = slot(SLOT_ID, employer, LocalDateTime.now().plusDays(1));
        Application application = givenApplication(ApplicationStatus.PENDING, null);
        when(slotRepository.findByIdAndCancelledFalse(SLOT_ID)).thenReturn(Optional.of(slot));
        when(applicationRepository.findByIdAndCandidate(APPLICATION_ID, candidate))
                .thenReturn(Optional.of(application));

        // Act
        BookSlotRequest request = bookSlot(SLOT_ID);
        request.setApplicationId(APPLICATION_ID);
        ApplicationResponse response = service.bookSlot(request);

        // Assert: don chuyen sang SCHEDULED va slot ghi nhan nguoi dat
        assertThat(response.getStatus()).isEqualTo(ApplicationStatus.SCHEDULED);
        assertThat(slot.getBookedBy()).isEqualTo(candidate);

        // Assert: gui 2 mail (ung vien + nguoi phong van)
        verify(emailService, times(2)).sendSlotBookedEmail(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Huy lich tren don chua co lich bi chan 400")
    void unschedule_requiresScheduledApplication() {
        // Arrange: don dang PENDING (chua dat lich)
        signIn(candidate);
        Application application = givenApplication(ApplicationStatus.PENDING, null);
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));

        // Act & Assert: khong co lich thi khong co gi de huy
        assertThatThrownBy(() -> service.unschedule(APPLICATION_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Đơn ứng tuyển chưa có lịch phỏng vấn");
    }

    @Test
    @DisplayName("Doi lich sang khung gio cua nguoi phong van khac bi chan 400")
    void reschedule_rejectsSlotOfAnotherInterviewer() {
        // Arrange: don dang gan voi slot cua employer, muon doi sang slot cua otherEmployer
        signIn(candidate);
        InterviewSlot current = slot(SLOT_ID, employer, LocalDateTime.now().plusDays(1));
        Application application = givenApplication(ApplicationStatus.SCHEDULED, current);
        InterviewSlot target = slot(6L, otherEmployer, LocalDateTime.now().plusDays(3));
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(slotRepository.findByIdAndCancelledFalse(SLOT_ID)).thenReturn(Optional.of(target));

        // Act & Assert: doi lich chi trong cung mot nguoi phong van
        assertThatThrownBy(() -> service.reschedule(APPLICATION_ID, SLOT_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Khung giờ không thuộc người phỏng vấn của đơn này");
    }

    @Test
    @DisplayName("Chi tiet don: ghi chu noi bo va nguoi thao tac bi an voi candidate (BR-09)")
    void getApplicationDetail_hidesInternalNoteFromCandidate() {
        // Arrange: 1 dong timeline co note noi bo va actor la employer
        Application application = givenApplication(ApplicationStatus.SHORTLISTED, null);
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        ApplicationStatusHistory row = ApplicationStatusHistory.builder()
                .application(application)
                .fromStatus("PENDING")
                .toStatus("SHORTLISTED")
                .actor(employer)
                .note("Ung vien tot nhung luong cao")
                .actedAt(LocalDateTime.now())
                .build();
        when(historyRepository.findByApplicationOrderByActedAtAsc(application)).thenReturn(List.of(row));

        // Act: xem bang quyen candidate
        signIn(candidate);
        ApplicationDetailResponse asCandidate = service.getApplicationDetail(APPLICATION_ID);

        // Assert: candidate thay lich su nhung note noi bo va actor bi an (null)
        assertThat(asCandidate.getStatusHistory()).hasSize(1);
        assertThat(asCandidate.getStatusHistory().get(0).getNote()).isNull();
        assertThat(asCandidate.getStatusHistory().get(0).getActorId()).isNull();

        // Act: cung du lieu nhung xem bang quyen chu tin
        signIn(employer);
        ApplicationDetailResponse asEmployer = service.getApplicationDetail(APPLICATION_ID);

        // Assert: employer thay day du note + actorId (de doi soat noi bo)
        assertThat(asEmployer.getStatusHistory().get(0).getNote()).isEqualTo("Ung vien tot nhung luong cao");
        assertThat(asEmployer.getStatusHistory().get(0).getActorId()).isEqualTo(employer.getId());
    }

    @Test
    @DisplayName("Xem chi tiet don cua nguoi khac bi chan 400")
    void getApplicationDetail_byStranger_returnsBadRequest() {
        // Arrange: employer khac mo don thuoc tin cua employer
        signIn(otherEmployer);
        Application application = givenApplication(ApplicationStatus.PENDING, null);
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));

        // Act & Assert: chan ca du lieu ung vien lan timeline
        assertThatThrownBy(() -> service.getApplicationDetail(APPLICATION_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Bạn không có quyền xem chi tiết đơn ứng tuyển này");
    }

    @Test
    @DisplayName("Pheu tinh theo vong xa nhat: don bi tu choi o vong phong van van tinh cac vong truoc")
    void getApplicationFunnel_countsFurthestStageFromTimeline() {
        // Arrange: 1 don status hien tai = REJECTED, nhung timeline cho thay da toi INTERVIEWED
        signIn(employer);
        givenJob(JobStatus.OPEN);
        List<Object[]> statuses = List.<Object[]>of(new Object[]{APPLICATION_ID, ApplicationStatus.REJECTED});
        List<Object[]> reached = List.<Object[]>of(new Object[]{APPLICATION_ID, "INTERVIEWED"});
        when(applicationRepository.findStatusesByJob(JOB_ID)).thenReturn(statuses);
        when(historyRepository.findReachedStatusesByJob(JOB_ID)).thenReturn(reached);

        // Act
        ApplicationFunnelResponse funnel = service.getApplicationFunnel(JOB_ID);

        // Assert: tong don = 1
        assertThat(funnel.getTotalApplications()).isEqualTo(1L);

        // Assert: don nay duoc tinh da toi PENDING/SHORTLISTED/INTERVIEWED/ACCEPTED=chua, nen
        // chuoi reached = 1,1,1,1,0 (cot cuoi ACCEPTED = 0 vi bi tu choi)
        assertThat(funnel.getStages()).extracting(ApplicationFunnelResponse.Stage::getReached)
                .containsExactly(1L, 1L, 1L, 1L, 0L);
        assertThat(funnel.getStages().get(0).getStatus()).isEqualTo(ApplicationStatus.PENDING);

        // Assert: ty le qua vong = reached(vong sau) / reached(vong nay); 0 don o vong cuoi => null
        assertThat(funnel.getStages().get(3).getPassRate()).isEqualTo(0.0);
        assertThat(funnel.getStages().get(4).getPassRate()).isNull();
    }

    @Test
    @DisplayName("Khung gio khong ton tai tra 404")
    void bookSlot_unknownSlot_returnsNotFound() {
        // Arrange: slotId khong co trong DB (hoac da bi huy)
        signIn(candidate);
        when(slotRepository.findByIdAndCancelledFalse(SLOT_ID)).thenReturn(Optional.empty());

        // Act & Assert: 404 kem id de FE hien thi dung
        assertThatThrownBy(() -> service.bookSlot(bookSlot(SLOT_ID)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy khung giờ với id: " + SLOT_ID);
    }

    private void signIn(User user) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(user.getEmail(), null));
    }

    private JobDescription givenJob(JobStatus status) {
        JobDescription job = JobDescription.builder()
                .id(JOB_ID)
                .employer(employer)
                .title("Senior Backend Engineer")
                .companyName("InterviewAI Platform")
                .status(status)
                .build();
        when(jobRepository.findByIdAndDeletedAtIsNull(JOB_ID)).thenReturn(Optional.of(job));
        return job;
    }

    private Application givenApplication(ApplicationStatus status, InterviewSlot slot) {
        JobDescription job = JobDescription.builder()
                .id(JOB_ID)
                .employer(employer)
                .title("Senior Backend Engineer")
                .companyName("InterviewAI Platform")
                .status(JobStatus.OPEN)
                .build();
        return Application.builder()
                .id(APPLICATION_ID)
                .candidate(candidate)
                .job(job)
                .slot(slot)
                .status(status)
                .build();
    }

    private InterviewSlot slot(Long slotId, User slotEmployer, LocalDateTime start) {
        return InterviewSlot.builder()
                .id(slotId)
                .employer(slotEmployer)
                .startTime(start)
                .endTime(start.plusHours(1))
                .build();
    }

    private BookSlotRequest bookSlot(Long slotId) {
        BookSlotRequest request = new BookSlotRequest();
        request.setSlotId(slotId);
        return request;
    }

    private EvaluateApplicationRequest evaluate(ApplicationStatus status) {
        EvaluateApplicationRequest request = new EvaluateApplicationRequest();
        request.setStatus(status);
        return request;
    }

    private User user(Long id, String email, String fullName, Role role) {
        return User.builder()
                .id(id)
                .email(email)
                .password("encoded")
                .fullName(fullName)
                .role(role)
                .locked(false)
                .build();
    }
}
