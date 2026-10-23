package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.InterviewSlot;
import com.aiinterview.platform.model.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InterviewSlotRepository extends JpaRepository<InterviewSlot, Long> {

    List<InterviewSlot> findByEmployerAndCancelledFalseOrderByStartTimeAsc(User employer);

    List<InterviewSlot> findByEmployerAndBookedByIsNullAndCancelledFalseAndStartTimeAfterOrderByStartTimeAsc(
            User employer, LocalDateTime from);

    Optional<InterviewSlot> findByIdAndCancelledFalse(Long id);

    /** Slots booked for the reminder window [from, to) — FR-SCH-03. */
    List<InterviewSlot> findByBookedByIsNotNullAndCancelledFalseAndStartTimeGreaterThanEqualAndStartTimeLessThan(
            LocalDateTime from, LocalDateTime to);

    List<InterviewSlot> findByBookedByOrderByStartTimeAsc(User candidate);

    Page<InterviewSlot> findByBookedByAndCancelledFalseOrderByStartTimeDesc(User candidate, Pageable pageable);

    long countByBookedByAndCancelledFalse(User candidate);

    /** Booked, not cancelled slots that start in the window — the "formal interview" series (009 FR-001). */
    @Query(value = "SELECT DATE_FORMAT(start_time, '%Y-%m'), COUNT(*) FROM interview_slots "
            + "WHERE booked_by IS NOT NULL AND cancelled = FALSE AND start_time >= :from "
            + "GROUP BY DATE_FORMAT(start_time, '%Y-%m')", nativeQuery = true)
    List<Object[]> countBookedSlotsByMonthSince(@Param("from") LocalDateTime from);
}
