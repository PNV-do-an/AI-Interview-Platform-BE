package com.aiinterview.platform.controller;

import com.aiinterview.platform.model.entity.JobDescription;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.ApplicationStatus;
import com.aiinterview.platform.model.enums.JobStatus;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.ApplicationRepository;
import com.aiinterview.platform.model.repository.ApplicationStatusHistoryRepository;
import com.aiinterview.platform.model.repository.JobDescriptionRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.service.EmailService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lô 2 — timeline trạng thái đơn ứng tuyển, chi tiết đơn theo quyền xem, và phễu ứng tuyển
 * (FR-APP-02, FR-ASM-03, BR-09). Mỗi con số phễu đối chiếu bằng SQL trên đúng DB đang test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Application workflow: timeline, detail, funnel")
class ApplicationWorkflowIntegrationTest {

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
    private UserRepository userRepository;
    @Autowired
    private JobDescriptionRepository jobRepository;
    @Autowired
    private ApplicationRepository applicationRepository;
    @Autowired
    private ApplicationStatusHistoryRepository historyRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User employer;
    private JobDescription job;

    @BeforeEach
    void setUp() {
        // history giữ FK tới applications và users nên phải xóa trước.
        historyRepository.deleteAll();
        applicationRepository.deleteAll();
        jobRepository.deleteAll();
        userRepository.deleteAll();

        employer = save("employer@example.com", "Employer One", Role.ROLE_INTERVIEWER);
        job = jobRepository.save(JobDescription.builder()
                .employer(employer)
                .title("Backend Java Developer")
                .companyName("ACME")
                .status(JobStatus.OPEN)
                .build());
    }

    // ==================== FR-APP-02: timeline ==========

    @Test
    @DisplayName("APP-1: nộp đơn ghi dòng timeline đầu tiên, fromStatus trống")
    void testApply_WritesFirstTimelineRow() throws Exception {
        User candidate = save("candidate@example.com", "Candidate One", Role.ROLE_USER);
        long id = applyAs(candidate);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select from_status, to_status, actor_user_id, note from application_status_history "
                        + "where application_id = ?", id);

        assertEquals(1, rows.size());
        assertNull(rows.get(0).get("from_status"));
        assertEquals("PENDING", rows.get(0).get("to_status"));
        assertEquals(candidate.getId(), rows.get(0).get("actor_user_id"));
        assertNull(rows.get(0).get("note"));
    }

    @Test
    @DisplayName("APP-2: ghi chú nội bộ chỉ phía tuyển dụng thấy, candidate không thấy note lẫn email người duyệt")
    void testDetail_NoteVisibilityFollowsRole() throws Exception {
        User candidate = save("candidate@example.com", "Candidate One", Role.ROLE_USER);
        long id = applyAs(candidate);
        evaluate(id, "{\"status\":\"SHORTLISTED\",\"score\":82,\"note\":\"Trúng tiêu chí sơ loại\"}");

        mockMvc.perform(asEmployer(get("/api/v1/applications/" + id)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.status").value("SHORTLISTED"))
                .andExpect(jsonPath("$.data.score").value(82))
                .andExpect(jsonPath("$.data.candidateEmail").value("candidate@example.com"))
                .andExpect(jsonPath("$.data.statusHistory.length()").value(2))
                .andExpect(jsonPath("$.data.statusHistory[1].fromStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.statusHistory[1].toStatus").value("SHORTLISTED"))
                .andExpect(jsonPath("$.data.statusHistory[1].note").value("Trúng tiêu chí sơ loại"))
                .andExpect(jsonPath("$.data.statusHistory[1].actorEmail").value("employer@example.com"));

        mockMvc.perform(as(get("/api/v1/applications/" + id), candidate, "USER"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusHistory.length()").value(2))
                .andExpect(jsonPath("$.data.statusHistory[1].toStatus").value("SHORTLISTED"))
                // BR-09: candidate thấy mốc thời gian nhưng không thấy ghi chú nội bộ hay danh tính người duyệt.
                .andExpect(jsonPath("$.data.statusHistory[1].note").value(nullValue()))
                .andExpect(jsonPath("$.data.statusHistory[1].actorEmail").value(nullValue()))
                .andExpect(jsonPath("$.data.statusHistory[1].actorId").value(nullValue()));
    }

    @Test
    @DisplayName("APP-3: chấm lại điểm ở cùng vòng không nhân bản dòng timeline")
    void testEvaluate_SameStatusRevisionAddsNoTimelineRow() throws Exception {
        User candidate = save("candidate@example.com", "Candidate One", Role.ROLE_USER);
        long id = applyAs(candidate);
        evaluate(id, "{\"status\":\"SHORTLISTED\",\"score\":82}");

        mockMvc.perform(asEmployer(patch("/api/v1/applications/" + id + "/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHORTLISTED\",\"score\":91}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(91));

        assertEquals(2, historyCount(id));
        assertEquals(91, applicationRepository.findById(id).orElseThrow().getScore());
    }

    @Test
    @DisplayName("APP-4: nhảy vòng bị cấm vẫn 400 và không ghi timeline")
    void testEvaluate_IllegalJumpRejected() throws Exception {
        User candidate = save("candidate@example.com", "Candidate One", Role.ROLE_USER);
        long id = applyAs(candidate);

        mockMvc.perform(asEmployer(patch("/api/v1/applications/" + id + "/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACCEPTED\",\"score\":99}")))
                .andDo(print())
                .andExpect(status().isBadRequest());

        assertEquals(ApplicationStatus.PENDING, applicationRepository.findById(id).orElseThrow().getStatus());
        assertEquals(1, historyCount(id));
    }

    @Test
    @DisplayName("APP-5: ứng viên tự rút đơn cũng xuất hiện trên timeline")
    void testCancel_AddsTimelineRow() throws Exception {
        User candidate = save("candidate@example.com", "Candidate One", Role.ROLE_USER);
        long id = applyAs(candidate);

        mockMvc.perform(as(patch("/api/v1/applications/" + id + "/cancel"), candidate, "USER"))
                .andExpect(status().isOk());

        assertEquals(ApplicationStatus.CANCELLED, applicationRepository.findById(id).orElseThrow().getStatus());
        assertEquals(2, historyCount(id));
        mockMvc.perform(asEmployer(get("/api/v1/applications/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusHistory[1].fromStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.statusHistory[1].toStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.data.statusHistory[1].actorEmail").value("candidate@example.com"));
    }

    @Test
    @DisplayName("APP-6: người không liên quan bị chặn xem chi tiết, anonymous 401")
    void testDetail_ForeignViewerRejected() throws Exception {
        User candidate = save("candidate@example.com", "Candidate One", Role.ROLE_USER);
        User stranger = save("stranger@example.com", "Stranger", Role.ROLE_USER);
        long id = applyAs(candidate);

        mockMvc.perform(as(get("/api/v1/applications/" + id), stranger, "USER"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/applications/" + id).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    // ==================== FR-ASM-03: phễu ==========

    @Test
    @DisplayName("ASM-1: phễu đếm theo vòng xa nhất từng đạt, đơn bị loại vẫn tính ở vòng trước")
    void testFunnel_CountsReachedStagesNotCurrentStatus() throws Exception {
        // 1 đơn mới nộp: PENDING
        applyAs(save("c1@example.com", "C One", Role.ROLE_USER));
        // 1 đơn đang ở SHORTLISTED
        long shortlisted = applyAs(save("c2@example.com", "C Two", Role.ROLE_USER));
        evaluate(shortlisted, "{\"status\":\"SHORTLISTED\",\"score\":70}");
        // 1 đơn đã đạt ACCEPTED đi qua đủ mọi vòng
        long accepted = applyAs(save("c3@example.com", "C Three", Role.ROLE_USER));
        evaluate(accepted, "{\"status\":\"SHORTLISTED\",\"score\":88}");
        evaluate(accepted, "{\"status\":\"SCHEDULED\"}");
        evaluate(accepted, "{\"status\":\"INTERVIEWED\",\"score\":95}");
        evaluate(accepted, "{\"status\":\"ACCEPTED\",\"score\":95}");
        // 1 đơn bị loại ngay ở vòng hẹn lịch: trạng thái hiện tại là REJECTED nhưng từng đạt SCHEDULED
        long rejected = applyAs(save("c4@example.com", "C Four", Role.ROLE_USER));
        evaluate(rejected, "{\"status\":\"SHORTLISTED\",\"score\":65}");
        evaluate(rejected, "{\"status\":\"SCHEDULED\"}");
        evaluate(rejected, "{\"status\":\"REJECTED\",\"score\":40}");
        // 1 đơn rút lui: chỉ từng ở PENDING
        User quitter = save("c5@example.com", "C Five", Role.ROLE_USER);
        long withdrawn = applyAs(quitter);
        mockMvc.perform(as(patch("/api/v1/applications/" + withdrawn + "/cancel"), quitter, "USER"))
                .andExpect(status().isOk());

        mockMvc.perform(asEmployer(get("/api/v1/applications/jobs/" + job.getId() + "/funnel")))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").value(job.getId()))
                .andExpect(jsonPath("$.data.totalApplications").value(5))
                .andExpect(jsonPath("$.data.stages.length()").value(5))
                // PENDING: cả 5 đơn đều từng nộp; 3 đơn đi tiếp qua sơ loại ⇒ 3/5.
                .andExpect(jsonPath("$.data.stages[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.stages[0].reached").value(5))
                .andExpect(jsonPath("$.data.stages[0].advanced").value(3))
                .andExpect(jsonPath("$.data.stages[0].passRate").value(closeTo(0.6, 1e-9)))
                // SHORTLISTED: c2 đang chờ + c3 đã đạt + c4 bị loại ở vòng sau ⇒ 3 đơn từng đạt.
                .andExpect(jsonPath("$.data.stages[1].status").value("SHORTLISTED"))
                .andExpect(jsonPath("$.data.stages[1].reached").value(3))
                .andExpect(jsonPath("$.data.stages[1].advanced").value(2))
                .andExpect(jsonPath("$.data.stages[1].passRate").value(closeTo(2.0 / 3.0, 1e-9)))
                // SCHEDULED: c3 và c4; c4 sau đó bị REJECTED nhưng vẫn từng đạt vòng này.
                .andExpect(jsonPath("$.data.stages[2].status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.stages[2].reached").value(2))
                .andExpect(jsonPath("$.data.stages[2].passRate").value(closeTo(0.5, 1e-9)))
                .andExpect(jsonPath("$.data.stages[3].status").value("INTERVIEWED"))
                .andExpect(jsonPath("$.data.stages[3].reached").value(1))
                .andExpect(jsonPath("$.data.stages[3].passRate").value(closeTo(1.0, 1e-9)))
                .andExpect(jsonPath("$.data.stages[4].status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.stages[4].reached").value(1))
                // Bước cuối không có tỷ lệ đi tiếp.
                .andExpect(jsonPath("$.data.stages[4].advanced").value(nullValue()))
                .andExpect(jsonPath("$.data.stages[4].passRate").value(nullValue()));

        // Đối chiếu SQL: số đơn CÓ dòng lịch sử chạm tới SHORTLISTED trở lên = 3.
        assertEquals(3L, jdbcTemplate.queryForObject(
                "select count(distinct application_id) from application_status_history "
                        + "where to_status in ('SHORTLISTED','SCHEDULED','INTERVIEWED','ACCEPTED')", Long.class));
        assertEquals(5L, jdbcTemplate.queryForObject(
                "select count(*) from applications where job_id = ?", Long.class, job.getId()));
    }

    @Test
    @DisplayName("ASM-2: chưa có đơn nào ⇒ mọi mốc 0 và passRate null, không bịa số")
    void testFunnel_EmptyJobReturnsNullRates() throws Exception {
        mockMvc.perform(asEmployer(get("/api/v1/applications/jobs/" + job.getId() + "/funnel")))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalApplications").value(0))
                .andExpect(jsonPath("$.data.stages[0].reached").value(0))
                .andExpect(jsonPath("$.data.stages[0].passRate").value(nullValue()));
    }

    @Test
    @DisplayName("ASM-3: phễu chỉ chủ tin hoặc admin/staff xem được, candidate khác bị chặn")
    void testFunnel_OwnerOnly() throws Exception {
        User otherEmployer = save("other@example.com", "Other Employer", Role.ROLE_INTERVIEWER);
        mockMvc.perform(as(get("/api/v1/applications/jobs/" + job.getId() + "/funnel"), otherEmployer, "INTERVIEWER"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/applications/jobs/" + job.getId() + "/funnel").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    // ==================== helpers ==========

    private long applyAs(User candidate) throws Exception {
        String body = mockMvc.perform(as(post("/api/v1/applications/jobs/" + job.getId()), candidate, "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coverLetter\":\"Em ứng tuyển vị trí backend.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        return com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(body).path("data").path("id").asLong();
    }

    private void evaluate(long applicationId, String jsonBody) throws Exception {
        mockMvc.perform(asEmployer(patch("/api/v1/applications/" + applicationId + "/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody)))
                .andExpect(status().isOk());
    }

    private int historyCount(long applicationId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from application_status_history where application_id = ?",
                Integer.class, applicationId);
        return count == null ? 0 : count;
    }

    private User save(String email, String fullName, Role role) {
        return userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode("password123"))
                .fullName(fullName)
                .role(role)
                .enabled(true)
                .locked(false)
                .build());
    }

    private MockHttpServletRequestBuilder asEmployer(MockHttpServletRequestBuilder call) {
        return as(call, employer, "INTERVIEWER");
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder call, User actor, String role) {
        return call.with(user(actor.getEmail()).roles(role)).with(csrf());
    }
}
