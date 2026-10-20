package com.aiinterview.platform.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** GET /api/v1/admin/permissions/matrix — a static, read-only description of the real
 *  hard-coded permissions (spec 006: RBAC động để kỳ sau). */
@Data
@Builder
public class PermissionMatrixResponse {

    private List<RoleSummary> roles;
    private List<ModulePermission> modules;

    @Data
    @Builder
    public static class RoleSummary {
        private String role;
        private String label;
        private long userCount;
        private boolean builtin;
    }

    @Data
    @Builder
    public static class ModulePermission {
        private String key;
        private String label;
        /** True for modules only admins may touch — FE renders the row locked, as the mockup does. */
        private boolean adminOnly;
        /** 5 actions × 4 roles, every role key always present. */
        private List<ActionPermission> actions;
    }

    @Data
    @Builder
    public static class ActionPermission {
        private String action;
        private String label;
        /** ROLE_USER → granted? etc.; all four keys present, no nulls. */
        private java.util.Map<String, Boolean> granted;
    }
}
