package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.model.dto.response.PermissionMatrixResponse;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.service.AdminPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The matrix is CODE, not data, on purpose (spec 006 D2): the only source of truth is the
 * @PreAuthorize annotations themselves. Each module entry names the controllers it
 * describes, and the integration test asserts the matrix against real HTTP calls so any
 * drift between this file and the annotations fails the build.
 */
@Service
@RequiredArgsConstructor
public class AdminPermissionServiceImpl implements AdminPermissionService {

    /** "Granted" means: the role passes @PreAuthorize on at least one endpoint of that action. */
    private static final List<ModuleDef> MODULES = List.of(
            new ModuleDef("candidate-profile", "Hồ sơ ứng viên & CV", false, Map.of(
                    "VIEW", roles(Role.ROLE_USER),
                    "CREATE", roles(Role.ROLE_USER),   // CvController POST /analyze, CoverLetterController POST /generate
                    "UPDATE", roles(Role.ROLE_USER),   // CandidateProfileController PUT, CoverLetterController PATCH
                    "DELETE", roles(Role.ROLE_USER),   // CoverLetterController DELETE
                    "APPROVE", roles())),
            new ModuleDef("jobs", "Tin tuyển dụng", false, Map.of(
                    "VIEW", roles(Role.ROLE_USER, Role.ROLE_INTERVIEWER, Role.ROLE_STAFF, Role.ROLE_ADMIN),
                    "CREATE", roles(Role.ROLE_INTERVIEWER, Role.ROLE_STAFF, Role.ROLE_ADMIN),
                    "UPDATE", roles(Role.ROLE_INTERVIEWER, Role.ROLE_STAFF, Role.ROLE_ADMIN),
                    "DELETE", roles(Role.ROLE_INTERVIEWER, Role.ROLE_STAFF, Role.ROLE_ADMIN),
                    "APPROVE", roles(Role.ROLE_STAFF, Role.ROLE_ADMIN))),   // PATCH /jobs/{id}/review
            new ModuleDef("applications", "Quản lý ứng tuyển", false, Map.of(
                    "VIEW", roles(Role.ROLE_USER, Role.ROLE_INTERVIEWER, Role.ROLE_STAFF, Role.ROLE_ADMIN),
                    "CREATE", roles(Role.ROLE_USER),   // POST /applications/jobs/{jobId}
                    "UPDATE", roles(Role.ROLE_USER, Role.ROLE_INTERVIEWER, Role.ROLE_STAFF, Role.ROLE_ADMIN),
                    "DELETE", roles(),
                    "APPROVE", roles(Role.ROLE_INTERVIEWER, Role.ROLE_STAFF, Role.ROLE_ADMIN))),
            new ModuleDef("slots", "Lịch phỏng vấn", false, Map.of(
                    "VIEW", roles(Role.ROLE_USER, Role.ROLE_INTERVIEWER, Role.ROLE_STAFF, Role.ROLE_ADMIN),
                    "CREATE", roles(Role.ROLE_INTERVIEWER, Role.ROLE_STAFF, Role.ROLE_ADMIN),
                    "UPDATE", roles(Role.ROLE_INTERVIEWER, Role.ROLE_STAFF, Role.ROLE_ADMIN),
                    "DELETE", roles(Role.ROLE_INTERVIEWER, Role.ROLE_STAFF, Role.ROLE_ADMIN),
                    "APPROVE", roles())),
            new ModuleDef("mock-interviews", "Phỏng vấn giả lập AI", false, Map.of(
                    "VIEW", roles(Role.ROLE_USER),
                    "CREATE", roles(Role.ROLE_USER),
                    "UPDATE", roles(Role.ROLE_USER),   // answer / finish
                    "DELETE", roles(),
                    "APPROVE", roles())),
            new ModuleDef("packages-orders", "Gói dịch vụ & Đơn hàng", false, Map.of(
                    "VIEW", roles(Role.ROLE_USER, Role.ROLE_INTERVIEWER, Role.ROLE_STAFF, Role.ROLE_ADMIN),
                    "CREATE", roles(Role.ROLE_INTERVIEWER, Role.ROLE_STAFF, Role.ROLE_ADMIN),  // orders POST; packages POST admin/staff
                    "UPDATE", roles(Role.ROLE_STAFF, Role.ROLE_ADMIN),  // package toggle-active
                    "DELETE", roles(Role.ROLE_STAFF, Role.ROLE_ADMIN),
                    "APPROVE", roles())),
            new ModuleDef("reviews", "Đánh giá & Phản hồi", false, Map.of(
                    "VIEW", roles(Role.ROLE_USER, Role.ROLE_INTERVIEWER, Role.ROLE_STAFF, Role.ROLE_ADMIN),
                    "CREATE", roles(Role.ROLE_USER),
                    "UPDATE", roles(Role.ROLE_STAFF, Role.ROLE_ADMIN),  // reply
                    "DELETE", roles(),
                    "APPROVE", roles())),
            new ModuleDef("bug-reports", "Báo lỗi", false, Map.of(
                    "VIEW", roles(Role.ROLE_USER, Role.ROLE_INTERVIEWER, Role.ROLE_STAFF, Role.ROLE_ADMIN),
                    "CREATE", roles(Role.ROLE_USER, Role.ROLE_INTERVIEWER, Role.ROLE_STAFF, Role.ROLE_ADMIN),
                    "UPDATE", roles(Role.ROLE_STAFF, Role.ROLE_ADMIN),
                    "DELETE", roles(),
                    "APPROVE", roles())),
            new ModuleDef("admin-console", "Bảng điều khiển quản trị", true, Map.of(
                    "VIEW", roles(Role.ROLE_ADMIN),
                    "CREATE", roles(Role.ROLE_ADMIN),
                    "UPDATE", roles(Role.ROLE_ADMIN),
                    "DELETE", roles(Role.ROLE_ADMIN),
                    "APPROVE", roles(Role.ROLE_ADMIN))));

    private static final Map<String, String> ACTION_LABELS = Map.of(
            "VIEW", "Xem",
            "CREATE", "Tạo",
            "UPDATE", "Sửa",
            "DELETE", "Xóa",
            "APPROVE", "Duyệt");

    private static final Map<String, String> ROLE_LABELS = Map.of(
            "ROLE_USER", "Ứng viên",
            "ROLE_INTERVIEWER", "Nhà tuyển dụng",
            "ROLE_STAFF", "Nhân viên vận hành",
            "ROLE_ADMIN", "Quản trị viên");

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public PermissionMatrixResponse getMatrix() {
        Map<String, Long> userCounts = new LinkedHashMap<>();
        ROLE_LABELS.keySet().forEach(role -> userCounts.put(role, 0L));
        for (Object[] row : userRepository.countLiveByRole()) {
            Role role = (Role) row[0];
            userCounts.put(role.name(), row[1] instanceof Number n ? n.longValue() : 0L);
        }

        List<PermissionMatrixResponse.RoleSummary> roleSummaries = new ArrayList<>();
        for (Map.Entry<String, String> entry : ROLE_LABELS.entrySet()) {
            roleSummaries.add(PermissionMatrixResponse.RoleSummary.builder()
                    .role(entry.getKey())
                    .label(entry.getValue())
                    .userCount(userCounts.get(entry.getKey()))
                    .builtin(true)
                    .build());
        }

        List<PermissionMatrixResponse.ModulePermission> modules = new ArrayList<>();
        for (ModuleDef moduleDef : MODULES) {
            List<PermissionMatrixResponse.ActionPermission> actions = new ArrayList<>();
            for (Map.Entry<String, java.util.Set<String>> action : moduleDef.granted().entrySet()) {
                Map<String, Boolean> granted = new LinkedHashMap<>();
                ROLE_LABELS.keySet().forEach(role -> granted.put(role, action.getValue().contains(role)));
                actions.add(PermissionMatrixResponse.ActionPermission.builder()
                        .action(action.getKey())
                        .label(ACTION_LABELS.get(action.getKey()))
                        .granted(granted)
                        .build());
            }
            modules.add(PermissionMatrixResponse.ModulePermission.builder()
                    .key(moduleDef.key())
                    .label(moduleDef.label())
                    .adminOnly(moduleDef.adminOnly())
                    .actions(actions)
                    .build());
        }
        return PermissionMatrixResponse.builder().roles(roleSummaries).modules(modules).build();
    }

    private static java.util.Set<String> roles(Role... roles) {
        return java.util.Arrays.stream(roles).map(Enum::name)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private record ModuleDef(String key, String label, boolean adminOnly,
                             Map<String, java.util.Set<String>> granted) {
    }
}
