package com.aiinterview.platform.service;

import com.aiinterview.platform.model.dto.response.PositionRecommendationResponse;

import java.util.List;

public interface PositionRecommendationService {

    /** Xep hang vi tri theo so ky nang trong CV trung voi ky nang yeu cau cua vi tri. */
    List<PositionRecommendationResponse> recommend(Long userId, int limit);

    /** Chi tiet do khop cua mot vi tri voi CV hien tai. */
    PositionRecommendationResponse matchPosition(Long userId, Long positionId);
}
