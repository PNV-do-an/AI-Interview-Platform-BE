package com.aiinterview.platform.service;

import com.aiinterview.platform.model.dto.response.CategoryGroupsResponse;

public interface AdminCategoryService {

    /** Read-only taxonomy built from the values live jobs already carry (spec 009 FR-008). */
    CategoryGroupsResponse getCategories();
}
