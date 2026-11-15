package com.aiinterview.platform.service.interview.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.model.dto.request.SlotRequest;
import com.aiinterview.platform.model.dto.response.SlotResponse;
import com.aiinterview.platform.model.entity.InterviewSlot;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.repository.InterviewSlotRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.security.utils.SecurityUtils;
import com.aiinterview.platform.service.interview.SlotService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SlotServiceImpl implements SlotService {

    private final InterviewSlotRepository slotRepository;
    private final UserRepository userRepository;

    @Override
    public List<SlotResponse> getMySlots() {
        User employer = SecurityUtils.getCurrentUser(userRepository);
        return slotRepository.findByEmployerAndCancelledFalseOrderByStartTimeAsc(employer).stream()
                .map(SlotResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SlotResponse> getAvailableSlots(Long employerId) {
        User employer = userRepository.findById(employerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người phỏng vấn với id: " + employerId));
        return slotRepository
                .findByEmployerAndBookedByIsNullAndCancelledFalseAndStartTimeAfterOrderByStartTimeAsc(
                        employer, LocalDateTime.now()).stream()
                .map(SlotResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SlotResponse createSlot(SlotRequest request) {
        validateTimeRange(request.getStartTime(), request.getEndTime());
        User employer = SecurityUtils.getCurrentUser(userRepository);
        InterviewSlot slot = InterviewSlot.builder()
                .employer(employer)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .note(request.getNote())
                .build();
        return SlotResponse.from(slotRepository.save(slot));
    }

    @Override
    @Transactional
    public SlotResponse updateSlot(Long slotId, SlotRequest request) {
        validateTimeRange(request.getStartTime(), request.getEndTime());
        InterviewSlot slot = findOwnedSlotOrThrow(slotId);
        if (slot.getBookedBy() != null) {
            throw new BadRequestException("Không thể sửa khung giờ đã có người đặt");
        }
        slot.setStartTime(request.getStartTime());
        slot.setEndTime(request.getEndTime());
        slot.setNote(request.getNote());
        return SlotResponse.from(slotRepository.save(slot));
    }

    @Override
    @Transactional
    public void cancelSlot(Long slotId) {
        InterviewSlot slot = findOwnedSlotOrThrow(slotId);
        if (slot.getBookedBy() != null) {
            throw new BadRequestException("Không thể hủy khung giờ đã có người đặt");
        }
        slot.setCancelled(true);
        slotRepository.save(slot);
    }

    private void validateTimeRange(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new BadRequestException("Khoảng thởi gian không hợp lệ");
        }
        if (start.isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Không thể tạo khung giờ trong quá khứ");
        }
    }

    private InterviewSlot findOwnedSlotOrThrow(Long slotId) {
        User current = SecurityUtils.getCurrentUser(userRepository);
        InterviewSlot slot = slotRepository.findByIdAndCancelledFalse(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khung giờ với id: " + slotId));
        if (!slot.getEmployer().getId().equals(current.getId())) {
            throw new BadRequestException("Bạn không có quyền thao tác trên khung giờ này");
        }
        return slot;
    }
}
