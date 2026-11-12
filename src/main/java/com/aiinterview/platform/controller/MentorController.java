package com.aiinterview.platform.controller;

import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.response.MentorResponse;
import com.aiinterview.platform.service.MentorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Danh sách mentor/chuyên gia hiển thị trên trang công khai /mentors.
 * Dữ liệu lấy từ tài khoản có role INTERVIEWER đang hoạt động, kèm điểm đánh giá trung bình.
 */
@RestController
@RequestMapping("/api/v1/mentors")
@RequiredArgsConstructor
public class MentorController {

    private final MentorService mentorService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<MentorResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(mentorService.listMentors()));
    }
}
