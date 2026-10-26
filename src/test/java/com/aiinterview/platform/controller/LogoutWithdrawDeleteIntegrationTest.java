package com.aiinterview.platform.controller;

import com.aiinterview.platform.model.entity.JobDescription;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.JobStatus;
import com.aiinterview.platform.model.enums.ModerationAction;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.InterviewAnswerRepository;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lô 1 — ba endpoint còn thiếu của luồng ứng viên/nhà tuyển dụng:
 * đăng xuất thu hồi tức thời, rút tin khỏi hàng chờ duyệt, xóa phiên luyện tập.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Logout, withdraw JD, delete mock session")
class LogoutWithdrawDeleteIntegrationTest {

    private static final String CANDIDATE_EMAIL = "candidate@example.com";
    private static final String PASSWORD = "password123";

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
    private InterviewSessionRepository sessionRepository;
    @Autowired
    private InterviewAnswerRepository answerRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User candidate;
    private User employer;
    private User otherEmployer;
    private User admin;

    @BeforeEach
    void setUp() {
        answerRepository.deleteAll();
        sessionRepository.deleteAll();
        moderationRepository.deleteAll();
        jobRepository.deleteAll();
        userRepository.deleteAll();
        jdbcTemplate.update("delete from login_attempts");

        candidate = save(CANDIDATE_EMAIL, "Candidate One", Role.ROLE_USER);
        employer = save("employer@example.com", "Employer One", Role.ROLE_INTERVIEWER);
        otherEmployer = save("other@example.com", "Other Employer", Role.ROLE_INTERVIEWER);
        admin = save("admin@example.com", "Admin User", Role.ROLE_ADMIN);
    }

    // ==================== POST /auth/logout ==========

    @Test
    @DisplayName("LOG-1: logout làm access token đang còn hạn chết ngay, refresh token cũng chết")
    void testLogout_KillsAccessTokenAndRefreshToken() throws Exception {
        String[] tokens = login();

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens[0]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(CANDIDATE_EMAIL));

        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens[0]))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("đăng xuất")));

        assertEquals(1, userRepository.findByEmail(CANDIDATE_EMAIL).orElseThrow().getTokenVersion());

        // Access token TTL 24h nhưng claim "tv" đã lệch => JwtFilter không dựng Authentication.
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens[0]))
                .andDo(print())
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokens[1] + "\"}"))
                .andExpect(status().isUnauthorized());

        // Đăng nhập lại cấp token mang tv mới và dùng được bình thường.
        String[] fresh = login();
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + fresh[0]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(CANDIDATE_EMAIL));
        // Đăng nhập lại chỉ phát token mang tv hiện hành, không tự tăng version.
        assertEquals(1, userRepository.findByEmail(CANDIDATE_EMAIL).orElseThrow().getTokenVersion());
    }

    @Test
    @DisplayName("LOG-2: logout không có token ⇒ 401, không tăng token_version của ai cả")
    void testLogout_AnonymousRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized());
        assertEquals(0, userRepository.findByEmail(CANDIDATE_EMAIL).orElseThrow().getTokenVersion());
    }

    // ==================== PATCH /jobs/{id}/withdraw ==========

    @Test
    @DisplayName("JD-1: chủ tin rút tin đang chờ duyệt về bản nháp, đồng hồ hàng chờ tắt, timeline ghi WITHDRAWN")
    void testWithdraw_ReturnsJobToDraft() throws Exception {
        JobDescription job = draftJob("Backend Java Developer");
        mockMvc.perform(asEmployer(patch("/api/v1/jobs/" + job.getId() + "/submit")))
                .andExpect(status().isOk());

        mockMvc.perform(asEmployer(patch("/api/v1/jobs/" + job.getId() + "/withdraw")))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        JobDescription after = jobRepository.findById(job.getId()).orElseThrow();
        assertEquals(JobStatus.DRAFT, after.getStatus());
        // submittedAt bị xóa nên lần nộp lại tính lại thời gian chờ từ đầu.
        assertNull(after.getSubmittedAt());

        List<ModerationAction> timeline = moderationRepository.findByJobIdOrderByCreatedAtDesc(job.getId())
                .stream().map(r -> r.getAction()).toList();
        // OrderByCreatedAtDesc: dòng mới nhất (WITHDRAWN) đứng trước.
        assertEquals(List.of(ModerationAction.WITHDRAWN, ModerationAction.SUBMITTED), timeline);

        // Hàng chờ của admin phải giảm tương ứng: không còn tin nào chờ duyệt.
        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation/stats")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pending").value(0))
                .andExpect(jsonPath("$.data.draft").value(1));
    }

    @Test
    @DisplayName("JD-2: bản nháp chưa nộp và tin của chủ khác đều bị chặn rút")
    void testWithdraw_RejectedForDraftAndForeignOwner() throws Exception {
        JobDescription draft = draftJob("Data Engineer");
        mockMvc.perform(asEmployer(patch("/api/v1/jobs/" + draft.getId() + "/withdraw")))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("đang chờ duyệt")));

        JobDescription pending = draftJob("Backend Go Developer", otherEmployer);
        mockMvc.perform(as(otherEmployer, "INTERVIEWER", patch("/api/v1/jobs/" + pending.getId() + "/submit")))
                .andExpect(status().isOk());
        // Chủ tin B không được rút tin của A.
        mockMvc.perform(asEmployer(patch("/api/v1/jobs/" + pending.getId() + "/withdraw")))
                .andExpect(status().isBadRequest());
        assertEquals(JobStatus.PENDING_APPROVAL, jobRepository.findById(pending.getId()).orElseThrow().getStatus());
    }

    // ==================== DELETE /mock-interviews/{id} ==========

    @Test
    @DisplayName("MOCK-1: xóa phiên luyện xóa luôn câu trả lời, không để lại FK mồ côi")
    void testDeleteSession_RemovesSessionAndAnswers() throws Exception {
        long sessionId = startSession(candidate);
        mockMvc.perform(as(candidate, "USER", post("/api/v1/mock-interviews/" + sessionId + "/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answerText\":\"Dùng chỉ mục và tránh N+1 query.\"}")))
                .andExpect(status().isOk());
        assertEquals(1L, answerRepository.count());

        mockMvc.perform(as(candidate, "USER", delete("/api/v1/mock-interviews/" + sessionId)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("Đã xóa")));

        assertTrue(sessionRepository.findById(sessionId).isEmpty());
        // Bảng con phải sạch: còn dòng là DB chặn lần xóa sau.
        assertEquals(0L, answerRepository.count());
        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from interview_session_questions where session_id = ?",
                Integer.class, sessionId));

        mockMvc.perform(as(candidate, "USER", get("/api/v1/mock-interviews/" + sessionId)))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(candidate, "USER", get("/api/v1/mock-interviews/my")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("MOCK-2: người khác không xóa được phiên của ứng viên khác, anonymous 401")
    void testDeleteSession_OwnerOnly() throws Exception {
        long sessionId = startSession(candidate);
        User stranger = save("stranger@example.com", "Stranger", Role.ROLE_USER);

        mockMvc.perform(as(stranger, "USER", delete("/api/v1/mock-interviews/" + sessionId)))
                .andDo(print())
                .andExpect(status().isForbidden());
        assertNotNull(sessionRepository.findById(sessionId).orElse(null));

        mockMvc.perform(delete("/api/v1/mock-interviews/" + sessionId))
                .andExpect(status().isUnauthorized());
    }

    // ==================== helpers ==========

    /** Đăng nhập thật để lấy cặp token, vì test này đo chính claim "tv" trong token. */
    private String[] login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + CANDIDATE_EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
        assertTrue(!data.path("accessToken").asText().isBlank(), "login phải trả accessToken");
        return new String[]{data.path("accessToken").asText(), data.path("refreshToken").asText()};
    }

    private long startSession(User actor) throws Exception {
        MvcResult result = mockMvc.perform(as(actor, "USER", post("/api/v1/mock-interviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"KNOWLEDGE\",\"topic\":\"Java Spring Boot\",\"questionCount\":3}")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
        assertTrue(data.path("id").asLong() > 0, "session phải có id");
        return data.path("id").asLong();
    }

    private JobDescription draftJob(String title) {
        return draftJob(title, employer);
    }

    private JobDescription draftJob(String title, User owner) {
        return jobRepository.save(JobDescription.builder()
                .employer(owner)
                .title(title)
                .companyName("ACME")
                .status(JobStatus.DRAFT)
                .build());
    }

    private User save(String email, String fullName, Role role) {
        return userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(PASSWORD))
                .fullName(fullName)
                .role(role)
                .enabled(true)
                .locked(false)
                .build());
    }

    private MockHttpServletRequestBuilder asEmployer(MockHttpServletRequestBuilder call) {
        return as(employer, "INTERVIEWER", call);
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder call) {
        return as(admin, "ADMIN", call);
    }

    private MockHttpServletRequestBuilder as(User actor, String role, MockHttpServletRequestBuilder call) {
        return call.with(user(actor.getEmail()).roles(role)).with(csrf());
    }
}
