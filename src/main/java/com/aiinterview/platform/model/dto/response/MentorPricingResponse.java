package com.aiinterview.platform.model.dto.response;

import com.aiinterview.platform.model.entity.MentorPricing;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MentorPricingResponse {
    private Long mentorId;
    private Long minPrice;
    private Long maxPrice;
    private Long hourlyRate;
    private String level;
    private Boolean featured;

    public static MentorPricingResponse from(MentorPricing p) {
        return MentorPricingResponse.builder()
                .mentorId(p.getMentor().getId())
                .minPrice(p.getMinPrice())
                .maxPrice(p.getMaxPrice())
                .hourlyRate(p.getHourlyRate())
                .level(p.getLevel())
                .featured(p.getFeatured())
                .build();
    }
}
