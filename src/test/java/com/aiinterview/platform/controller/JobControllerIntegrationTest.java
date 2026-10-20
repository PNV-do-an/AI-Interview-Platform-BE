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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Job wizard + lifecycle integration tests (003)")
class JobControllerIntegrationTest {

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

    private User employer;
    private User admin;
    private User candidate;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        // 004 thêm FK job_moderation_records.job_id -> phải xóa bản ghi duyệt trước tin.
        jdbcTemplate.execute("delete from job_moderation_records");
        jobRepository.deleteAll();
        userRepository.deleteAll();

        employer = userRepository.save(User.builder()
                .email("employer@example.com")
                .password(passwordEncoder.encode("password123"))
                .fullName("Employer User")
                .role(Role.ROLE_INTERVIEWER)
                .locked(false)
                .build());
        admin = userRepository.save(User.builder()
                .email("admin@example.com")
                .password(passwordEncoder.encode("password123"))
                .fullName("Admin User")
                .role(Role.ROLE_ADMIN)
                .locked(false)
                .build());
        candidate = userRepository.save(User.builder()
                .email("candidate@example.com")
                .password(passwordEncoder.encode("password123"))
                .fullName("Candidate User")
                .role(Role.ROLE_USER)
                .locked(false)
                .build());
    }

    private String draftPayload() {
        return """
                {
                  "title": "Senior Distributed Systems Engineer",
                  "companyName": "InterviewAI Platform",
                  "location": "San Francisco, CA"
                }
                """;
    }

    private String fullWizardPayload() {
        return """
                {
                  "title": "Senior Distributed Systems Engineer",
                  "companyName": "InterviewAI Platform",
                  "location": "San Francisco, CA",
                  "department": "Platform Infrastructure",
                  "experienceLevel": "Senior (5-8+ yrs)",
                  "employmentType": "Full-Time Regular",
                  "workplaceModel": "HYBRID",
                  "timezoneRequirement": "PST / EST core overlap +/-3 hrs",
                  "salaryCurrency": "USD",
                  "salaryMin": 165000,
                  "salaryMax": 210000,
                  "equityBand": "0.05% - 0.15%",
                  "annualBonus": "15% Performance Target",
                  "salaryRange": "$165,000 - $210,000",
                  "requiredSkills": "Go,Kubernetes,Kafka",
                  "description": "<p>Architect and scale ingestion clusters.</p>",
                  "requirements": "<ul><li>5+ years Go</li></ul>",
                  "aiScreeningEnabled": true,
                  "aiScreeningTrack": "LIVE_CODING",
                  "aiScreeningPassScore": 80,
                  "aiQuestionFocusAreas": "Distributed Consensus,Lock-Free Concurrency"
                }
                """;
    }

    private long createDraftViaApi() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/jobs")
                        .with(user(employer.getEmail()).roles("INTERVIEWER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã lưu bản nháp tin tuyển dụng"))
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return data.get("id").asLong();
    }

    private long publishJobViaApi() throws Exception {
        long jobId = createDraftViaApi();
        mockMvc.perform(patch("/api/v1/jobs/{id}/submit", jobId)
                        .with(user(employer.getEmail()).roles("INTERVIEWER")))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/jobs/{id}/review", jobId)
                        .with(user(admin.getEmail()).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\": true}"))
                .andExpect(status().isOk());
        return jobId;
    }

    @Test
    @DisplayName("AC-1: POST tạo DRAFT, public list không thấy, my-jobs thấy")
    void createDraft_savesDraft_hiddenFromPublic_visibleInMyJobs() throws Exception {
        long jobId = createDraftViaApi();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select status from job_descriptions where id = ?", jobId);
        assertThat(row.get("status")).isEqualTo("DRAFT");

        mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));

        mockMvc.perform(get("/api/v1/jobs/my-jobs")
                        .with(user(employer.getEmail()).roles("INTERVIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].status").value("DRAFT"));
    }

    @Test
    @DisplayName("AC-2: PUT wizard lưu được toàn bộ field mới xuống DB")
    void updateWizardFields_persistsAllNewColumns() throws Exception {
        long jobId = createDraftViaApi();

        mockMvc.perform(put("/api/v1/jobs/{id}", jobId)
                        .with(user(employer.getEmail()).roles("INTERVIEWER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fullWizardPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workplaceModel").value("HYBRID"))
                .andExpect(jsonPath("$.data.salaryMin").value(165000))
                .andExpect(jsonPath("$.data.salaryMax").value(210000))
                .andExpect(jsonPath("$.data.aiScreeningEnabled").value(true))
                .andExpect(jsonPath("$.data.aiScreeningPassScore").value(80))
                .andExpect(jsonPath("$.data.reviewNote").doesNotExist());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select department, workplace_model, salary_currency, salary_min, salary_max, "
                        + "ai_screening_enabled, ai_screening_track, ai_screening_pass_score, "
                        + "ai_question_focus_areas from job_descriptions where id = ?", jobId);
        assertThat(row.get("department")).isEqualTo("Platform Infrastructure");
        assertThat(row.get("workplace_model")).isEqualTo("HYBRID");
        assertThat(row.get("salary_currency")).isEqualTo("USD");
        assertThat(((Number) row.get("salary_min")).longValue()).isEqualTo(165000L);
        assertThat(((Number) row.get("salary_max")).longValue()).isEqualTo(210000L);
        assertThat(Boolean.TRUE.equals(row.get("ai_screening_enabled"))).isTrue();
        assertThat(row.get("ai_screening_track")).isEqualTo("LIVE_CODING");
        assertThat(((Number) row.get("ai_screening_pass_score")).intValue()).isEqualTo(80);
        assertThat(row.get("ai_question_focus_areas")).isEqualTo("Distributed Consensus,Lock-Free Concurrency");
    }

    @Test
    @DisplayName("AC-6: passScore 96 bị chặn 400 ngay lúc ghi")
    void createJob_passScoreOutOfRange_returns400() throws Exception {
        String payload = """
                {"title": "Backend Dev", "companyName": "ACME", "aiScreeningEnabled": true,
                 "aiScreeningTrack": "LIVE_CODING", "aiScreeningPassScore": 96}
                """;
        mockMvc.perform(post("/api/v1/jobs")
                        .with(user(employer.getEmail()).roles("INTERVIEWER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dữ liệu không hợp lệ"));
        assertThat(jobRepository.count()).isZero();
    }

    @Test
    @DisplayName("AC-6: salaryMin > salaryMax bị chặn 400, không lưu")
    void updateJob_salaryMinGreaterThanMax_returns400() throws Exception {
        long jobId = createDraftViaApi();
        String payload = """
                {"title": "Senior Distributed Systems Engineer", "companyName": "InterviewAI Platform",
                 "salaryMin": 300000, "salaryMax": 210000}
                """;
        mockMvc.perform(put("/api/v1/jobs/{id}", jobId)
                        .with(user(employer.getEmail()).roles("INTERVIEWER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mức lương tối thiểu không được lớn hơn mức lương tối đa"));
    }

    @Test
    @DisplayName("AC-6: workplaceModel giá trị lạ bị chặn 400")
    void createJob_unknownWorkplaceModel_returns400() throws Exception {
        String payload = """
                {"title": "Backend Dev", "companyName": "ACME", "workplaceModel": "ONSITEE"}
                """;
        mockMvc.perform(post("/api/v1/jobs")
                        .with(user(employer.getEmail()).roles("INTERVIEWER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
        assertThat(jobRepository.count()).isZero();
    }

    @Test
    @DisplayName("AC-3: submit từ DRAFT lên PENDING_APPROVAL, submit lần nữa 400")
    void submit_fromDraft_movesToPending_twiceFails() throws Exception {
        long jobId = createDraftViaApi();

        mockMvc.perform(patch("/api/v1/jobs/{id}/submit", jobId)
                        .with(user(employer.getEmail()).roles("INTERVIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã gửi duyệt tin tuyển dụng"))
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"));

        mockMvc.perform(patch("/api/v1/jobs/{id}/submit", jobId)
                        .with(user(employer.getEmail()).roles("INTERVIEWER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Chỉ bản nháp mới gửi duyệt được"));
    }

    @Test
    @DisplayName("AC-3: PUT khi đang PENDING_APPROVAL bị chặn 400")
    void updateJob_whilePendingApproval_returns400() throws Exception {
        long jobId = createDraftViaApi();
        mockMvc.perform(patch("/api/v1/jobs/{id}/submit", jobId)
                .with(user(employer.getEmail()).roles("INTERVIEWER"))).andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/jobs/{id}", jobId)
                        .with(user(employer.getEmail()).roles("INTERVIEWER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftPayload()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Tin đang chờ duyệt, không thể sửa"));
    }

    @Test
    @DisplayName("AC-6: PATCH /status từ DRAFT bị chặn; OPEN<->CLOSED vẫn chạy như cũ")
    void changeStatus_draftBlocked_openClosedStillWorks() throws Exception {
        long jobId = createDraftViaApi();

        mockMvc.perform(patch("/api/v1/jobs/{id}/status", jobId)
                        .param("status", "OPEN")
                        .with(user(employer.getEmail()).roles("INTERVIEWER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Trạng thái không hợp lệ cho tin tuyển dụng này"));

        long publishedId = publishJobViaApi();
        mockMvc.perform(patch("/api/v1/jobs/{id}/status", publishedId)
                        .param("status", "CLOSED")
                        .with(user(employer.getEmail()).roles("INTERVIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
        mockMvc.perform(patch("/api/v1/jobs/{id}/status", publishedId)
                        .param("status", "OPEN")
                        .with(user(employer.getEmail()).roles("INTERVIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"));
    }

    @Test
    @DisplayName("AC-4: duyệt xong OPEN, public list thấy, anonymous GET /{id} được 200")
    void review_approve_publishesJob_publiclyVisible() throws Exception {
        long jobId = createDraftViaApi();
        mockMvc.perform(patch("/api/v1/jobs/{id}/submit", jobId)
                .with(user(employer.getEmail()).roles("INTERVIEWER"))).andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/jobs/{id}/review", jobId)
                        .with(user(admin.getEmail()).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Tin tuyển dụng đã được duyệt"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.reviewNote").doesNotExist());

        mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("Senior Distributed Systems Engineer"));

        mockMvc.perform(get("/api/v1/jobs/{id}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"));
    }

    @Test
    @DisplayName("AC-5: từ chối thiếu lý do 400; đủ lý do về DRAFT kèm reviewNote; duyệt lại xóa note")
    void review_reject_requiresReason_backToDraft_resubmittable() throws Exception {
        long jobId = createDraftViaApi();
        mockMvc.perform(patch("/api/v1/jobs/{id}/submit", jobId)
                .with(user(employer.getEmail()).roles("INTERVIEWER"))).andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/jobs/{id}/review", jobId)
                        .with(user(admin.getEmail()).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\": false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Vui lòng nêu lý do từ chối"));

        mockMvc.perform(patch("/api/v1/jobs/{id}/review", jobId)
                        .with(user(admin.getEmail()).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\": false, \"reason\": \"Thiếu mô tả trách nhiệm\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Tin tuyển dụng đã bị từ chối"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.reviewNote").value("Thiếu mô tả trách nhiệm"));

        // Job bị từ chối không hiện công khai
        mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));

        // Employer sửa lại được rồi submit lại được
        mockMvc.perform(put("/api/v1/jobs/{id}", jobId)
                        .with(user(employer.getEmail()).roles("INTERVIEWER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fullWizardPayload()))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/jobs/{id}/submit", jobId)
                .with(user(employer.getEmail()).roles("INTERVIEWER"))).andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/jobs/{id}/review", jobId)
                        .with(user(admin.getEmail()).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.reviewNote").doesNotExist());
    }

    @Test
    @DisplayName("AC-7: GET /{id} bản nháp — anonymous và candidate 404, owner 200")
    void getJobDetail_draft_hiddenFromStrangers_ownerSees() throws Exception {
        long jobId = createDraftViaApi();

        mockMvc.perform(get("/api/v1/jobs/{id}", jobId))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/jobs/{id}", jobId)
                        .with(user(candidate.getEmail()).roles("USER")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/jobs/{id}", jobId)
                        .with(user(employer.getEmail()).roles("INTERVIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    @DisplayName("AC-7: review chỉ từ PENDING_APPROVAL, sai trạng thái 400")
    void review_wrongInitialState_returns400() throws Exception {
        long jobId = createDraftViaApi();
        mockMvc.perform(patch("/api/v1/jobs/{id}/review", jobId)
                        .with(user(admin.getEmail()).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\": true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Tin không ở trạng thái chờ duyệt"));
    }

    @Test
    @DisplayName("AC-7: ROLE_USER bị 403 ở mọi endpoint ghi job")
    void writeEndpoints_forbiddenForCandidateRole() throws Exception {
        long jobId = createDraftViaApi();

        mockMvc.perform(post("/api/v1/jobs")
                        .with(user(candidate.getEmail()).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftPayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/jobs/{id}", jobId)
                        .with(user(candidate.getEmail()).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftPayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/jobs/{id}/submit", jobId)
                        .with(user(candidate.getEmail()).roles("USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/jobs/{id}/review", jobId)
                        .with(user(candidate.getEmail()).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\": true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-7: anonymous gọi endpoint ghi job bị 401")
    void writeEndpoints_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftPayload()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/v1/jobs/{id}/submit", 999))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/v1/jobs/{id}/review", 999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\": true}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AC-7: review ghi audit với admin thật, không có 0/system")
    void review_writesAuditWithRealAdmin() throws Exception {
        long jobId = createDraftViaApi();
        mockMvc.perform(patch("/api/v1/jobs/{id}/submit", jobId)
                .with(user(employer.getEmail()).roles("INTERVIEWER"))).andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/jobs/{id}/review", jobId)
                        .with(user(admin.getEmail()).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\": true}"))
                .andExpect(status().isOk());

        var logs = auditLogRepository.findAll();
        assertThat(logs).hasSize(1);
        var entry = logs.get(0);
        assertThat(entry.getAction()).isEqualTo("APPROVE_JOB");
        assertThat(entry.getAdminId()).isEqualTo(admin.getId());
        assertThat(entry.getAdminEmail()).isEqualTo(admin.getEmail());
        assertThat(entry.getTargetUserId()).isNull();
        assertThat(entry.getDetail()).contains("job #" + jobId);
    }

    @Test
    @DisplayName("AC-8 hồi quy: GET /{id} job OPEN anonymous vẫn 200 như trước")
    void regression_openJobDetail_stillPublic() throws Exception {
        long jobId = publishJobViaApi();
        mockMvc.perform(get("/api/v1/jobs/{id}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"));
    }
}
