package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.model.dto.response.CategoryGroupsResponse;
import com.aiinterview.platform.model.repository.JobDescriptionRepository;
import com.aiinterview.platform.service.AdminCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminCategoryServiceImpl implements AdminCategoryService {

    private final JobDescriptionRepository jobRepository;

    @Override
    @Transactional(readOnly = true)
    public CategoryGroupsResponse getCategories() {
        List<CategoryGroupsResponse.Group> groups = List.of(
                group("department", "Phòng ban", jobRepository.countGroupedByDepartment()),
                group("experienceLevel", "Cấp kinh nghiệm", jobRepository.countGroupedByExperienceLevel()),
                group("employmentType", "Kiểu làm việc", jobRepository.countGroupedByEmploymentType()),
                group("workplaceModel", "Hình thức làm việc", jobRepository.countGroupedByWorkplaceModel()));
        return CategoryGroupsResponse.builder().groups(groups).build();
    }

    /** Rows are {@code [value, count]} — the enum column arrives as the enum itself, so stringify here. */
    private static CategoryGroupsResponse.Group group(String key, String label, List<Object[]> rows) {
        List<CategoryGroupsResponse.Value> values = new ArrayList<>();
        for (Object[] row : rows) {
            String value = String.valueOf(row[0]).trim();
            if (value.isEmpty()) {
                continue;
            }
            values.add(CategoryGroupsResponse.Value.builder()
                    .value(value)
                    .jobCount(((Number) row[1]).longValue())
                    .build());
        }
        values.sort(Comparator.comparingLong(CategoryGroupsResponse.Value::getJobCount)
                .reversed()
                .thenComparing(CategoryGroupsResponse.Value::getValue));
        return CategoryGroupsResponse.Group.builder().key(key).label(label).values(values).build();
    }
}
