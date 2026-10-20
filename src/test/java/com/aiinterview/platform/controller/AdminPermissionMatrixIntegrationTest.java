package com.aiinterview.platform.controller;

import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.AdminAuditLogRepository;
import com.aiinterview.platform.model.repository.JobDescriptionRepository;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Admin Permission Matrix Integration Tests (spec 006)")
class AdminPermissionMatrixIntegrationTest {

    private static final List<String> ALL_ROLES =
            List.of("ROLE_USER", "ROLE_INTERVIEWER", "ROLE_STAFF", "ROLE_ADMIN");
    private static final List<String> ALL_ACTIONS = List.of("VIEW", "CREATE", "UPDATE", "DELETE", "APPROVE");

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
    private JobDescriptionRepository jobRepository;

    @Autowired
    private AdminAuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
        jdbcTemplate.update("delete from login_attempts");

        persist("admin@example.com", "Admin", Role.ROLE_ADMIN);
        persist("c1@example.com", "Candidate One", Role.ROLE_USER);
        persist("emp@example.com", "Employer", Role.ROLE_INTERVIEWER);
        persist("staff@example.com", "Staff", Role.ROLE_STAFF);
        // Một user đã xóa mềm: userCount chỉ tính tài khoản sống.
        User deleted = persist("gone@example.com", "Gone", Role.ROLE_USER);
        deleted.setDeletedAt(java.time.LocalDateTime.now());
        userRepository.save(deleted);
    }

    // ==================== AC-1: userCount khớp GROUP BY role ============================

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("AC-1: roles[].userCount matches SELECT role, COUNT(*) FROM users GROUP BY role")
    void testMatrix_RoleCountsMatchSql() throws Exception {
        JsonNode matrix = getMatrix();

        for (String role : ALL_ROLES) {
            long fromSql = jdbcTemplate.queryForObject(
                    "select count(*) from users where role = ? and deleted_at is null",
                    Long.class, role);
            assertEquals(fromSql, roleNode(matrix, role).path("userCount").asLong(),
                    "userCount của " + role);
        }
        // ROLE_USER sống duy nhất là c1@ — gone@ đã xóa mềm nên không được đếm.
        assertEquals(1, roleNode(matrix, "ROLE_USER").path("userCount").asInt());
        assertEquals("Ứng viên", roleNode(matrix, "ROLE_USER").path("label").asText());
        assertTrue(roleNode(matrix, "ROLE_ADMIN").path("builtin").asBoolean());
        assertEquals(4, matrix.path("roles").size());
    }

    // ==================== AC-4: đủ 4 role × 9 module × 5 action, không null ============

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("AC-4: the matrix is a complete 9 × 5 × 4 grid with no null cell")
    void testMatrix_IsComplete() throws Exception {
        JsonNode matrix = getMatrix();

        assertEquals(9, matrix.path("modules").size());
        int cells = 0;
        List<String> gaps = new ArrayList<>();
        for (JsonNode module : matrix.path("modules")) {
            assertEquals(5, module.path("actions").size(), "module " + module.path("key"));
            assertFalse(module.path("label").asText().isBlank());
            for (JsonNode action : module.path("actions")) {
                assertTrue(ALL_ACTIONS.contains(action.path("action").asText()),
                        "action lạ: " + action.path("action"));
                assertFalse(action.path("label").asText().isBlank());
                assertEquals(4, action.path("granted").size(), "granted thiếu role cho ô "
                        + module.path("key") + "/" + action.path("action"));
                for (String role : ALL_ROLES) {
                    JsonNode cell = action.path("granted").path(role);
                    if (!cell.isBoolean()) {
                        gaps.add(module.path("key") + "/" + action.path("action") + "/" + role);
                    }
                    cells++;
                }
            }
        }
        assertEquals(List.of(), gaps, "ô không phải boolean: " + gaps);
        assertEquals(180, cells);

        // admin-console là module khóa, chỉ ADMIN.
        JsonNode console = moduleNode(matrix, "admin-console");
        assertTrue(console.path("adminOnly").asBoolean());
        console.path("actions").forEach(action -> {
            assertTrue(action.path("granted").path("ROLE_ADMIN").asBoolean());
            assertFalse(action.path("granted").path("ROLE_STAFF").asBoolean());
            assertFalse(action.path("granted").path("ROLE_INTERVIEWER").asBoolean());
            assertFalse(action.path("granted").path("ROLE_USER").asBoolean());
        });

        // Duyệt JD: STAFF + ADMIN, không phải NTD/ứng viên.
        JsonNode approve = actionNode(matrix, "jobs", "APPROVE").path("granted");
        assertTrue(approve.path("ROLE_STAFF").asBoolean());
        assertTrue(approve.path("ROLE_ADMIN").asBoolean());
        assertFalse(approve.path("ROLE_INTERVIEWER").asBoolean());
        assertFalse(approve.path("ROLE_USER").asBoolean());

        // Hồ sơ ứng viên là miền của ROLE_USER.
        assertTrue(actionNode(matrix, "candidate-profile", "VIEW").path("granted")
                .path("ROLE_USER").asBoolean());
        assertFalse(actionNode(matrix, "candidate-profile", "VIEW").path("granted")
                .path("ROLE_ADMIN").asBoolean());
    }

    // ==================== AC-2: đối chứng ≥ 6 ô bằng HTTP thật ==========================

    @Test
    @DisplayName("AC-2: six real HTTP probes agree with the matrix cells they describe")
    void testMatrix_ProbesAgreeWithReality() throws Exception {
        JsonNode matrix = getMatrix();

        // 1) admin-console VIEW × STAFF ⇒ DENIED, thực tế 403.
        assertFalse(actionNode(matrix, "admin-console", "VIEW").path("granted")
                .path("ROLE_STAFF").asBoolean());
        mockMvc.perform(get("/api/v1/admin/users")
                        .with(user("staff@example.com").roles("STAFF")).with(csrf()))
                .andExpect(status().isForbidden());

        // 2) admin-console VIEW × ADMIN ⇒ GRANTED, thực tế 200.
        assertTrue(actionNode(matrix, "admin-console", "VIEW").path("granted")
                .path("ROLE_ADMIN").asBoolean());
        mockMvc.perform(get("/api/v1/admin/users")
                        .with(user("admin@example.com").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk());

        // 3) jobs CREATE × INTERVIEWER ⇒ GRANTED, thực tế tạo được bản nháp.
        assertTrue(actionNode(matrix, "jobs", "CREATE").path("granted")
                .path("ROLE_INTERVIEWER").asBoolean());
        mockMvc.perform(post("/api/v1/jobs")
                        .with(user("emp@example.com").roles("INTERVIEWER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Probe Job\",\"companyName\":\"ACME\"}"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        // 4) jobs CREATE × USER ⇒ DENIED, thực tế 403.
        assertFalse(actionNode(matrix, "jobs", "CREATE").path("granted").path("ROLE_USER").asBoolean());
        mockMvc.perform(post("/api/v1/jobs")
                        .with(user("c1@example.com").roles("USER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Denied Job\",\"companyName\":\"ACME\"}"))
                .andExpect(status().isForbidden());

        // 5) candidate-profile VIEW × USER ⇒ GRANTED, thực tế 200.
        assertTrue(actionNode(matrix, "candidate-profile", "VIEW").path("granted")
                .path("ROLE_USER").asBoolean());
        mockMvc.perform(get("/api/v1/candidate-profile")
                        .with(user("c1@example.com").roles("USER")).with(csrf()))
                .andExpect(status().isOk());

        // 6) admin-console VIEW × INTERVIEWER ⇒ DENIED, thực tế 403 trên chính endpoint matrix.
        mockMvc.perform(get("/api/v1/admin/permissions/matrix")
                        .with(user("emp@example.com").roles("INTERVIEWER")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ==================== AC-3: chốt cửa ================================================

    @Test
    @DisplayName("AC-3: anonymous ⇒ 401, STAFF ⇒ 403 on the matrix endpoint")
    void testMatrix_Guards() throws Exception {
        mockMvc.perform(get("/api/v1/admin/permissions/matrix").with(csrf()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/permissions/matrix")
                        .with(user("staff@example.com").roles("STAFF")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    /** Ma trận chỉ mô tả code: đọc xong không được để lại dòng audit nào. */
    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Matrix read is side-effect free: no audit rows, no writes")
    void testMatrix_ReadOnly() throws Exception {
        getMatrix();
        getMatrix();
        assertEquals(0, auditLogRepository.count());
    }

    // ==================== helpers ====================

    private User persist(String email, String fullName, Role role) {
        return userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode("password123"))
                .fullName(fullName)
                .role(role)
                .enabled(true)
                .locked(false)
                .build());
    }

    private JsonNode getMatrix() throws Exception {
        MvcResult result = mockMvc.perform(asAdmin(get("/api/v1/admin/permissions/matrix")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
    }

    private static JsonNode roleNode(JsonNode matrix, String role) {
        for (JsonNode node : matrix.path("roles")) {
            if (role.equals(node.path("role").asText())) {
                return node;
            }
        }
        fail("thiếu role " + role + " trong ma trận");
        return null;
    }

    private static JsonNode moduleNode(JsonNode matrix, String key) {
        for (JsonNode node : matrix.path("modules")) {
            if (key.equals(node.path("key").asText())) {
                return node;
            }
        }
        fail("thiếu module " + key + " trong ma trận");
        return null;
    }

    private static JsonNode actionNode(JsonNode matrix, String moduleKey, String action) {
        for (JsonNode node : moduleNode(matrix, moduleKey).path("actions")) {
            if (action.equals(node.path("action").asText())) {
                return node;
            }
        }
        fail("thiếu action " + action + " của module " + moduleKey);
        return null;
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder call) {
        return call.with(user("admin@example.com").roles("ADMIN")).with(csrf());
    }
}
