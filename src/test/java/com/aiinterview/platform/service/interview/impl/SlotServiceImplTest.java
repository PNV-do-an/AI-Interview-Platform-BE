package com.aiinterview.platform.service.interview.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.model.dto.request.SlotRequest;
import com.aiinterview.platform.model.dto.response.SlotResponse;
import com.aiinterview.platform.model.entity.InterviewSlot;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.InterviewSlotRepository;
import com.aiinterview.platform.model.repository.UserRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Slot rules (folder 06 of the Postman collection): time window, ownership, and the
 * "a booked slot is frozen" guarantee that keeps employer and candidate calendars consistent.
 */
class SlotServiceImplTest {

    private static final Long SLOT_ID = 5L;

    private SlotServiceImpl service;
    private InterviewSlotRepository slotRepository;
    private UserRepository userRepository;

    private User employer;
    private User otherEmployer;
    private User candidate;

    @BeforeEach
    void setUp() {
        slotRepository = mock(InterviewSlotRepository.class);
        userRepository = mock(UserRepository.class);
        service = new SlotServiceImpl(slotRepository, userRepository);

        employer = user(10L, "employer@example.com", "Employer User", Role.ROLE_INTERVIEWER);
        otherEmployer = user(12L, "other@example.com", "Other Employer", Role.ROLE_INTERVIEWER);
        candidate = user(20L, "candidate@example.com", "Candidate User", Role.ROLE_USER);

        when(userRepository.findByEmail(employer.getEmail())).thenReturn(Optional.of(employer));
        when(userRepository.findByEmail(otherEmployer.getEmail())).thenReturn(Optional.of(otherEmployer));
        when(slotRepository.save(any(InterviewSlot.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Khung gio thieu start/end hoac end <= start bi chan 400")
    void createSlot_rejectsInvalidRange() {
        signIn(employer);
        LocalDateTime start = LocalDateTime.now().plusDays(1);

        assertThatThrownBy(() -> service.createSlot(request(null, start)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Khoảng thởi gian không hợp lệ");
        assertThatThrownBy(() -> service.createSlot(request(start, start)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Khoảng thởi gian không hợp lệ");
        verify(slotRepository, never()).save(any(InterviewSlot.class));
    }

    @Test
    @DisplayName("Khong tao duoc khung gio trong qua khu")
    void createSlot_rejectsPastStart() {
        signIn(employer);
        LocalDateTime past = LocalDateTime.now().minusHours(2);

        assertThatThrownBy(() -> service.createSlot(request(past, past.plusHours(1))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Không thể tạo khung giờ trong quá khứ");
        verify(slotRepository, never()).save(any(InterviewSlot.class));
    }

    @Test
    @DisplayName("Tao khung gio gan dung nguoi dang dang nhap")
    void createSlot_assignsCurrentEmployer() {
        signIn(employer);
        LocalDateTime start = LocalDateTime.now().plusDays(1);

        SlotResponse response = service.createSlot(request(start, start.plusHours(1)));

        assertThat(response.getEmployerId()).isEqualTo(employer.getId());
        assertThat(response.getEmployerName()).isEqualTo(employer.getFullName());
        assertThat(response.isBooked()).isFalse();
        assertThat(response.getStartTime()).isEqualTo(start);
        verify(slotRepository).save(any(InterviewSlot.class));
    }

    @Test
    @DisplayName("GET /slots/my chi tra khung gio cua chinh minh")
    void getMySlots_queriesCurrentEmployer() {
        signIn(employer);
        InterviewSlot slot = slot(employer, LocalDateTime.now().plusDays(1), null);
        when(slotRepository.findByEmployerAndCancelledFalseOrderByStartTimeAsc(employer))
                .thenReturn(List.of(slot));

        List<SlotResponse> slots = service.getMySlots();

        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).getId()).isEqualTo(SLOT_ID);
    }

    @Test
    @DisplayName("GET /slots/available voi employer khong ton tai tra 404")
    void getAvailableSlots_unknownEmployer_returnsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAvailableSlots(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy người phỏng vấn với id: 99");
    }

    @Test
    @DisplayName("GET /slots/available chi doc khung gio trong va sau hien tai")
    void getAvailableSlots_readsFreeFutureSlots() {
        InterviewSlot free = slot(employer, LocalDateTime.now().plusDays(2), null);
        when(userRepository.findById(employer.getId())).thenReturn(Optional.of(employer));
        when(slotRepository.findByEmployerAndBookedByIsNullAndCancelledFalseAndStartTimeAfterOrderByStartTimeAsc(
                eq(employer), any(LocalDateTime.class))).thenReturn(List.of(free));

        List<SlotResponse> slots = service.getAvailableSlots(employer.getId());

        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).isBooked()).isFalse();
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(slotRepository).findByEmployerAndBookedByIsNullAndCancelledFalseAndStartTimeAfterOrderByStartTimeAsc(
                eq(employer), cutoff.capture());
        assertThat(cutoff.getValue()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    @DisplayName("Sua khung gio da co nguoi dat bi chan 400")
    void updateSlot_whenBooked_returnsBadRequest() {
        signIn(employer);
        InterviewSlot booked = slot(employer, LocalDateTime.now().plusDays(1), candidate);
        when(slotRepository.findByIdAndCancelledFalse(SLOT_ID)).thenReturn(Optional.of(booked));

        assertThatThrownBy(() -> service.updateSlot(SLOT_ID, request(
                booked.getStartTime(), booked.getEndTime())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Không thể sửa khung giờ đã có người đặt");
    }

    @Test
    @DisplayName("Sua khung gio cua nguoi khac bi chan 400")
    void updateSlot_byNonOwner_returnsBadRequest() {
        signIn(otherEmployer);
        InterviewSlot slot = slot(employer, LocalDateTime.now().plusDays(1), null);
        when(slotRepository.findByIdAndCancelledFalse(SLOT_ID)).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> service.updateSlot(SLOT_ID, request(
                slot.getStartTime(), slot.getEndTime())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Bạn không có quyền thao tác trên khung giờ này");
    }

    @Test
    @DisplayName("Sua khung gio trong cap nhat ca note")
    void updateSlot_persistsNewWindowAndNote() {
        signIn(employer);
        InterviewSlot slot = slot(employer, LocalDateTime.now().plusDays(1), null);
        when(slotRepository.findByIdAndCancelledFalse(SLOT_ID)).thenReturn(Optional.of(slot));
        LocalDateTime newStart = LocalDateTime.now().plusDays(4);
        SlotRequest request = request(newStart, newStart.plusHours(2));
        request.setNote("Phong van ky thuat");

        SlotResponse response = service.updateSlot(SLOT_ID, request);

        assertThat(response.getStartTime()).isEqualTo(newStart);
        assertThat(response.getNote()).isEqualTo("Phong van ky thuat");
    }

    @Test
    @DisplayName("Huy khung gio da co nguoi dat bi chan 400")
    void cancelSlot_whenBooked_returnsBadRequest() {
        signIn(employer);
        InterviewSlot booked = slot(employer, LocalDateTime.now().plusDays(1), candidate);
        when(slotRepository.findByIdAndCancelledFalse(SLOT_ID)).thenReturn(Optional.of(booked));

        assertThatThrownBy(() -> service.cancelSlot(SLOT_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Không thể hủy khung giờ đã có người đặt");
        assertThat(booked.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("Huy khung gio trong danh dau cancelled chu khong xoa hang")
    void cancelSlot_marksSlotCancelled() {
        signIn(employer);
        InterviewSlot slot = slot(employer, LocalDateTime.now().plusDays(1), null);
        when(slotRepository.findByIdAndCancelledFalse(SLOT_ID)).thenReturn(Optional.of(slot));

        service.cancelSlot(SLOT_ID);

        assertThat(slot.isCancelled()).isTrue();
        verify(slotRepository).save(slot);
        verify(slotRepository, never()).delete(any(InterviewSlot.class));
    }

    @Test
    @DisplayName("Khung gio khong ton tai tra 404")
    void cancelSlot_unknownSlot_returnsNotFound() {
        signIn(employer);
        when(slotRepository.findByIdAndCancelledFalse(SLOT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelSlot(SLOT_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy khung giờ với id: " + SLOT_ID);
    }

    private void signIn(User user) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(user.getEmail(), null));
    }

    private SlotRequest request(LocalDateTime start, LocalDateTime end) {
        SlotRequest request = new SlotRequest();
        request.setStartTime(start);
        request.setEndTime(end);
        return request;
    }

    private InterviewSlot slot(User slotEmployer, LocalDateTime start, User bookedBy) {
        return InterviewSlot.builder()
                .id(SLOT_ID)
                .employer(slotEmployer)
                .startTime(start)
                .endTime(start.plusHours(1))
                .bookedBy(bookedBy)
                .build();
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
