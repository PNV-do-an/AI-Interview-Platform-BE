package com.aiinterview.platform.controller;

import com.aiinterview.platform.model.entity.JobDescription;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.JobStatus;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.AdminAuditLogRepository;
import com.aiinterview.platform.model.repository.JobDescriptionRepository;
import com.aiinterview.platform.model.repository.JobModerationRecordRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.service.EmailService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("JD Moderation Console Integration Tests (spec 004)")
class JobModerationConsoleIntegrationTest {

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
    private JobModerationRecordRepository moderationRepository;

    @Autowired
    private AdminAuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User employer;
    private User staff;

    @BeforeEach
    void setUp() {
        moderationRepository.deleteAll();
        jobRepository.deleteAll();
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
        jdbcTemplate.update("delete from login_attempts");

        userRepository.save(buildUser("admin@example.com", "Admin User", Role.ROLE_ADMIN));
        employer = userRepository.save(buildUser("employer@example.com", "Ngoai Thi Employer",
                Role.ROLE_INTERVIEWER));
        staff = userRepository.save(buildUser("staff@example.com", "Staff Member", Role.ROLE_STAFF));
    }

    // ==================== AC-1: queue khớp trạng thái trong DB ====================

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("AC-1: default queue lists only PENDING_APPROVAL jobs, newest first; status re-filters")
    void testQueue_MatchesDbStatuses() throws Exception {
        JobDescription pending1 = persistJob("Backend Java", JobStatus.PENDING_APPROVAL, employer);
        JobDescription pending2 = persistJob("Backend Go", JobStatus.PENDING_APPROVAL, employer);
        persistJob("Draft Job", JobStatus.DRAFT, employer);
        persistJob("Open Job", JobStatus.OPEN, employer);
        // pending1 older than pending2 ⇒ ordering by createdAt DESC is deterministic.
        ageOut(pending1, 2);

        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation")))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].id").value(pending2.getId()))
                .andExpect(jsonPath("$.data.content[1].id").value(pending1.getId()))
                .andExpect(jsonPath("$.data.content[0].status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.data.content[0].employerEmail").value("employer@example.com"))
                .andExpect(jsonPath("$.data.content[0].companyName").value("ACME"));

        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation").param("status", "OPEN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("Open Job"));

        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation").param("status", "DRAFT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("Draft Job"));

        // Đối chiếu SQL: countLiveByStatus phải khớp totalElements từng trạng thái.
        Map<String, Long> counts = new java.util.HashMap<>();
        for (Object[] row : jobRepository.countLiveByStatus()) {
            counts.put(((JobStatus) row[0]).name(), ((Number) row[1]).longValue());
        }
        assertEquals(2L, counts.get(JobStatus.PENDING_APPROVAL.name()));
        assertEquals(1L, counts.get(JobStatus.OPEN.name()));
        assertEquals(1L, counts.get(JobStatus.DRAFT.name()));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("AC-1: queue search matches title / company / employer email and paginates")
    void testQueue_SearchFilters() throws Exception {
        persistJob("Java Backend", JobStatus.PENDING_APPROVAL, employer);
        persistJob("Marketing Lead", JobStatus.PENDING_APPROVAL, employer);

        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation").param("search", "java")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("Java Backend"));

        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation").param("search", "employer@")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation").param("search", "zzz-no-match")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation").param("size", "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(2));
    }

    // ==================== AC-2 + AC-3: lifecycle ghi record, timeline mới nhất trước ====

    @Test
    @DisplayName("AC-2/AC-3: submit→SUBMITTED(actor=NTD); reject→REJECTED+reviewNote+decidedAt; resubmit→RESUBMITTED")
    void testLifecycle_RecordsAndTimeline() throws Exception {
        JobDescription job = persistJob("Data Engineer", JobStatus.DRAFT, employer);

        mockMvc.perform(asEmployer(patch("/api/v1/jobs/" + job.getId() + "/submit")))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"));

        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation/" + job.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submittedAt").exists())
                .andExpect(jsonPath("$.data.decidedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.timeline.length()").value(1))
                .andExpect(jsonPath("$.data.timeline[0].action").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.timeline[0].actorEmail").value("employer@example.com"))
                .andExpect(jsonPath("$.data.timeline[0].actorRole").value("EMPLOYER"));

        mockMvc.perform(asAdmin(patch("/api/v1/jobs/" + job.getId() + "/review"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":false,\"reason\":\"Thieu mo ta luong\"}"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.reviewNote").value("Thieu mo ta luong"));

        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation/" + job.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decidedAt").exists())
                .andExpect(jsonPath("$.data.timeline.length()").value(2))
                // Mới nhất lên đầu (AC-3).
                .andExpect(jsonPath("$.data.timeline[0].action").value("REJECTED"))
                .andExpect(jsonPath("$.data.timeline[0].actorEmail").value("admin@example.com"))
                .andExpect(jsonPath("$.data.timeline[0].actorRole").value("ADMIN"))
                .andExpect(jsonPath("$.data.timeline[0].note").value("Thieu mo ta luong"))
                .andExpect(jsonPath("$.data.timeline[1].action").value("SUBMITTED"));

        mockMvc.perform(asEmployer(patch("/api/v1/jobs/" + job.getId() + "/submit")))
                .andExpect(status().isOk());

        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation/" + job.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timeline.length()").value(3))
                .andExpect(jsonPath("$.data.timeline[0].action").value("RESUBMITTED"));

        assertEquals(3, moderationRepository.findByJobIdOrderByCreatedAtDesc(job.getId()).size());
    }

    @Test
    @DisplayName("AC-2: approval clears reviewNote and moves the job to OPEN")
    void testLifecycle_Approve() throws Exception {
        JobDescription job = persistJob("QA Lead", JobStatus.PENDING_APPROVAL, employer);
        job.setReviewNote("Dot truoc bi tu choi");
        jobRepository.saveAndFlush(job);

        mockMvc.perform(asAdmin(patch("/api/v1/jobs/" + job.getId() + "/review"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.reviewNote").value(nullValue()));

        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation/" + job.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timeline[0].action").value("APPROVED"))
                .andExpect(jsonPath("$.data.decidedAt").exists());
    }

    // ==================== AC-4: history lọc đúng, stats khớp đếm SQL ====================

    @Test
    @DisplayName("AC-4: history filters by decision/moderator/search/date; stats match manual counts")
    void testHistory_AndStats() throws Exception {
        JobDescription job1 = persistJob("Backend A", JobStatus.DRAFT, employer);
        JobDescription job2 = persistJob("Backend B", JobStatus.DRAFT, employer);
        submit(job1);
        submit(job2);
        mockMvc.perform(asAdmin(patch("/api/v1/jobs/" + job1.getId() + "/review"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"approved\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(asAdmin(patch("/api/v1/jobs/" + job2.getId() + "/review"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":false,\"reason\":\"Thieu yeu cau\"}"))
                .andExpect(status().isOk());

        // History là trang "bản ghi quyết định" (FR-006): chỉ APPROVED/REJECTED,
        // còn SUBMITTED/RESUBMITTED của employer nằm ở timeline từng job (E2).
        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation/history")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation/history").param("decision", "APPROVED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].action").value("APPROVED"))
                .andExpect(jsonPath("$.data.content[0].moderatorEmail").value("admin@example.com"))
                .andExpect(jsonPath("$.data.content[0].jobTitle").value("Backend A"))
                .andExpect(jsonPath("$.data.content[0].processingSeconds").isNumber());

        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation/history")
                        .param("decision", "REJECTED").param("moderatorId", String.valueOf(adminId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].note").value("Thieu yeu cau"))
                // Bản ghi từ chối vẫn giữ được thời gian chờ (submittedAt chụp trước khi xóa).
                .andExpect(jsonPath("$.data.content[0].processingSeconds").isNumber());

        // moderatorId = employer ⇒ rỗng: người gửi tin không phải người quyết định.
        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation/history")
                        .param("moderatorId", String.valueOf(employer.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation/history")
                        .param("moderatorId", String.valueOf(adminId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation/history").param("search", "Backend A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        String today = LocalDate.now().toString();
        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation/history")
                        .param("from", today).param("to", today)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation/history")
                        .param("from", LocalDate.now().plusDays(1).toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        // Stats khớp đếm thủ công: 1 approved + 1 rejected, avg có số.
        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation/history/stats")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalDecisions").value(2))
                .andExpect(jsonPath("$.data.approved").value(1))
                .andExpect(jsonPath("$.data.rejected").value(1))
                .andExpect(jsonPath("$.data.avgProcessingSeconds").isNumber());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("AC-4/AC-6: decision chỉ nhận APPROVED/REJECTED; from>to ⇒ 400; enum lạ ⇒ 400")
    void testHistory_InvalidInput() throws Exception {
        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation/history").param("decision", "SUBMITTED")))
                .andDo(print())
                .andExpect(status().isBadRequest());

        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation/history")
                        .param("from", "2026-10-20").param("to", "2026-10-10")))
                .andExpect(status().isBadRequest());

        // enum binding fail ⇒ 400 chứ không 500.
        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation/history").param("decision", "FOO")))
                .andExpect(status().isBadRequest());
    }

    // ==================== AC-6: input ma trận, không 500 ====================

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("AC-6: id lạ ⇒ 404, job xóa mềm ⇒ 404, status=FOO ⇒ 400, search='%' không khớp gì")
    void testConsole_InputMatrix() throws Exception {
        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation/999999")))
                .andExpect(status().isNotFound());

        JobDescription job = persistJob("Hidden Job", JobStatus.PENDING_APPROVAL, employer);
        jdbcTemplate.update("update job_descriptions set deleted_at = ? where id = ?",
                LocalDateTime.now(), job.getId());
        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation/" + job.getId())))
                .andExpect(status().isNotFound());
        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation").param("status", "FOO")))
                .andExpect(status().isBadRequest());

        persistJob("Percent Job", JobStatus.PENDING_APPROVAL, employer);
        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation").param("search", "%")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation").param("size", "101")))
                .andExpect(status().isBadRequest());
    }

    // ==================== AC-5: bảo vệ endpoint, hàng chờ đọc mở cho STAFF (009 FR-007) =====

    @Test
    @DisplayName("AC-5 + 009 FR-007: anonymous ⇒ 401, STAFF đọc được 4 endpoint hàng chờ, admin-only vẫn 403")
    void testConsole_Anonymous401_StaffReadsQueue() throws Exception {
        // Id thật: STAFF đi tới tận service nên một id không tồn tại sẽ trả 404 chứ không phải 403.
        JobDescription pending = persistJob("Queue readable by staff", JobStatus.PENDING_APPROVAL, employer);

        // Builder mới cho từng request: MockHttpServletRequestBuilder tích trạng thái post-processor.
        List<java.util.function.Supplier<MockHttpServletRequestBuilder>> calls = List.of(
                () -> get("/api/v1/admin/jobs/moderation"),
                () -> get("/api/v1/admin/jobs/moderation/" + pending.getId()),
                () -> get("/api/v1/admin/jobs/moderation/history"),
                () -> get("/api/v1/admin/jobs/moderation/history/stats"),
                () -> get("/api/v1/admin/jobs/moderation/stats"));

        for (var call : calls) {
            mockMvc.perform(call.get().with(csrf()))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(call.get().with(user(staff.getEmail()).roles("STAFF")).with(csrf()))
                    .andExpect(status().isOk());
        }

        // STAFF chỉ được miền kiểm duyệt JD; mọi endpoint admin khác vẫn 403.
        mockMvc.perform(get("/api/v1/admin/users")
                        .with(user(staff.getEmail()).roles("STAFF")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-5 (hồi quy 003): PATCH review vẫn 200 với STAFF, record ghi actorRole=STAFF")
    void testReview_StaffStillAllowed() throws Exception {
        JobDescription job = persistJob("Staff Reviews Me", JobStatus.PENDING_APPROVAL, employer);

        mockMvc.perform(asStaff(patch("/api/v1/jobs/" + job.getId() + "/review"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"));

        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation/" + job.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timeline[0].actorRole").value("STAFF"));
    }

    // ==================== helpers ====================

    private User buildUser(String email, String fullName, Role role) {
        return User.builder()
                .email(email)
                .password(passwordEncoder.encode("password123"))
                .fullName(fullName)
                .role(role)
                .enabled(true)
                .locked(false)
                .build();
    }

    private JobDescription persistJob(String title, JobStatus status, User owner) {
        return jobRepository.save(JobDescription.builder()
                .employer(owner)
                .title(title)
                .companyName("ACME")
                .position("Dev")
                .status(status)
                .build());
    }

    /** Moves created_at back so createdAt-DESC ordering is deterministic. */
    private void ageOut(JobDescription job, int days) {
        jdbcTemplate.update("update job_descriptions set created_at = ? where id = ?",
                LocalDateTime.now().minusDays(days), job.getId());
    }

    private void submit(JobDescription job) throws Exception {
        mockMvc.perform(asEmployer(patch("/api/v1/jobs/" + job.getId() + "/submit")))
                .andExpect(status().isOk());
    }

    private Long adminId() {
        return userRepository.findByEmail("admin@example.com").orElseThrow().getId();
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder call) {
        return call.with(user("admin@example.com").roles("ADMIN")).with(csrf());
    }

    private MockHttpServletRequestBuilder asEmployer(MockHttpServletRequestBuilder call) {
        return call.with(user(employer.getEmail()).roles("INTERVIEWER")).with(csrf());
    }

    private MockHttpServletRequestBuilder asStaff(MockHttpServletRequestBuilder call) {
        return call.with(user(staff.getEmail()).roles("STAFF")).with(csrf());
    }
}
