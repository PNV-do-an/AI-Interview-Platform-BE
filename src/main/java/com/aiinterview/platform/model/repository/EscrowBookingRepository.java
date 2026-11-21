package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.EscrowBooking;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.EscrowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EscrowBookingRepository extends JpaRepository<EscrowBooking, Long> {
    List<EscrowBooking> findByCandidateOrderByHeldAtDesc(User candidate);
    List<EscrowBooking> findByMentorOrderByHeldAtDesc(User mentor);
    List<EscrowBooking> findByStatusAndComplaintDeadlineBefore(EscrowStatus status, LocalDateTime deadline);
    Optional<EscrowBooking> findByIdempotencyKey(String idempotencyKey);
    long countByCandidateAndMentor(User candidate, User mentor);
}
