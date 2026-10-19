package com.aiinterview.platform.controller;

import com.aiinterview.platform.common.exception.MailDeliveryException;
import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.request.ChangeRoleRequest;
import com.aiinterview.platform.model.dto.response.UserDetailResponse;
import com.aiinterview.platform.model.dto.response.UserSummaryResponse;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.AdminAuditLogRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.service.EmailService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Admin User Controller Integration Tests")
class AdminUserControllerIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.mail.test-connection", () -> false);
    }

    @MockBean
    private EmailService emailService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AdminAuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User adminUser;
    private User testUser;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
        // login_attempts is keyed by email and survives deleteAll(); lastLoginAt must start empty.
        jdbcTemplate.update("delete from login_attempts");

        // Create admin user
        adminUser = User.builder()
                .email("admin@example.com")
                .password(passwordEncoder.encode("password123"))
                .fullName("Admin User")
                .role(Role.ROLE_ADMIN)
                .locked(false)
                .build();
        adminUser = userRepository.save(adminUser);

        // Create test user
        testUser = User.builder()
                .email("user@example.com")
                .password(passwordEncoder.encode("password123"))
                .fullName("Test User")
                .role(Role.ROLE_USER)
                .locked(false)
                .build();
        testUser = userRepository.save(testUser);
    }

    // ==================== Get Users Tests ====================

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Should get all users with pagination")
    void testGetAllUsers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                .param("page", "0")
                .param("size", "20")
                .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(greaterThanOrEqualTo(2)));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Should get users with search filter")
    void testGetUsersWithSearch() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                .param("page", "0")
                .param("size", "20")
                .param("search", "admin")
                .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Should get users with status filter")
    void testGetUsersWithStatusFilter() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                .param("page", "0")
                .param("size", "20")
                .param("status", "ACTIVE")
                .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Should get users with role filter")
    void testGetUsersWithRoleFilter() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                .param("page", "0")
                .param("size", "20")
                .param("role", "ROLE_USER")
                .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ==================== Get User Detail Tests ====================

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Should get user detail by ID")
    void testGetUserDetail() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/{id}", testUser.getId())
                .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(testUser.getEmail()))
                .andExpect(jsonPath("$.data.fullName").value(testUser.getFullName()));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Should return 404 when user not found")
    void testGetUserDetail_NotFound() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/99999")
                .with(csrf()))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ==================== Lock/Unlock User Tests ====================

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Should lock user successfully")
    void testLockUser() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/{id}/lock", testUser.getId())
                .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Tài khoản đã bị khóa"));

        User lockedUser = userRepository.findById(testUser.getId()).get();
        assert lockedUser.isLocked();
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Should not lock already locked user")
    void testLockUser_AlreadyLocked() throws Exception {
        testUser.setLocked(true);
        userRepository.save(testUser);

        mockMvc.perform(patch("/api/v1/admin/users/{id}/lock", testUser.getId())
                .with(csrf()))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Should unlock user successfully")
    void testUnlockUser() throws Exception {
        testUser.setLocked(true);
        userRepository.save(testUser);

        mockMvc.perform(patch("/api/v1/admin/users/{id}/unlock", testUser.getId())
                .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Tài khoản đã được mở khóa"));

        User unlockedUser = userRepository.findById(testUser.getId()).get();
        assert !unlockedUser.isLocked();
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Should not unlock user that is not locked")
    void testUnlockUser_NotLocked() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/{id}/unlock", testUser.getId())
                .with(csrf()))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // ==================== Delete User Tests ====================

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Should delete user successfully")
    void testDeleteUser() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/users/{id}", testUser.getId())
                .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Tài khoản đã được xóa"));

        User deletedUser = userRepository.findById(testUser.getId()).get();
        assert deletedUser.getDeletedAt() != null;
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Should not allow admin to delete own account")
    void testDeleteUser_OwnAccount() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/users/{id}", adminUser.getId())
                .with(csrf()))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Deleting an already deleted account uses the same 404 as every other action (FR-001)")
    void testDeleteUser_AlreadyDeleted() throws Exception {
        testUser.setDeletedAt(java.time.LocalDateTime.now());
        userRepository.save(testUser);

        mockMvc.perform(delete("/api/v1/admin/users/{id}", testUser.getId())
                .with(csrf()))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Không tìm thấy tài khoản (đã bị xóa)"));
    }

    // ==================== Change Role Tests ====================

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Should change user role successfully")
    void testChangeUserRole() throws Exception {
        ChangeRoleRequest request = new ChangeRoleRequest();
        request.setRole(Role.ROLE_INTERVIEWER);

        mockMvc.perform(patch("/api/v1/admin/users/{id}/role", testUser.getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Vai trò đã được cập nhật"));

        User updatedUser = userRepository.findById(testUser.getId()).get();
        assert updatedUser.getRole() == Role.ROLE_INTERVIEWER;
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Should not allow admin to change own role")
    void testChangeUserRole_OwnAccount() throws Exception {
        ChangeRoleRequest request = new ChangeRoleRequest();
        request.setRole(Role.ROLE_USER);

        mockMvc.perform(patch("/api/v1/admin/users/{id}/role", adminUser.getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Should not change role of deleted user")
    void testChangeUserRole_DeletedUser() throws Exception {
        testUser.setDeletedAt(java.time.LocalDateTime.now());
        userRepository.save(testUser);

        ChangeRoleRequest request = new ChangeRoleRequest();
        request.setRole(Role.ROLE_INTERVIEWER);

        mockMvc.perform(patch("/api/v1/admin/users/{id}/role", testUser.getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ==================== Reset Password Tests ====================

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Should reset password successfully")
    void testResetPassword() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/{id}/reset-password", testUser.getId())
                .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Email đặt lại mật khẩu đã được gửi"));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Should not reset password for deleted user")
    void testResetPassword_DeletedUser() throws Exception {
        testUser.setDeletedAt(java.time.LocalDateTime.now());
        userRepository.save(testUser);

        mockMvc.perform(post("/api/v1/admin/users/{id}/reset-password", testUser.getId())
                .with(csrf()))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ==================== Audit Log Tests ====================

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Should get audit logs")
    void testGetAuditLogs() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                .param("page", "0")
                .param("size", "20")
                .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    // ==================== Authorization Tests ====================

    @Test
    @DisplayName("Should deny access without authentication")
    void testUnauthorizedAccess() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Should deny access for non-admin users")
    void testForbiddenAccess() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                .with(csrf()))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    // ==================== Invalid filter / paging parameters (FR-002, FR-003) ====================

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Unknown status filter is rejected at the door, not answered with an empty page")
    void testGetUsers_UnknownStatusFilter() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users").param("status", "FOO").with(csrf()))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("status")));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Unknown role filter is rejected with the list of allowed values")
    void testGetUsers_UnknownRoleFilter() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users").param("role", "ROLE_FOO").with(csrf()))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Out-of-range paging is rejected before touching the database")
    void testGetUsers_InvalidPaging() throws Exception {
        for (String query : new String[]{"page=-1", "size=0", "size=100000"}) {
            int eq = query.indexOf('=');
            mockMvc.perform(get("/api/v1/admin/users")
                            .param(query.substring(0, eq), query.substring(eq + 1))
                            .with(csrf()))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("status=DELETED points the admin at the deleted-accounts endpoint")
    void testGetUsers_DeletedStatusRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users").param("status", "DELETED").with(csrf()))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("users/deleted")));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Malformed JSON body is a 400, not a 500")
    void testChangeRole_MalformedBody() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/{id}/role", testUser.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    // ==================== Consistent "deleted account" contract (FR-001) ====================

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Unlocking a deleted account is refused with the same 404 as every other action")
    void testUnlockUser_DeletedUser() throws Exception {
        testUser.setLocked(true);
        testUser.setDeletedAt(LocalDateTime.now());
        userRepository.save(testUser);

        mockMvc.perform(patch("/api/v1/admin/users/{id}/unlock", testUser.getId()).with(csrf()))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy tài khoản (đã bị xóa)"));

        assertEquals(true, userRepository.findById(testUser.getId()).orElseThrow().isLocked());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Locking a deleted account uses the same 404 message")
    void testLockUser_DeletedUser() throws Exception {
        testUser.setDeletedAt(LocalDateTime.now());
        userRepository.save(testUser);

        mockMvc.perform(patch("/api/v1/admin/users/{id}/lock", testUser.getId()).with(csrf()))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy tài khoản (đã bị xóa)"));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Detail of a deleted account is still readable so the admin can decide to restore")
    void testGetUserDetail_DeletedUserIsReadable() throws Exception {
        testUser.setDeletedAt(LocalDateTime.now());
        userRepository.save(testUser);

        mockMvc.perform(get("/api/v1/admin/users/{id}", testUser.getId()).with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELETED"));
    }

    // ==================== Mail failure => 502 + rollback (FR-005) ====================

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("When the mail channel fails the lock is rolled back and the API answers 502")
    void testLockUser_MailFailureRollsBack() throws Exception {
        int versionBefore = userRepository.findById(testUser.getId()).orElseThrow().getTokenVersion();
        willThrow(new MailDeliveryException("Không gửi được email"))
                .given(emailService).sendAccountLockedEmail(anyString(), anyString());

        mockMvc.perform(patch("/api/v1/admin/users/{id}/lock", testUser.getId()).with(csrf()))
                .andDo(print())
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502));

        User after = userRepository.findById(testUser.getId()).orElseThrow();
        assertFalse(after.isLocked());
        assertNull(after.getLockedAt());
        assertEquals(versionBefore, after.getTokenVersion());
        assertEquals(0, auditLogRepository.search(null, testUser.getId(), null, null, null,
                PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("A reset link is not persisted when its email cannot be sent")
    void testResetPassword_MailFailureKeepsPreviousToken() throws Exception {
        testUser.setResetToken("old-token");
        testUser.setResetTokenExpiry(LocalDateTime.now().plusMinutes(30));
        userRepository.save(testUser);
        willThrow(new MailDeliveryException("Không gửi được email"))
                .given(emailService).sendResetPasswordEmail(anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/v1/admin/users/{id}/reset-password", testUser.getId()).with(csrf()))
                .andDo(print())
                .andExpect(status().isBadGateway());

        assertEquals("old-token", userRepository.findById(testUser.getId()).orElseThrow().getResetToken());
    }

    // ==================== Enable / disable (FR-009) ====================

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Disabling an account revokes its sessions; enabling it back works")
    void testSetUserStatus_DisableThenEnable() throws Exception {
        testUser.setEnabled(true);
        testUser = userRepository.save(testUser);
        int versionBefore = testUser.getTokenVersion();

        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", testUser.getId())
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Tài khoản đã được tắt"));

        User disabled = userRepository.findById(testUser.getId()).orElseThrow();
        assertFalse(disabled.isEnabled());
        assertEquals(versionBefore + 1, disabled.getTokenVersion());

        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", testUser.getId())
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Tài khoản đã được bật"));
        assertTrue(userRepository.findById(testUser.getId()).orElseThrow().isEnabled());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Toggling to the current state, self, or an unverified account is refused")
    void testSetUserStatus_Rejections() throws Exception {
        // already inactive (setUp creates enabled=false)
        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", testUser.getId())
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Tài khoản đã ở trạng thái tắt"));

        // own account
        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", adminUser.getId())
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Không thể thay đổi trạng thái tài khoản của chính mình"));

        // pending email verification must not be bypassed by an admin
        testUser.setVerificationToken("pending-token");
        testUser.setVerificationTokenExpiry(LocalDateTime.now().plusHours(24));
        userRepository.save(testUser);
        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", testUser.getId())
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("chưa xác thực email")));

        // missing body field
        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", testUser.getId())
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ==================== Deleted list + restore (FR-010) ====================

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("A soft-deleted account disappears from the live list, appears in the deleted list, and comes back")
    void testDeleteListAndRestore() throws Exception {
        testUser.setEnabled(true);
        testUser.setLocked(true);
        testUser = userRepository.save(testUser);

        mockMvc.perform(delete("/api/v1/admin/users/{id}", testUser.getId()).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/users").with(csrf()))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(get("/api/v1/admin/users/deleted").with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(testUser.getId()))
                .andExpect(jsonPath("$.data.content[0].status").value("DELETED"));

        mockMvc.perform(post("/api/v1/admin/users/{id}/restore", testUser.getId()).with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Tài khoản đã được khôi phục"));

        User restored = userRepository.findById(testUser.getId()).orElseThrow();
        assertNull(restored.getDeletedAt());
        // delete only marked the row deleted, so the pre-delete state is still there
        assertTrue(restored.isEnabled());
        assertTrue(restored.isLocked());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Restoring an account that is not deleted is refused")
    void testRestoreUser_NotDeleted() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/{id}/restore", testUser.getId()).with(csrf()))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Tài khoản không ở trạng thái đã xóa"));
    }

    // ==================== Audit log (FR-013..FR-016) ====================

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Audit rows carry the real admin and the target's email, and can be filtered")
    void testAuditLogs_AreFilterableAndShowTargetEmail() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/{id}/lock", testUser.getId()).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("targetUserId", testUser.getId().toString())
                        .param("action", "LOCK_USER")
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].adminId").value(adminUser.getId()))
                .andExpect(jsonPath("$.data.content[0].adminEmail").value("admin@example.com"))
                .andExpect(jsonPath("$.data.content[0].targetEmail").value("user@example.com"))
                .andExpect(jsonPath("$.data.content[0].targetFullName").value("Test User"))
                .andExpect(jsonPath("$.data.content[0].succeeded").value(true));

        mockMvc.perform(get("/api/v1/admin/audit-logs").param("action", "NOT_ARealAction").with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("A rejected attempt leaves an audit row with succeeded=false and the reason")
    void testAuditLogs_RejectedAttemptIsRecorded() throws Exception {
        testUser.setLocked(true);
        userRepository.save(testUser);

        mockMvc.perform(patch("/api/v1/admin/users/{id}/lock", testUser.getId()).with(csrf()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("targetUserId", testUser.getId().toString())
                        .with(csrf()))
                .andDo(print())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].succeeded").value(false))
                .andExpect(jsonPath("$.data.content[0].action").value("LOCK_USER"))
                .andExpect(jsonPath("$.data.content[0].reason").value("Tài khoản đã bị khóa"));
    }

    @Test
    @DisplayName("No audit row is faked with admin 0 / system when the principal cannot be resolved")
    void testAuditLogs_UnresolvableAdminIsRefused() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/{id}/lock", testUser.getId())
                        .with(csrf())
                        .with(user("ghost@example.com").roles("ADMIN")))
                .andDo(print())
                .andExpect(status().isForbidden());
        assertEquals(0, auditLogRepository.count());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Date filters reject an inverted range and a malformed value")
    void testAuditLogs_DateFilters() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("from", "2026-10-20").param("to", "2026-10-19").with(csrf()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/admin/audit-logs").param("from", "20/10/2026").with(csrf()))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("from")));

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("from", "2026-10-19").with(csrf()))
                .andExpect(status().isOk());
    }

    // ==================== Search safety (FR-006) ====================

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("LIKE wildcards in the search box are treated as literal characters")
    void testGetUsers_SearchWildcardsAreLiteral() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users").param("search", "%").with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(get("/api/v1/admin/users").param("search", "example").with(csrf()))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("An over-long search term is refused")
    void testGetUsers_SearchTooLong() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .param("search", "x".repeat(101)).with(csrf()))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("100")));
    }

    // ==================== 002 Lô A: stats, PENDING, lastLoginAt (AC-1, FR-003, FR-004) ==========

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("AC-1: every badge equals totalElements of the list with the same filter")
    void testStats_MatchListTotals() throws Exception {
        persistMember("pending@example.com", "Pending One", Role.ROLE_USER, false, false, "tok-pending");
        persistMember("active@example.com", "Active One", Role.ROLE_USER, true, false, null);
        persistMember("lockedstaff@example.com", "Locked Staff", Role.ROLE_STAFF, true, true, null);
        mockMvc.perform(delete("/api/v1/admin/users/{id}", testUser.getId()).with(csrf()))
                .andExpect(status().isOk());

        String[][] filters = {{}, {"search=pending"}, {"role=ROLE_USER"}, {"search=nomatchatall"}};
        for (String[] filter : filters) {
            JsonNode stats = json("/api/v1/admin/users/stats", filter);
            String label = " filter=" + Arrays.toString(filter);
            assertEquals(total("/api/v1/admin/users", filter), stats.path("total").asLong(), "total" + label);
            assertEquals(total("/api/v1/admin/users", plus(filter, "status=ACTIVE")),
                    stats.path("active").asLong(), "active" + label);
            assertEquals(total("/api/v1/admin/users", plus(filter, "status=INACTIVE")),
                    stats.path("disabled").asLong(), "disabled" + label);
            assertEquals(total("/api/v1/admin/users", plus(filter, "status=LOCKED")),
                    stats.path("locked").asLong(), "locked" + label);
            assertEquals(total("/api/v1/admin/users", plus(filter, "status=PENDING")),
                    stats.path("pendingVerification").asLong(), "pending" + label);
            assertEquals(total("/api/v1/admin/users/deleted", filter),
                    stats.path("deleted").asLong(), "deleted" + label);
        }
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("PENDING lists exactly the accounts that never used their verification link")
    void testStatusFilterPending() throws Exception {
        persistMember("pending@example.com", "Pending One", Role.ROLE_USER, false, false, "tok-pending");
        persistMember("verified@example.com", "Verified One", Role.ROLE_USER, true, false, null);

        JsonNode page = json("/api/v1/admin/users", "status=PENDING", "size=100");
        assertEquals(1, page.path("totalElements").asLong());
        JsonNode row = page.path("content").get(0);
        assertEquals("pending@example.com", row.path("email").asText());
        assertTrue(row.path("pendingVerification").asBoolean());

        // Regression for 001: the four old status values still mean what they meant.
        JsonNode active = json("/api/v1/admin/users", "status=ACTIVE", "size=100").path("content").get(0);
        assertEquals("verified@example.com", active.path("email").asText());
        assertFalse(active.path("pendingVerification").asBoolean());
        assertEquals(3, total("/api/v1/admin/users", "status=INACTIVE", "size=100"));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("lastLoginAt is the newest successful login; the 30-day KPI uses the same window")
    void testLastLoginAtAndActiveWindow() throws Exception {
        insertLogin("user@example.com", 40, false);
        insertLogin("user@example.com", 40, true);
        insertLogin("user@example.com", 2, true);
        insertLogin("admin@example.com", 45, true);
        persistMember("never@example.com", "Never One", Role.ROLE_USER, true, false, null);

        JsonNode rows = json("/api/v1/admin/users", "size=100").path("content");
        LocalDateTime recent = LocalDateTime.parse(lastLoginOf(rows, "user@example.com"));
        assertTrue(recent.isAfter(LocalDateTime.now().minusDays(3)),
                "phải là lần đăng nhập gần nhất, không phải lần cũ");
        LocalDateTime oldOnly = LocalDateTime.parse(lastLoginOf(rows, "admin@example.com"));
        assertTrue(oldOnly.isBefore(LocalDateTime.now().minusDays(30)));
        assertTrue(rowsOf(rows, "never@example.com").path("lastLoginAt").isNull(),
                "chưa từng đăng nhập thì trả null để FE hiển thị Never");

        assertEquals(1, json("/api/v1/admin/users/stats").path("activeLast30Days").asLong());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("stats rejects a bad role and an over-long search instead of answering 500")
    void testStats_InvalidParameters() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/stats").param("role", "ROLE_FOO").with(csrf()))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(get("/api/v1/admin/users/stats").param("search", "x".repeat(101)).with(csrf()))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("100")));
    }

    @Test
    @DisplayName("AC-6: every new directory endpoint is closed to Staff and to anonymous callers")
    void testNewEndpoints_Authorization() throws Exception {
        String invite = inviteBody("anyone@example.com", "ROLE_USER", null);
        List<MockHttpServletRequestBuilder> calls = List.of(
                get("/api/v1/admin/users/stats"),
                get("/api/v1/admin/users/export"),
                post("/api/v1/admin/users").contentType(MediaType.APPLICATION_JSON).content(invite),
                post("/api/v1/admin/users/1/invitation/resend"),
                delete("/api/v1/admin/users/1/invitation"),
                patch("/api/v1/admin/users/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"X\"}"));

        for (MockHttpServletRequestBuilder call : List.of(calls.get(0), calls.get(1))) {
            mockMvc.perform(call).andExpect(status().isUnauthorized());
        }
        for (MockHttpServletRequestBuilder call : calls) {
            mockMvc.perform(withAdmin(call, false)).andExpect(status().isForbidden());
        }
    }

    // ==================== 002 Lô B: mời tài khoản (AC-2) ====================

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Add User creates an inactive member, mails the invitation and audits the real admin")
    void testInviteUser_HappyPath() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(inviteBody("invited@example.com", "ROLE_STAFF",
                                "\"password\":\"Secret@123\"")))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã gửi email mời tài khoản"))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.pendingVerification").value(true))
                .andExpect(jsonPath("$.data.role").value("ROLE_STAFF"))
                .andExpect(jsonPath("$.data.password").doesNotExist());

        User invited = userRepository.findByEmail("invited@example.com").orElseThrow();
        assertFalse(invited.isEnabled());
        assertNotNull(invited.getVerificationToken());
        assertTrue(invited.getVerificationTokenExpiry().isAfter(LocalDateTime.now().plusHours(23)));
        // Clarify C-4: mật khẩu gửi kèm bị bỏ qua, BE không bao giờ biết mật khẩu của user được mời.
        assertFalse(passwordEncoder.matches("Secret@123", invited.getPassword()));

        verify(emailService).sendVerificationEmail(eq("invited@example.com"), eq("Moi Nhap"),
                eq(invited.getVerificationToken()));

        var audit = auditLogRepository.search(null, invited.getId(), "INVITE_USER", null, null,
                PageRequest.of(0, 10));
        assertEquals(1, audit.getTotalElements());
        assertEquals(adminUser.getId(), audit.getContent().get(0).getAdminId());
        assertTrue(audit.getContent().get(0).isSucceeded());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Inviting an email that already exists is refused before anything is written")
    void testInviteUser_DuplicateEmail() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(inviteBody("user@example.com", "ROLE_USER", null)))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email đã tồn tại trong hệ thống"));

        verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("A soft-deleted account still owns its email, so the 409 points at restore")
    void testInviteUser_SoftDeletedEmailConflict() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/users/{id}", testUser.getId()).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/users").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(inviteBody("user@example.com", "ROLE_USER", null)))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("/restore")))
                .andExpect(jsonPath("$.message").value(containsString(String.valueOf(testUser.getId()))));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("AC-2: a failed invitation mail means 502, no user and no successful audit row")
    void testInviteUser_MailFailureRollsBackEverything() throws Exception {
        willThrow(new MailDeliveryException("Không gửi được email"))
                .given(emailService).sendVerificationEmail(anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/v1/admin/users").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(inviteBody("invited@example.com", "ROLE_USER", null)))
                .andDo(print())
                .andExpect(status().isBadGateway());

        assertTrue(userRepository.findByEmail("invited@example.com").isEmpty());
        assertEquals(0, auditLogRepository.search(null, null, "INVITE_USER", null, null,
                PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Invite rejects a missing name, a malformed email and a missing role with 400")
    void testInviteUser_Validation() throws Exception {
        mockMvc.perform(withAdmin(post("/api/v1/admin/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nope@\",\"role\":\"ROLE_USER\"}"), true))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withAdmin(post("/api/v1/admin/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"A B\",\"role\":\"ROLE_UNKNOWN\"}"), true))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withAdmin(post("/api/v1/admin/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"A B\",\"email\":\"a@b.co\"}"), true))
                .andExpect(status().isBadRequest());
    }

    // ==================== 002 Lô B: resend / revoke ====================

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Resend rotates the invitation token and re-sends the mail")
    void testResendInvitation() throws Exception {
        User pending = persistMember("pending@example.com", "Pending One", Role.ROLE_USER,
                false, false, "old-token");

        mockMvc.perform(post("/api/v1/admin/users/{id}/invitation/resend", pending.getId()).with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingVerification").value(true));

        String newToken = userRepository.findById(pending.getId()).orElseThrow().getVerificationToken();
        assertNotEquals("old-token", newToken);
        verify(emailService).sendResendVerificationEmail(eq("pending@example.com"), anyString(), eq(newToken));
        assertEquals(1, auditLogRepository.search(null, pending.getId(), "RESEND_INVITATION", null, null,
                PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Resend on an account that already verified its email is refused")
    void testResendInvitation_AlreadyVerified() throws Exception {
        User verified = persistMember("verified@example.com", "Verified One", Role.ROLE_USER,
                true, false, null);

        mockMvc.perform(post("/api/v1/admin/users/{id}/invitation/resend", verified.getId()).with(csrf()))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Tài khoản đã xác thực email"));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("A failed resend leaves the token the member already holds untouched")
    void testResendInvitation_MailFailureKeepsOldToken() throws Exception {
        User pending = persistMember("pending@example.com", "Pending One", Role.ROLE_USER,
                false, false, "old-token");
        willThrow(new MailDeliveryException("Không gửi được email"))
                .given(emailService).sendResendVerificationEmail(anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/v1/admin/users/{id}/invitation/resend", pending.getId()).with(csrf()))
                .andDo(print())
                .andExpect(status().isBadGateway());

        assertEquals("old-token",
                userRepository.findById(pending.getId()).orElseThrow().getVerificationToken());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("AC-3: revoke kills the link, keeps the account, and a later resend works again")
    void testRevokeInvitation() throws Exception {
        User pending = persistMember("pending@example.com", "Pending One", Role.ROLE_USER,
                false, false, "old-token");
        int versionBefore = pending.getTokenVersion();

        mockMvc.perform(delete("/api/v1/admin/users/{id}/invitation", pending.getId()).with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã thu hồi link mời"))
                .andExpect(jsonPath("$.data.pendingVerification").value(false))
                .andExpect(jsonPath("$.data.enabled").value(false));

        User after = userRepository.findById(pending.getId()).orElseThrow();
        assertNull(after.getVerificationToken());
        assertNull(after.getVerificationTokenExpiry());
        assertFalse(after.isEnabled());
        assertEquals(versionBefore, after.getTokenVersion());
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/v1/admin/users/{id}/invitation/resend", pending.getId()).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/admin/users/{id}/invitation", pending.getId()).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/admin/users/{id}/invitation", pending.getId()).with(csrf()))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("không có lời mời")));
    }

    // ==================== 002 Lô B: sửa hồ sơ (FR-009) ====================

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Editing name and phone keeps the verified state")
    void testUpdateProfile_WithoutEmailChange() throws Exception {
        User member = persistMember("member@example.com", "Old Name", Role.ROLE_USER, true, false, null);

        mockMvc.perform(patch("/api/v1/admin/users/{id}", member.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"New Name\",\"phone\":\"0900123456\"}"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hồ sơ đã được cập nhật"))
                .andExpect(jsonPath("$.data.emailChanged").value(false))
                .andExpect(jsonPath("$.data.user.fullName").value("New Name"))
                .andExpect(jsonPath("$.data.user.enabled").value(true));

        User after = userRepository.findById(member.getId()).orElseThrow();
        assertEquals("New Name", after.getFullName());
        assertEquals("0900123456", after.getPhone());
        assertEquals(1, auditLogRepository.search(null, member.getId(), "UPDATE_PROFILE", null, null,
                PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Changing the email sends the account back to pending and kills its old sessions")
    void testUpdateProfile_EmailChange() throws Exception {
        User member = persistMember("member@example.com", "Member", Role.ROLE_USER, true, false, null);
        int versionBefore = member.getTokenVersion();

        mockMvc.perform(patch("/api/v1/admin/users/{id}", member.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"fresh@example.com\"}"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emailChanged").value(true))
                .andExpect(jsonPath("$.data.user.pendingVerification").value(true))
                .andExpect(jsonPath("$.data.user.enabled").value(false));

        User after = userRepository.findByEmail("fresh@example.com").orElseThrow();
        assertFalse(after.isEnabled());
        assertNotNull(after.getVerificationToken());
        assertEquals(versionBefore + 1, after.getTokenVersion());
        verify(emailService).sendVerificationEmail(eq("fresh@example.com"), anyString(),
                eq(after.getVerificationToken()));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("An email edit that collides with another account is refused with 409")
    void testUpdateProfile_EmailTaken() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/{id}", testUser.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@example.com\"}"))
                .andDo(print())
                .andExpect(status().isConflict());

        assertEquals("user@example.com",
                userRepository.findById(testUser.getId()).orElseThrow().getEmail());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("A failed re-invitation rolls the email change back to the old address")
    void testUpdateProfile_EmailChangeMailFailure() throws Exception {
        User member = persistMember("member@example.com", "Member", Role.ROLE_USER, true, false, null);
        willThrow(new MailDeliveryException("Không gửi được email"))
                .given(emailService).sendVerificationEmail(anyString(), anyString(), anyString());

        mockMvc.perform(patch("/api/v1/admin/users/{id}", member.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"fresh@example.com\"}"))
                .andDo(print())
                .andExpect(status().isBadGateway());

        User after = userRepository.findById(member.getId()).orElseThrow();
        assertEquals("member@example.com", after.getEmail());
        assertTrue(after.isEnabled());
        assertNull(after.getVerificationToken());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Empty edits, a blank name and changing the admin's own email are refused")
    void testUpdateProfile_Rejections() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/{id}", testUser.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Không có thông tin")));

        mockMvc.perform(patch("/api/v1/admin/users/{id}", testUser.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Họ tên")));

        mockMvc.perform(patch("/api/v1/admin/users/{id}", adminUser.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"other@example.com\"}"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("chính tài khoản quản trị")));
    }

    // ==================== 002 Lô B: CSV export (AC-4, FR-008) ====================

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("AC-4: the CSV has exactly the rows the same filter lists, and no secrets")
    void testExportCsv_MatchesListAndHasNoSecrets() throws Exception {
        persistMember("pending@example.com", "Pending One", Role.ROLE_USER, false, false, "tok-pending");
        persistMember("boss@example.com", "Boss Person", Role.ROLE_ADMIN, true, false, null);

        for (String[] filter : new String[][]{{}, {"role=ROLE_USER"}}) {
            long listed = total("/api/v1/admin/users", plus(filter, "size=100"));
            MvcResult result = mockMvc.perform(withAdmin(getWithParams("/api/v1/admin/users/export", filter), true))
                    .andExpect(status().isOk())
                    .andReturn();

            String csv = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
            String[] lines = csv.split("\n");
            assertEquals(listed + 1, lines.length, "số dòng CSV phải khớp danh sách: " + Arrays.toString(filter));
            assertEquals("id,email,fullName,role,status,pendingVerification,lastLoginAt,createdAt,deletedAt",
                    lines[0]);
            assertFalse(csv.toLowerCase().contains("password"));
            assertTrue(result.getResponse().getContentType().startsWith("text/csv"));
            assertEquals("attachment; filename=\"users-export.csv\"",
                    result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION));
        }

        var audit = auditLogRepository.search(null, null, "EXPORT_USERS", null, null, PageRequest.of(0, 10));
        assertEquals(2, audit.getTotalElements());
        assertNull(audit.getContent().get(0).getTargetUserId());
        assertEquals(adminUser.getId(), audit.getContent().get(0).getAdminId());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("A name containing commas, quotes or newlines cannot break the CSV columns")
    void testExportCsv_EscapesHostileCells() throws Exception {
        persistMember("tricky@example.com", "Nguyen, \"Ti\" Van\nLine2", Role.ROLE_USER, true, false, null);
        persistMember("formula@example.com", "=SUM(A1:A9)", Role.ROLE_USER, true, false, null);

        MvcResult result = mockMvc.perform(get("/api/v1/admin/users/export").with(csrf()))
                .andExpect(status().isOk()).andReturn();
        String csv = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertTrue(csv.contains("\"Nguyen, \"\"Ti\"\" Van\nLine2\""), "ô có dấu phẩy/nháy/xuống dòng phải được bọc");
        assertTrue(csv.contains("'=SUM(A1:A9)"), "ô bắt đầu bằng = phải bị vô hiệu trước khi vào bảng tính");
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("status=DELETED is refused for the export, same as for the list")
    void testExportCsv_DeletedStatusRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/export").param("status", "DELETED").with(csrf()))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("users/deleted")));
    }

    // ==================== 002 helpers ====================

    private User persistMember(String email, String fullName, Role role, boolean enabled,
                               boolean locked, String verificationToken) {
        return userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode("password123"))
                .fullName(fullName)
                .role(role)
                .enabled(enabled)
                .locked(locked)
                .verificationToken(verificationToken)
                .build());
    }

    private void insertLogin(String email, int daysAgo, boolean success) {
        jdbcTemplate.update("insert into login_attempts (email, ip_address, success, created_at) "
                        + "values (?, ?, ?, ?)",
                email, "127.0.0.1", success, LocalDateTime.now().minusDays(daysAgo));
    }

    private static String inviteBody(String email, String role, String extra) {
        return "{\"fullName\":\"Moi Nhap\",\"email\":\"" + email + "\",\"role\":\"" + role + "\""
                + (extra == null ? "" : ",") + (extra == null ? "" : extra) + "}";
    }

    private JsonNode json(String path, String... params) throws Exception {
        MvcResult result = mockMvc.perform(withAdmin(getWithParams(path, params), true))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
    }

    private long total(String path, String... params) throws Exception {
        return json(path, params).path("totalElements").asLong();
    }

    private JsonNode rowsOf(JsonNode content, String email) {
        List<JsonNode> matched = new ArrayList<>();
        content.forEach(row -> {
            if (email.equals(row.path("email").asText())) {
                matched.add(row);
            }
        });
        assertEquals(1, matched.size(), "phải có đúng một dòng cho " + email);
        return matched.get(0);
    }

    private String lastLoginOf(JsonNode content, String email) {
        return rowsOf(content, email).path("lastLoginAt").asText();
    }

    private static String[] plus(String[] params, String extra) {
        String[] out = Arrays.copyOf(params, params.length + 1);
        out[params.length] = extra;
        return out;
    }

    private MockHttpServletRequestBuilder getWithParams(String path, String... params) {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(path);
        for (String param : params) {
            int separator = param.indexOf('=');
            builder.param(param.substring(0, separator), param.substring(separator + 1));
        }
        return builder;
    }

    /** roleAdmin=false stands for a Staff token, which the URL rule must reject. */
    private MockHttpServletRequestBuilder withAdmin(MockHttpServletRequestBuilder call, boolean roleAdmin) {
        return roleAdmin
                ? call.with(user("admin@example.com").roles("ADMIN")).with(csrf())
                : call.with(user("staff@example.com").roles("STAFF")).with(csrf());
    }
}
