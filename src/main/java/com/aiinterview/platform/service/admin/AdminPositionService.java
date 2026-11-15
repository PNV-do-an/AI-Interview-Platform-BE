package com.aiinterview.platform.service.admin;

import com.aiinterview.platform.model.dto.request.PositionRequest;
import com.aiinterview.platform.model.dto.response.AdminPositionResponse;
import org.springframework.data.domain.Page;

import java.util.List;

public interface AdminPositionService {

    Page<AdminPositionResponse> getPositions(int page, int size, String search, Boolean active);

    AdminPositionResponse getPosition(Long id);

    AdminPositionResponse createPosition(PositionRequest request);

    AdminPositionResponse updatePosition(Long id, PositionRequest request);

    AdminPositionResponse changeStatus(Long id, boolean active);

    /** Preview ky nang AI trich xuat tu JD truoc khi admin luu vi tri. */
    List<String> extractSkills(String jdText);
}
