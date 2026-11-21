package com.aiinterview.platform.model.dto.response;

import com.aiinterview.platform.model.entity.EscrowBooking;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class BookingResponse {
    private Long id;
    private Long candidateId;
    private Long mentorId;
    private Long slotId;
    private Long amount;
    private Long mentorAmount;
    private Long commissionAmount;
    private BigDecimal commissionRate;
    private String status;
    private LocalDateTime heldAt;
    private LocalDateTime releasedAt;
    private LocalDateTime complaintDeadline;

    public static BookingResponse from(EscrowBooking b) {
        return BookingResponse.builder()
                .id(b.getId())
                .candidateId(b.getCandidate().getId())
                .mentorId(b.getMentor().getId())
                .slotId(b.getSlot() == null ? null : b.getSlot().getId())
                .amount(b.getAmount())
                .mentorAmount(b.getMentorAmount())
                .commissionAmount(b.getCommissionAmount())
                .commissionRate(b.getCommissionRate())
                .status(b.getStatus().name())
                .heldAt(b.getHeldAt())
                .releasedAt(b.getReleasedAt())
                .complaintDeadline(b.getComplaintDeadline())
                .build();
    }
}
