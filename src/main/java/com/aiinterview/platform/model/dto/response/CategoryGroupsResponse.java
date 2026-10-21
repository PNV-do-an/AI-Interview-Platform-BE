package com.aiinterview.platform.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** GET /api/v1/admin/categories — what employers actually type into the four taxonomy fields (009 FR-008). */
@Data
@Builder
public class CategoryGroupsResponse {

    private List<Group> groups;

    @Data
    @Builder
    public static class Group {
        /** Query parameter name on the job wizard: department | experienceLevel | employmentType | workplaceModel. */
        private String key;
        /** Vietnamese label for the admin screen. */
        private String label;
        /** Busiest value first; blank/null values of live jobs are left out. */
        private List<Value> values;
    }

    @Data
    @Builder
    public static class Value {
        private String value;
        private long jobCount;
    }
}
