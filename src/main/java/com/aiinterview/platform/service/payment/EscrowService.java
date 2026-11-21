package com.aiinterview.platform.service.payment;

import com.aiinterview.platform.model.entity.EscrowBooking;
import com.aiinterview.platform.model.entity.User;

import java.util.List;

public interface EscrowService {
    EscrowBooking bookSlot(User candidate, Long slotId, long amount, String idempotencyKey);
    EscrowBooking completeBooking(Long bookingId);
    EscrowBooking cancelBooking(User requester, Long bookingId, String reason);
    void releaseDueEscrows();
    List<EscrowBooking> getMyBookings(User user);
    EscrowBooking getBooking(Long id);
}
