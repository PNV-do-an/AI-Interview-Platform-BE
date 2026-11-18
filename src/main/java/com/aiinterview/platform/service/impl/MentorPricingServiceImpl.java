package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.model.entity.MentorPricing;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.repository.MentorPricingRepository;
import com.aiinterview.platform.service.MentorPricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MentorPricingServiceImpl implements MentorPricingService {

    private final MentorPricingRepository repository;

    @Override
    @Transactional
    public MentorPricing getOrCreate(User mentor) {
        return repository.findByMentor(mentor).orElseGet(() -> repository.save(MentorPricing.builder().mentor(mentor).build()));
    }

    @Override
    @Transactional
    public MentorPricing update(User mentor, long minPrice, long maxPrice, long hourlyRate, String level, Boolean featured) {
        if (minPrice < 0 || maxPrice < minPrice) throw new BadRequestException("Khoang gia khong hop le");
        MentorPricing p = repository.findByMentor(mentor).orElseGet(() -> MentorPricing.builder().mentor(mentor).build());
        p.setMinPrice(minPrice);
        p.setMaxPrice(maxPrice);
        p.setHourlyRate(hourlyRate);
        p.setLevel(level == null ? p.getLevel() : level);
        if (featured != null) p.setFeatured(featured);
        return repository.save(p);
    }
}
