package com.aiinterview.platform.service;

import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.entity.MentorPricing;

public interface MentorPricingService {
    MentorPricing getOrCreate(User mentor);
    MentorPricing update(User mentor, long minPrice, long maxPrice, long hourlyRate, String level, Boolean featured);
}
