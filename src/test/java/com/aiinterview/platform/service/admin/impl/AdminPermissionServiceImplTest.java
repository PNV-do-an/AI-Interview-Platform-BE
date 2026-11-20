package com.aiinterview.platform.service.admin.impl;

import com.aiinterview.platform.model.dto.response.PermissionMatrixResponse;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GET /api/v1/admin/permissions (spec 006) — ma trận quyền là CODE, không phải dữ liệu.
 * <p>
 * Vì vậy test ở đây không kiểm tra "đọc DB ra gì" mà kiểm tra hai thứ:
 * (1) hình dạng hợp đồng mà FE dựa vào (9 module, mỗi module 5 hành động, luôn đủ 4 role),
 * (2) những chỗ lệch pha giữa ma trận và @PreAuthorize thật sự đã từng gây bug — ví dụ
 * duyệt tin tuyển dụng không dành cho ROLE_INTERVIEWER.
 */
class AdminPermissionServiceImplTest {

    private UserRepository userRepository;
    private AdminPermissionServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        service = new AdminPermissionServiceImpl(userRepository);
    }

    @Test
    @DisplayName("Ma trận trả đúng 9 module theo thứ tự cố định của spec 006")
    void matrix_exposesNineModulesInFixedOrder() {
        when(userRepository.countLiveByRole()).thenReturn(List.of());

        PermissionMatrixResponse matrix = service.getMatrix();

        assertThat(matrix.getModules())
                .extracting(PermissionMatrixResponse.ModulePermission::getKey)
                .containsExactly("candidate-profile", "jobs", "applications", "slots",
                        "mock-interviews", "packages-orders", "reviews", "bug-reports",
                        "admin-console");
    }

    @Test
    @DisplayName("Chỉ admin-console là adminOnly, 8 module còn lại là module nghiệp vụ")
    void matrix_onlyAdminConsoleIsAdminOnly() {
        when(userRepository.countLiveByRole()).thenReturn(List.of());

        PermissionMatrixResponse matrix = service.getMatrix();

        assertThat(matrix.getModules())
                .filteredOn(PermissionMatrixResponse.ModulePermission::isAdminOnly)
                .extracting(PermissionMatrixResponse.ModulePermission::getKey)
                .containsExactly("admin-console");
    }

    @Test
    @DisplayName("Mỗi module có đủ 5 hành động VIEW/CREATE/UPDATE/DELETE/APPROVE, đúng 1 lần")
    void matrix_everyModuleHasTheFiveActions() {
        when(userRepository.countLiveByRole()).thenReturn(List.of());

        PermissionMatrixResponse matrix = service.getMatrix();

        assertThat(matrix.getModules()).allSatisfy(module ->
                assertThat(module.getActions())
                        .extracting(PermissionMatrixResponse.ActionPermission::getAction)
                        .containsExactlyInAnyOrder("VIEW", "CREATE", "UPDATE", "DELETE", "APPROVE"));
    }

    @Test
    @DisplayName("admin-console: cả 5 hành động chỉ cấp cho ROLE_ADMIN")
    void matrix_adminConsoleGrantedOnlyToAdmin() {
        when(userRepository.countLiveByRole()).thenReturn(List.of());

        PermissionMatrixResponse.ModulePermission console = module("admin-console");

        assertThat(console.getActions()).allSatisfy(action ->
                assertThat(allowedRoles(action)).containsExactly("ROLE_ADMIN"));
    }

    @Test
    @DisplayName("Duyệt tin tuyển dụng (PATCH /jobs/{id}/review) không cấp cho nhà tuyển dụng")
    void matrix_jobsApproveExcludesInterviewer() {
        when(userRepository.countLiveByRole()).thenReturn(List.of());

        PermissionMatrixResponse.ActionPermission approve = action("jobs", "APPROVE");

        // @PreAuthorize của endpoint duyệt chỉ cho STAFF/ADMIN; nếu ma trận lỡ cấp cho
        // INTERVIEWER thì FE sẽ hiện nút Duyệt cho người không có quyền.
        assertThat(approve.getGranted())
                .containsEntry("ROLE_STAFF", true)
                .containsEntry("ROLE_ADMIN", true)
                .containsEntry("ROLE_INTERVIEWER", false)
                .containsEntry("ROLE_USER", false);
    }

    @Test
    @DisplayName("APPROVE rỗng (roles()) vẫn phải hiện ra với granted toàn false, không bị ẩn")
    void matrix_emptyPermissionStillAppearsWithAllFalse() {
        when(userRepository.countLiveByRole()).thenReturn(List.of());

        PermissionMatrixResponse.ActionPermission approve = action("candidate-profile", "APPROVE");

        assertThat(approve.getGranted()).hasSize(4).containsOnly(
                entry("ROLE_USER", false), entry("ROLE_INTERVIEWER", false),
                entry("ROLE_STAFF", false), entry("ROLE_ADMIN", false));
    }

    @Test
    @DisplayName("Mỗi hành động luôn kèm nhãn tiếng Việt để FE không phải tự dịch")
    void matrix_everyActionHasVietnameseLabel() {
        when(userRepository.countLiveByRole()).thenReturn(List.of());

        Map<String, String> expected = Map.of(
                "VIEW", "Xem", "CREATE", "Tạo", "UPDATE", "Sửa",
                "DELETE", "Xóa", "APPROVE", "Duyệt");

        assertThat(service.getMatrix().getModules())
                .flatExtracting(PermissionMatrixResponse.ModulePermission::getActions)
                .allSatisfy(a -> assertThat(a.getLabel()).isEqualTo(expected.get(a.getAction())));
    }

    @Test
    @DisplayName("Cột role luôn đủ 4 role và số lượng user lấy từ countLiveByRole")
    void matrix_roleSummariesCarryLiveCounts() {
        when(userRepository.countLiveByRole()).thenReturn(List.of(
                new Object[]{Role.ROLE_USER, 12L},
                new Object[]{Role.ROLE_INTERVIEWER, 3L},
                new Object[]{Role.ROLE_ADMIN, 1L}));

        PermissionMatrixResponse matrix = service.getMatrix();

        assertThat(matrix.getRoles()).hasSize(4);
        assertThat(matrix.getRoles()).allMatch(PermissionMatrixResponse.RoleSummary::isBuiltin);
        assertThat(countOf(matrix, "ROLE_USER")).isEqualTo(12L);
        assertThat(countOf(matrix, "ROLE_INTERVIEWER")).isEqualTo(3L);
        assertThat(countOf(matrix, "ROLE_ADMIN")).isEqualTo(1L);
        // Không có dòng nào trong DB -> vẫn trả 0, không phải null.
        assertThat(countOf(matrix, "ROLE_STAFF")).isZero();
        assertThat(matrix.getRoles())
                .extracting(PermissionMatrixResponse.RoleSummary::getLabel)
                .containsExactlyInAnyOrder("Ứng viên", "Nhà tuyển dụng",
                        "Nhân viên vận hành", "Quản trị viên");
    }

    @Test
    @DisplayName("Dòng group-by trả số dạng Integer vẫn đọc được (driver có thể trả Integer)")
    void matrix_roleCountAcceptsAnyNumberType() {
        when(userRepository.countLiveByRole()).thenReturn(List.<Object[]>of(
                new Object[]{Role.ROLE_USER, 7}));

        assertThat(countOf(service.getMatrix(), "ROLE_USER")).isEqualTo(7L);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private PermissionMatrixResponse matrix() {
        return service.getMatrix();
    }

    private PermissionMatrixResponse.ModulePermission module(String key) {
        return matrix().getModules().stream()
                .filter(m -> m.getKey().equals(key))
                .findFirst()
                .orElseThrow();
    }

    private PermissionMatrixResponse.ActionPermission action(String moduleKey, String action) {
        return module(moduleKey).getActions().stream()
                .filter(a -> a.getAction().equals(action))
                .findFirst()
                .orElseThrow();
    }

    private static Set<String> allowedRoles(PermissionMatrixResponse.ActionPermission action) {
        return action.getGranted().entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private static long countOf(PermissionMatrixResponse matrix, String role) {
        return matrix.getRoles().stream()
                .filter(r -> r.getRole().equals(role))
                .findFirst()
                .orElseThrow()
                .getUserCount();
    }

    private static Map.Entry<String, Boolean> entry(String k, Boolean v) {
        return Map.entry(k, v);
    }
}
