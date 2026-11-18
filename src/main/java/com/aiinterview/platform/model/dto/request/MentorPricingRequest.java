package com.aiinterview.platform.model.dto.request;

import lombok.Data;

@Data
public class MentorPricingRequest {
    private long minPrice;
    private long maxPrice;
    private long hourlyRate;
    private String level;
    private Boolean featured;
}
