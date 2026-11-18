package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.model.entity.EscrowBooking;
import com.aiinterview.platform.model.entity.InterviewSlot;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.EscrowStatus;
import com.aiinterview.platform.model.repository.EscrowBookingRepository;
import com.aiinterview.platform.model.repository.InterviewSlotRepository;
import com.aiinterview.platform.model.repository.MentorPricingRepository;
import com.aiinterview.platform.service.EscrowService;
import com.aiinterview.platform.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EscrowServiceImpl implements EscrowService {

    private final EscrowBookingRepository bookingRepository;
    private final InterviewSlotRepository slotRepository;
    private final MentorPricingRepository pricingRepository;
    private final WalletService walletService;

    @Override
    @Transactional
    public EscrowBooking bookSlot(User candidate, Long slotId, long amount, String idempotencyKey) {
        if (idempotencyKey != null) {
            var existing = bookingRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) return existing.get();
        }
        InterviewSlot slot = slotRepository.findByIdAndCancelledFalse(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay khung gio"));
        if (slot.getBookedBy() != null) throw new BadRequestException("Khung gio da duoc dat");
        if (slot.getEmployer().getId().equals(candidate.getId())) throw new BadRequestException("Khong the dat khung gio cua chinh minh");

        User mentor = slot.getEmployer();
        // pricing validation
        var pricing = pricingRepository.findByMentor(mentor).orElse(null);
        if (pricing != null) {
            if (amount < pricing.getMinPrice()) throw new BadRequestException("Gia thap hon muc san");
            if (amount > pricing.getMaxPrice()) throw new BadRequestException("Gia vuot muc tran");
        }
        if (amount <= 0) throw new BadRequestException("So tien phai lon hon 0");

        // commission rate locked at booking
        long prior = bookingRepository.countByCandidateAndMentor(candidate, mentor);
        BigDecimal rate = (prior == 0) ? new BigDecimal("0.15") : new BigDecimal("0.10");
        long commission = Math.round(amount * rate.doubleValue());
        long mentorAmount = amount - commission;

        // hold funds
        walletService.holdEscrow(candidate, amount);

        slot.setBookedBy(candidate);
        slot.setBookedAt(LocalDateTime.now());
        slotRepository.save(slot);

        EscrowBooking booking = EscrowBooking.builder()
                .candidate(candidate)
                .mentor(mentor)
                .slot(slot)
                .amount(amount)
                .mentorAmount(mentorAmount)
                .commissionAmount(commission)
                .commissionRate(rate)
                .status(EscrowStatus.HELD)
                .idempotencyKey(idempotencyKey)
                .build();
        booking = bookingRepository.save(booking);
        // update ledger reference id
        return booking;
    }

    @Override
    @Transactional
    public EscrowBooking completeBooking(Long bookingId) {
        EscrowBooking b = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay booking"));
        if (b.getStatus() != EscrowStatus.HELD) throw new BadRequestException("Booking khong o trang thai giu tien");
        // release to mentor
        walletService.releaseEscrowHold(b.getCandidate(), b.getAmount());
        // candidate escrow balance decreases; mentor available increases
        walletService.creditAvailable(b.getMentor(), b.getMentorAmount(), "ESCROW_RELEASE", "ESCROW_BOOKING", b.getId(),
                "REL-" + b.getId(), "Giai phong escrow cho mentor");
        // platform commission ledger (credit to? platform has no wallet; record as COMMISSION with null? skip wallet, but ledger for candidate? We'll record a transaction for mentor? Instead record commission in booking only)
        b.setStatus(EscrowStatus.RELEASED);
        b.setReleasedAt(LocalDateTime.now());
        return bookingRepository.save(b);
    }

    @Override
    @Transactional
    public EscrowBooking cancelBooking(User requester, Long bookingId, String reason) {
        EscrowBooking b = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay booking"));
        if (b.getStatus() != EscrowStatus.HELD) throw new BadRequestException("Booking khong o trang thai giu tien");
        boolean isCandidate = b.getCandidate().getId().equals(requester.getId());
        boolean isMentor = b.getMentor().getId().equals(requester.getId());
        if (!isCandidate && !isMentor) throw new BadRequestException("Khong co quyen huy");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = b.getSlot().getStartTime();
        long hoursBefore = java.time.Duration.between(now, start).toHours();

        if (isMentor) {
            // mentor cancels -> full refund candidate
            refundCandidate(b, b.getAmount(), "Mentor huy lich");
            b.setStatus(EscrowStatus.REFUNDED_FULL);
            b.setCancelReason(reason == null ? "Mentor huy" : reason);
            // free slot? mark cancelled
            b.getSlot().setCancelled(true);
            slotRepository.save(b.getSlot());
        } else {
            if (hoursBefore >= 24) {
                refundCandidate(b, b.getAmount(), "Huy truoc 24h");
                b.setStatus(EscrowStatus.REFUNDED_FULL);
            } else {
                // partial: hold amount (e.g. 50%) to mentor, rest refund
                long hold = Math.round(b.getAmount() * 0.5);
                long refund = b.getAmount() - hold;
                refundCandidate(b, refund, "Huy sat gio");
                walletService.creditAvailable(b.getMentor(), hold, "ESCROW_RELEASE", "ESCROW_BOOKING", b.getId(),
                        "PARTIAL-" + b.getId(), "Giu mot phan do huy sat gio");
                b.setStatus(EscrowStatus.REFUNDED_PARTIAL);
            }
            b.setCancelReason(reason);
            b.getSlot().setBookedBy(null);
            slotRepository.save(b.getSlot());
        }
        walletService.releaseEscrowHold(b.getCandidate(), b.getAmount());
        return bookingRepository.save(b);
    }

    private void refundCandidate(EscrowBooking b, long amount, String desc) {
        walletService.creditAvailable(b.getCandidate(), amount, "REFUND", "ESCROW_BOOKING", b.getId(),
                "RF-" + b.getId(), desc);
    }

    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void releaseDueEscrows() {
        List<EscrowBooking> due = bookingRepository.findByStatusAndComplaintDeadlineBefore(EscrowStatus.HELD, LocalDateTime.now());
        for (EscrowBooking b : due) {
            try {
                completeBooking(b.getId());
            } catch (Exception e) {
                // ignore
            }
        }
    }

    @Override
    public List<EscrowBooking> getMyBookings(User user) {
        // candidate or mentor
        List<EscrowBooking> c = bookingRepository.findByCandidateOrderByHeldAtDesc(user);
        List<EscrowBooking> m = bookingRepository.findByMentorOrderByHeldAtDesc(user);
        c.addAll(m);
        return c;
    }

    @Override
    public EscrowBooking getBooking(Long id) {
        return bookingRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Khong tim thay booking"));
    }
}
