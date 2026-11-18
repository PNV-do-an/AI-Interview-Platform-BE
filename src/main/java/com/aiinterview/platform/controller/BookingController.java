package com.aiinterview.platform.controller;

import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.request.BookingRequest;
import com.aiinterview.platform.model.dto.response.BookingResponse;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.security.utils.SecurityUtils;
import com.aiinterview.platform.service.EscrowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final EscrowService escrowService;
    private final UserRepository userRepository;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BookingResponse>> book(@Valid @RequestBody BookingRequest req) {
        User u = SecurityUtils.getCurrentUser(userRepository);
        return ResponseEntity.ok(ApiResponse.success(BookingResponse.from(
                escrowService.bookSlot(u, req.getSlotId(), req.getAmount(), req.getIdempotencyKey()))));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BookingResponse>> complete(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(BookingResponse.from(escrowService.completeBooking(id))));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BookingResponse>> cancel(@PathVariable Long id, @RequestParam(required = false) String reason) {
        User u = SecurityUtils.getCurrentUser(userRepository);
        return ResponseEntity.ok(ApiResponse.success(BookingResponse.from(escrowService.cancelBooking(u, id, reason))));
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<BookingResponse>>> my() {
        User u = SecurityUtils.getCurrentUser(userRepository);
        return ResponseEntity.ok(ApiResponse.success(escrowService.getMyBookings(u).stream().map(BookingResponse::from).collect(Collectors.toList())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BookingResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(BookingResponse.from(escrowService.getBooking(id))));
    }
}
