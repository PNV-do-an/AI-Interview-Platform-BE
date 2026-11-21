package com.aiinterview.platform.controller.interview;

import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.request.SlotRequest;
import com.aiinterview.platform.model.dto.response.SlotResponse;
import com.aiinterview.platform.service.interview.SlotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/slots")
@RequiredArgsConstructor
public class SlotController {

    private final SlotService slotService;

    /** GET /api/v1/slots/my — employer's own slots */
    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('INTERVIEWER','ADMIN','STAFF')")
    public ResponseEntity<ApiResponse<List<SlotResponse>>> getMySlots() {
        return ResponseEntity.ok(ApiResponse.success(slotService.getMySlots()));
    }

    /** GET /api/v1/slots/available?employerId=1 — public, for candidates booking */
    @GetMapping("/available")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<SlotResponse>>> getAvailableSlots(@RequestParam Long employerId) {
        return ResponseEntity.ok(ApiResponse.success(slotService.getAvailableSlots(employerId)));
    }

    /** POST /api/v1/slots */
    @PostMapping
    @PreAuthorize("hasAnyRole('INTERVIEWER','ADMIN','STAFF')")
    public ResponseEntity<ApiResponse<SlotResponse>> createSlot(@Valid @RequestBody SlotRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Khung giờ đã được tạo", slotService.createSlot(request)));
    }

    /** PUT /api/v1/slots/{id} */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('INTERVIEWER','ADMIN','STAFF')")
    public ResponseEntity<ApiResponse<SlotResponse>> updateSlot(
            @PathVariable Long id, @Valid @RequestBody SlotRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Khung giờ đã được cập nhật", slotService.updateSlot(id, request)));
    }

    /** DELETE /api/v1/slots/{id} */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('INTERVIEWER','ADMIN','STAFF')")
    public ResponseEntity<ApiResponse<Void>> cancelSlot(@PathVariable Long id) {
        slotService.cancelSlot(id);
        return ResponseEntity.ok(ApiResponse.success("Khung giờ đã được hủy", null));
    }
}
