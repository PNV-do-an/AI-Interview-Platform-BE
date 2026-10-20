package com.aiinterview.platform.controller;

import com.aiinterview.platform.model.entity.InterviewSession;
import com.aiinterview.platform.model.entity.JobDescription;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.InterviewMode;
import com.aiinterview.platform.model.enums.InterviewSessionStatus;
import com.aiinterview.platform.model.enums.JobStatus;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.AdminAuditLogRepository;
import com.aiinterview.platform.model.repository.InterviewSessionRepository;
import com.aiinterview.platform.model.repository.JobDescriptionRepository;
import com.aiinterview.platform.model.repository.JobModerationRecordRepository;
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
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Admin Dashboard Integration Tests (spec 005)")
class AdminDashboardIntegrationTest {

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
    private InterviewSessionRepository sessionRepository;

    @Autowired
    private JobModerationRecordRepository moderationRepository;

    @Autowired
    private AdminAuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User employer;

    @BeforeEach
    void setUp() {
        moderationRepository.deleteAll();
        sessionRepository.deleteAll();
        jobRepository.deleteAll();
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
        jdbcTemplate.update("delete from login_attempts");
        employer = null;
    }

    /** Every dashboard number must equal a hand-run SQL count on the same data. */
    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("AC-1: six independent KPIs match direct SQL counts")
    void testDashboard_NumbersMatchSql() throws Exception {
        persistUser("admin@example.com", "Admin", Role.ROLE_ADMIN, true, false, null);
        User candidate1 = persistUser("c1@example.com", "C1", Role.ROLE_USER, true, false, null);
        persistUser("c2@example.com", "C2", Role.ROLE_USER, true, true, null);
        persistUser("c3@example.com", "C3", Role.ROLE_USER, false, false, "token-pending");
        employer = persistUser("emp@example.com", "Employer", Role.ROLE_INTERVIEWER, true, false, null);

        persistJob("Job Pending", JobStatus.PENDING_APPROVAL);
        persistJob("Job Open", JobStatus.OPEN);
        persistJob("Job Draft A", JobStatus.DRAFT);
        persistJob("Job Draft B", JobStatus.DRAFT);

        persistSession(candidate1, InterviewSessionStatus.COMPLETED, 8, 0);
        persistSession(candidate1, InterviewSessionStatus.IN_PROGRESS, null, 0);

        insertLogin("c1@example.com", 0, true);
        insertLogin("c1@example.com", 0, true);
        insertLogin("c1@example.com", 0, false);

        JsonNode data = dashboard(30);

        // 6 số lệch miền nhau, mỗi số đối chiếu một câu SQL.
        assertEquals(sqlCount("select count(*) from users where deleted_at is null"),
                data.path("users").path("total").asLong(), "users.total");
        assertEquals(sqlCount("select count(*) from users where deleted_at is null "
                        + "and enabled = 1 and locked = 0"),
                data.path("users").path("active").asLong(), "users.active");
        assertEquals(sqlCount("select count(*) from users where deleted_at is null and role = 'ROLE_ADMIN'"),
                data.path("users").path("byRole").path("ROLE_ADMIN").asLong(), "users.byRole.ROLE_ADMIN");
        assertEquals(sqlCount("select count(*) from job_descriptions where deleted_at is null "
                        + "and status = 'PENDING_APPROVAL'"),
                data.path("jobs").path("pendingModeration").asLong(), "jobs.pendingModeration");
        assertEquals(sqlCount("select count(*) from interview_sessions where completed_at is not null"),
                data.path("interviews").path("completedInPeriod").asLong(), "interviews.completedInPeriod");
        assertEquals(sqlCount("select count(*) from login_attempts where success = 0"),
                data.path("logins").path("failedInPeriod").asLong(), "logins.failedInPeriod");
        assertEquals(sqlCount("select count(*) from users where deleted_at is null"),
                data.path("users").path("newInPeriod").asLong(), "users.newInPeriod");

        // Những con số còn lại, tính tay từ bộ dữ liệu vừa seed.
        assertEquals(1, data.path("users").path("locked").asInt());
        assertEquals(1, data.path("users").path("pendingVerification").asInt());
        assertEquals(0, data.path("users").path("deleted").asInt());
        // Đếm theo email thành công đã unique ⇒ 3 lần đăng nhập nhưng chỉ 1 email.
        assertEquals(1, data.path("users").path("loginsInPeriod").asInt());
        assertEquals(4, data.path("jobs").path("total").asInt());
        assertEquals(2, data.path("jobs").path("byStatus").path("DRAFT").asInt());
        assertEquals(0, data.path("jobs").path("byStatus").path("CLOSED").asInt());
        assertEquals(4, data.path("jobs").path("newInPeriod").asInt());
        assertEquals(2, data.path("interviews").path("total").asInt());
        assertEquals(2, data.path("interviews").path("inPeriod").asInt());
        assertEquals(8.0, data.path("interviews").path("avgOverallScore").asDouble());
        assertEquals(2, data.path("logins").path("successInPeriod").asInt());
        assertEquals(1, data.path("moderation").path("pendingApproval").asInt());
        assertEquals(30, data.path("rangeDays").asInt());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("AC-2: the days window moves period counters only; users.total stays put")
    void testDashboard_DaysWindow() throws Exception {
        persistUser("admin@example.com", "Admin", Role.ROLE_ADMIN, true, false, null);
        User old = persistUser("old@example.com", "Old", Role.ROLE_USER, true, false, null);
        persistUser("new1@example.com", "N1", Role.ROLE_USER, true, false, null);
        User candidate = persistUser("c@example.com", "C", Role.ROLE_USER, true, false, null);
        ageUser(old, 40);

        persistSession(candidate, InterviewSessionStatus.IN_PROGRESS, null, 3);
        persistSession(candidate, InterviewSessionStatus.IN_PROGRESS, null, 20);
        insertLogin("c@example.com", 3, true);
        insertLogin("c@example.com", 20, true);
        insertLogin("c@example.com", 45, false);

        JsonNode week = dashboard(7);
        JsonNode month = dashboard(30);
        JsonNode quarter = dashboard(90);

        assertEquals(4, week.path("users").path("total").asInt());
        assertEquals(4, month.path("users").path("total").asInt(), "users.total không phụ thuộc days");
        assertEquals(3, week.path("users").path("newInPeriod").asInt());
        assertEquals(3, month.path("users").path("newInPeriod").asInt());
        assertEquals(1, week.path("interviews").path("inPeriod").asInt());
        assertEquals(2, month.path("interviews").path("inPeriod").asInt());
        assertEquals(1, week.path("logins").path("successInPeriod").asInt());
        assertEquals(2, month.path("logins").path("successInPeriod").asInt());
        assertEquals(0, month.path("logins").path("failedInPeriod").asInt());
        assertEquals(1, quarter.path("logins").path("failedInPeriod").asInt());
    }

    @Test
    @DisplayName("AC-3: days=5 ⇒ 400 kèm allowedValues; anonymous ⇒ 401; STAFF ⇒ 403")
    void testDashboard_BadRangeAndGuards() throws Exception {
        mockMvc.perform(asAdmin(get("/api/v1/admin/dashboard").param("days", "5")))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("7, 30, 90")));

        mockMvc.perform(get("/api/v1/admin/dashboard").with(csrf()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/dashboard")
                        .with(user("staff@example.com").roles("STAFF")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("AC-4: an empty database answers with zeroes and a null average, not a 500")
    void testDashboard_EmptyDatabase() throws Exception {
        JsonNode data = dashboard(30);

        assertEquals(0, data.path("users").path("total").asInt());
        assertEquals(0, data.path("users").path("byRole").path("ROLE_STAFF").asInt());
        assertEquals(0, data.path("jobs").path("total").asInt());
        assertEquals(0, data.path("jobs").path("byStatus").path("OPEN").asInt());
        assertEquals(0, data.path("interviews").path("total").asInt());
        assertTrue(data.path("interviews").path("avgOverallScore").isNull(), "avgOverallScore phải null");
        assertEquals(0, data.path("logins").path("successInPeriod").asInt());
        assertEquals(0, data.path("moderation").path("pendingApproval").asInt());
    }

    // ==================== helpers ====================

    private JsonNode dashboard(int days) throws Exception {
        MvcResult result = mockMvc.perform(asAdmin(get("/api/v1/admin/dashboard").param("days", String.valueOf(days))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
    }

    private long sqlCount(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private User persistUser(String email, String fullName, Role role, boolean enabled,
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

    private JobDescription persistJob(String title, JobStatus status) {
        return jobRepository.save(JobDescription.builder()
                .employer(employer)
                .title(title)
                .companyName("ACME")
                .status(status)
                .build());
    }

    private void persistSession(User candidate, InterviewSessionStatus status, Integer score, int daysAgo) {
        InterviewSession session = sessionRepository.save(InterviewSession.builder()
                .candidate(candidate)
                .mode(InterviewMode.KNOWLEDGE)
                .topic("Java")
                .status(status)
                .overallScore(score)
                .completedAt(status == InterviewSessionStatus.COMPLETED ? LocalDateTime.now() : null)
                .build());
        if (daysAgo > 0) {
            jdbcTemplate.update("update interview_sessions set created_at = ? where id = ?",
                    LocalDateTime.now().minusDays(daysAgo), session.getId());
        }
    }

    private void ageUser(User user, int days) {
        jdbcTemplate.update("update users set created_at = ? where id = ?",
                LocalDateTime.now().minusDays(days), user.getId());
    }

    private void insertLogin(String email, int daysAgo, boolean success) {
        jdbcTemplate.update("insert into login_attempts (email, ip_address, success, created_at) values (?, ?, ?, ?)",
                email, "127.0.0.1", success, LocalDateTime.now().minusDays(daysAgo));
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder call) {
        return call.with(user("admin@example.com").roles("ADMIN")).with(csrf());
    }
}
