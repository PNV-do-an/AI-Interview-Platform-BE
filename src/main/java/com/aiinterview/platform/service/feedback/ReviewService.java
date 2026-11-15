package com.aiinterview.platform.service.feedback;

import com.aiinterview.platform.model.dto.request.ReviewRequest;
import com.aiinterview.platform.model.dto.response.ReviewListResponse;
import com.aiinterview.platform.model.dto.response.ReviewResponse;
import com.aiinterview.platform.model.entity.User;

import java.util.List;

public interface ReviewService {

    ReviewResponse create(User reviewer, ReviewRequest request);

    ReviewListResponse listByTarget(Long targetId);

    List<ReviewResponse> listMine(User reviewer);

    List<ReviewResponse> listAll();

    ReviewResponse reply(User staff, Long reviewId, String reply);
}
