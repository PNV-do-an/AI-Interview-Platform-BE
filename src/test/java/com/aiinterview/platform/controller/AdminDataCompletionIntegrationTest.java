package com.aiinterview.platform.controller;

import com.aiinterview.platform.model.entity.Application;
import com.aiinterview.platform.model.entity.CandidateProfile;
import com.aiinterview.platform.model.entity.InterviewSession;
import com.aiinterview.platform.model.entity.InterviewSlot;
import com.aiinterview.platform.model.entity.JobDescription;
import com.aiinterview.platform.model.entity.LoginAttempt;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.ApplicationStatus;
import com.aiinterview.platform.model.enums.InterviewMode;
import com.aiinterview.platform.model.enums.InterviewSessionStatus;
import com.aiinterview.platform.model.enums.JobStatus;
import com.aiinterview.platform.model.enums.ModerationAction;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.enums.WorkplaceModel;
import com.aiinterview.platform.model.repository.AdminAuditLogRepository;
import com.aiinterview.platform.model.repository.ApplicationRepository;
import com.aiinterview.platform.model.repository.CandidateProfileRepository;
import com.aiinterview.platform.model.repository.InterviewSessionRepository;
import com.aiinterview.platform.model.repository.InterviewSlotRepository;
import com.aiinterview.platform.model.repository.JobDescriptionRepository;
import com.aiinterview.platform.model.repository.JobModerationRecordRepository;
import com.aiinterview.platform.model.repository.LoginAttemptRepository;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec 009 — các khối admin mà FE gắn nhãn "Khối demo" sau wave 2026-10-21, giờ có số thật.
 * Mỗi test đối chiếu một con số với câu SQL/JPA đếm trên cùng DB, không tin con số tự tính.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Admin data completion (spec 009)")
class AdminDataCompletionIntegrationTest {

    private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyy-MM");
    /** > 200 ký tự để qua kiểm tra "mô tả đủ chi tiết", không chứa số/ký tự liên lạc. */
    private static final String LONG_DESCRIPTION =
            "Team chúng tôi cần một kỹ sư backend phụ trách thiết kế và vận hành dịch vụ xử lý hồ sơ "
                    + "ứng viên, viết API cho giao diện quản trị, bảo đảm hiệu năng và khả năng quan sát "
                    + "của hệ thống trong môi trường sản xuất nhiều tenant, đồng thời phối hợp chặt chẽ "
                    + "với nhóm frontend để hoàn thiện luồng duyệt tin tuyển dụng.";

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
    private JobModerationRecordRepository moderationRepository;
    @Autowired
    private AdminAuditLogRepository auditLogRepository;
    @Autowired
    private LoginAttemptRepository loginAttemptRepository;
    @Autowired
    private InterviewSessionRepository sessionRepository;
    @Autowired
    private InterviewSlotRepository slotRepository;
    @Autowired
    private ApplicationRepository applicationRepository;
    @Autowired
    private CandidateProfileRepository profileRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User admin;
    private User employer;
    private User staff;
    private User candidate;

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();
        profileRepository.deleteAll();
        moderationRepository.deleteAll();
        jobRepository.deleteAll();
        sessionRepository.deleteAll();
        slotRepository.deleteAll();
        auditLogRepository.deleteAll();
        loginAttemptRepository.deleteAll();
        userRepository.deleteAll();

        admin = save("admin@example.com", "Admin User", Role.ROLE_ADMIN);
        employer = save("employer@example.com", "Employer One", Role.ROLE_INTERVIEWER);
        staff = save("staff@example.com", "Staff Member", Role.ROLE_STAFF);
        candidate = save("candidate@example.com", "Candidate Two", Role.ROLE_USER);
    }

    // ==================== F1: dashboard chuỗi thời gian + thời lượng + phiên live ==========

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("AC-1: biểu đồ 12 tháng — mỗi mốc khớp câu đếm của đúng tháng, ngoài 12 tháng bị cắt")
    void testTrends_TwelveMonths_MatchHandCounts() throws Exception {
        YearMonth current = YearMonth.now();
        YearMonth lastMonth = current.minusMonths(1);
        YearMonth tooOld = current.minusMonths(13);

        persistSession(current, 80);
        persistSession(current, 90);
        persistSession(lastMonth, 70);
        persistSession(tooOld, 60);
        // Chỉ slot đã đặt, không hủy mới tính là phỏng vấn chính thức.
        persistSlot(current.atDay(5).atStartOfDay(), employer, candidate, false);
        persistSlot(current.atDay(6).atStartOfDay(), employer, candidate, false);
        persistSlot(current.atDay(7).atStartOfDay(), employer, null, true);
        persistSlot(current.atDay(8).atStartOfDay(), employer, null, false);
        persistSlot(lastMonth.atDay(3).atStartOfDay(), employer, candidate, false);

        mockMvc.perform(asAdmin(get("/api/v1/admin/dashboard")))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trends.months.length()").value(12))
                .andExpect(jsonPath("$.data.trends.months[11].month")
                        .value(current.format(MONTH_KEY)))
                .andExpect(jsonPath("$.data.trends.months[0].month")
                        .value(current.minusMonths(11).format(MONTH_KEY)))
                .andExpect(jsonPath("$.data.trends.months[11].mock").value(2))
                .andExpect(jsonPath("$.data.trends.months[11].formal").value(2))
                .andExpect(jsonPath("$.data.trends.months[10].month").value(lastMonth.format(MONTH_KEY)))
                .andExpect(jsonPath("$.data.trends.months[10].mock").value(1))
                .andExpect(jsonPath("$.data.trends.months[10].formal").value(1))
                // Tháng trống vẫn phải có mặt để FE vẽ liên tục.
                .andExpect(jsonPath("$.data.trends.months[3].mock").value(0))
                .andExpect(jsonPath("$.data.trends.months[3].formal").value(0));

        // SQL đối chiếu: đúng 2 phiên tháng này, và phiên 13 tháng trước KHÔNG được rơi vào bucket nào.
        Integer sessionsThisMonth = jdbcTemplate.queryForObject(
                "select count(*) from interview_sessions "
                        + "where date_format(created_at, '%Y-%m') = ?",
                Integer.class, current.format(MONTH_KEY));
        assertEquals(2, sessionsThisMonth);
        Integer booked = jdbcTemplate.queryForObject(
                "select count(*) from interview_slots "
                        + "where booked_by is not null and cancelled = false "
                        + "and date_format(start_time, '%Y-%m') = ?",
                Integer.class, current.format(MONTH_KEY));
        assertEquals(2, booked);
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from interview_sessions where date_format(created_at, '%Y-%m') = ?",
                Integer.class, tooOld.format(MONTH_KEY)));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("AC-2: thời lượng trung bình + số phiên đang chạy, khớp TIMESTAMPDIFF và đếm status")
    void testDurationAndLiveSessions() throws Exception {
        InterviewSession first = persistSession(YearMonth.now(), 80);
        InterviewSession second = persistSession(YearMonth.now(), 70);
        InterviewSession running = persistSession(YearMonth.now(), null);
        // 600s + 1200s ⇒ trung bình 900s; phiên chưa completedAt không được tính.
        stamp(first, LocalDateTime.now().minusHours(3), LocalDateTime.now().minusHours(3).plusSeconds(600));
        stamp(second, LocalDateTime.now().minusHours(2), LocalDateTime.now().minusHours(2).plusSeconds(1200));
        running.setStatus(InterviewSessionStatus.IN_PROGRESS);
        running.setCompletedAt(null);
        sessionRepository.save(running);

        mockMvc.perform(asAdmin(get("/api/v1/admin/dashboard")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interviews.avgDurationSeconds").value(900))
                .andExpect(jsonPath("$.data.interviews.liveNow").value(1))
                // 005 giữ nguyên hợp đồng: 2 session có điểm ⇒ trung bình điểm vẫn tính.
                .andExpect(jsonPath("$.data.interviews.total").value(3))
                .andExpect(jsonPath("$.data.interviews.avgOverallScore").isNumber());

        Long handAvg = ((Number) jdbcTemplate.queryForObject(
                "select avg(timestampdiff(second, created_at, completed_at)) from interview_sessions "
                        + "where completed_at is not null", Object.class)).longValue();
        assertEquals(900L, handAvg);
    }

    @Test
    @DisplayName("AC-8: dashboard + categories + activity vẫn ADMIN-only, STAFF bị 403, anonymous 401")
    void testAdminOnlyEndpoints_StaffForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard").with(csrf()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/categories").with(csrf()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/users/" + candidate.getId() + "/activity").with(csrf()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/dashboard")
                        .with(user(staff.getEmail()).roles("STAFF")).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/categories")
                        .with(user(staff.getEmail()).roles("STAFF")).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/users/" + candidate.getId() + "/activity")
                        .with(user(staff.getEmail()).roles("STAFF")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ==================== F2: chi tiết user + dòng thời gian hoạt động ==========

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("AC-3: IP + lần đăng nhập cuối + điểm sẵn sàng AI + hồ sơ ứng viên của ĐÚNG user")
    void testUserDetail_LastLoginReadinessProfile() throws Exception {
        String email = candidate.getEmail();
        loginAttemptRepository.save(new LoginAttempt(email, "10.0.0.1", true));
        backdate(email, "10.0.0.1", LocalDateTime.now().minusHours(3));
        loginAttemptRepository.save(new LoginAttempt(email, "10.0.0.9", true));
        backdate(email, "10.0.0.9", LocalDateTime.now().minusHours(2));
        // Dòng THẤT BẠI mới nhất (t=-1h) không được thắng: màn hình cần lần đăng nhập thành công cuối.
        loginAttemptRepository.save(new LoginAttempt(email, "10.0.0.2", false));
        backdate(email, "10.0.0.2", LocalDateTime.now().minusHours(1));
        // Đăng nhập của người khác không được lẫn vào hồ sơ của candidate.
        loginAttemptRepository.save(new LoginAttempt(employer.getEmail(), "10.0.0.5", true));

        persistSession(YearMonth.now(), 80);   // của candidate (see persistSession)
        persistSession(YearMonth.now(), 90);
        InterviewSession otherScore = sessionOf(employer, YearMonth.now(), 10);
        otherScore.setCompletedAt(LocalDateTime.now());
        sessionRepository.save(otherScore);

        profileRepository.save(CandidateProfile.builder()
                .user(candidate)
                .skills("Java, Spring Boot,, React ")
                .experienceYears(4)
                .summary("Backend engineer")
                .careerGoal("Dẫn dắt team platform")
                .cvFileName("cv-huy.pdf")
                .build());

        mockMvc.perform(asAdmin(get("/api/v1/admin/users/" + candidate.getId())))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lastIp").value("10.0.0.9"))
                .andExpect(jsonPath("$.data.aiReadinessScore").value(85.0))
                .andExpect(jsonPath("$.data.activity.applications").value(0))
                .andExpect(jsonPath("$.data.activity.mockSessions").value(2))
                .andExpect(jsonPath("$.data.profile.skills.length()").value(3))
                .andExpect(jsonPath("$.data.profile.skills[0]").value("Java"))
                .andExpect(jsonPath("$.data.profile.skills[2]").value("React"))
                .andExpect(jsonPath("$.data.profile.experienceYears").value(4))
                .andExpect(jsonPath("$.data.profile.cvFileName").value("cv-huy.pdf"));

        // User chưa từng đăng nhập / chưa có hồ sơ ⇒ null, không phải giá trị giả.
        mockMvc.perform(asAdmin(get("/api/v1/admin/users/" + admin.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lastIp").value(nullValue()))
                .andExpect(jsonPath("$.data.lastLoginAt").value(nullValue()))
                .andExpect(jsonPath("$.data.aiReadinessScore").value(nullValue()))
                .andExpect(jsonPath("$.data.profile").value(nullValue()));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("AC-4: tab Hoạt động gộp ba nguồn, sắp mới nhất trước, phân trang không đổi totalElements")
    void testUserActivity_MergedAndPaged() throws Exception {
        JobDescription job = persistJob("Backend Java", JobStatus.OPEN, employer);
        // Mốc relative để thứ tự không phụ thuộc hôm nay là ngày nào: slot (+3d) > phiên (-1d) > đơn (-4d).
        InterviewSession session = persistSession(YearMonth.now(), 80);
        stamp(session, LocalDateTime.now().minusDays(1), LocalDateTime.now().minusDays(1).plusMinutes(30));
        persistSlot(LocalDateTime.now().plusDays(3), employer, candidate, false);
        Application older = applicationRepository.save(Application.builder()
                .candidate(candidate).job(job).status(ApplicationStatus.PENDING).build());
        jdbcTemplate.update("update applications set created_at = ? where id = ?",
                LocalDateTime.now().minusDays(4), older.getId());

        mockMvc.perform(asAdmin(get("/api/v1/admin/users/" + candidate.getId() + "/activity")))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content.length()").value(3))
                .andExpect(jsonPath("$.data.content[0].type").value("INTERVIEW_SLOT"))
                .andExpect(jsonPath("$.data.content[1].type").value("MOCK_SESSION"))
                .andExpect(jsonPath("$.data.content[2].type").value("APPLICATION"))
                .andExpect(jsonPath("$.data.content[2].title").value("Backend Java"))
                .andExpect(jsonPath("$.data.content[2].status").value("PENDING"));

        // Cắt trang: 2 dòng đầu, totalElements giữ nguyên 3.
        mockMvc.perform(asAdmin(get("/api/v1/admin/users/" + candidate.getId() + "/activity")
                        .param("page", "0").param("size", "2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2));

        mockMvc.perform(asAdmin(get("/api/v1/admin/users/" + candidate.getId() + "/activity")
                        .param("page", "1").param("size", "2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(3));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("AC-4/FR-009: input guard — size > 100 ⇒ 400, vượt cửa sổ 1000 dòng ⇒ 400, id lạ ⇒ 404")
    void testUserActivity_InputGuards() throws Exception {
        mockMvc.perform(asAdmin(get("/api/v1/admin/users/" + candidate.getId() + "/activity")
                        .param("size", "101")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(asAdmin(get("/api/v1/admin/users/" + candidate.getId() + "/activity")
                        .param("page", "60").param("size", "20")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("1000")));

        mockMvc.perform(asAdmin(get("/api/v1/admin/users/999999/activity")))
                .andExpect(status().isNotFound());

        // User bị xóa mềm vẫn đọc được (đúng quy tắc 002: tab Deleted mở hồ sơ).
        mockMvc.perform(asAdmin(get("/api/v1/admin/users/" + candidate.getId() + "/activity")))
                .andExpect(status().isOk());
    }

    // ==================== F3: thống kê hàng chờ + hồ sơ NTD + sàng lọc chính sách ==========

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("AC-5: 4 chip thống kê khớp countLiveByStatus và số job từng bị từ chối")
    void testModerationQueueStats() throws Exception {
        JobDescription go = persistJob("Backend Go", JobStatus.DRAFT, employer);
        persistJob("Draft One", JobStatus.DRAFT, employer);
        persistJob("Open One", JobStatus.OPEN, employer);
        JobDescription sentBack = persistJob("Closed One", JobStatus.PENDING_APPROVAL, employer);
        submit(go);
        decide(go, true, null);
        decide(sentBack, false, "Thiếu mô tả");

        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation/stats")))
                .andDo(print())
                .andExpect(status().isOk())
                // pending = 0 (tin vừa được duyệt), draft = 2 (bản nháp + tin bị trả về nháp),
                // approved = 2 (Open One + Backend Go), rejected = 1, total = 4 job live.
                .andExpect(jsonPath("$.data.pending").value(0))
                .andExpect(jsonPath("$.data.draft").value(2))
                .andExpect(jsonPath("$.data.approved").value(2))
                .andExpect(jsonPath("$.data.rejected").value(1))
                .andExpect(jsonPath("$.data.total").value(4));

        assertEquals(4L, jobRepository.countByEmployerIdAndDeletedAtIsNull(employer.getId()));
        assertEquals(1L, moderationRepository.countDistinctJobsByAction(ModerationAction.REJECTED));
        assertEquals(0L, jobRepository.countByDeletedAtIsNullAndStatus(JobStatus.PENDING_APPROVAL));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("AC-6: điểm tín nhiệm = 100 × approved/(approved+rejected); chưa quyết định nào ⇒ null")
    void testEmployerSnapshot_TrustScore() throws Exception {
        JobDescription a = persistJob("Java One", JobStatus.PENDING_APPROVAL, employer);
        JobDescription b = persistJob("Java Two", JobStatus.PENDING_APPROVAL, employer);
        JobDescription c = persistJob("Java Three", JobStatus.PENDING_APPROVAL, employer);
        decide(a, true, null);
        decide(b, true, null);
        decide(c, false, "Trùng nội dung");

        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation/" + a.getId())))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employer.id").value(employer.getId()))
                .andExpect(jsonPath("$.data.employer.companyName").value("ACME"))
                .andExpect(jsonPath("$.data.employer.email").value("employer@example.com"))
                .andExpect(jsonPath("$.data.employer.jobs.total").value(3))
                .andExpect(jsonPath("$.data.employer.jobs.approved").value(2))
                .andExpect(jsonPath("$.data.employer.jobs.rejected").value(1))
                .andExpect(jsonPath("$.data.employer.jobs.pending").value(0))
                .andExpect(jsonPath("$.data.employer.trustScore").value(67));

        // Nhà tuyển dụng chưa từng bị duyệt/từ chối: không có chứng cứ ⇒ null, không mặc định 100.
        User freshEmployer = save("fresh@example.com", "Fresh Company", Role.ROLE_INTERVIEWER);
        JobDescription untouched = persistJob("Fresh Job", JobStatus.PENDING_APPROVAL, freshEmployer);
        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation/" + untouched.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employer.trustScore").value(nullValue()))
                .andExpect(jsonPath("$.data.employer.jobs.total").value(1))
                .andExpect(jsonPath("$.data.employer.jobs.approved").value(0));
    }

    @Test
    @DisplayName("AC-7: sàng lọc rule ghi ai_safety_score lúc nộp và khớp hệt khi đọc; 4 thẻ luôn đủ")
    void testPolicyScreener_ScoreStoredAndMatchesDetail() throws Exception {
        JobDescription clean = persistJob("Backend Java Developer", JobStatus.DRAFT, employer);
        clean.setDescription(LONG_DESCRIPTION);
        clean.setRequirements("Thành thạo một ngôn ngữ backend.");
        clean.setRequiredSkills("Java, Spring Boot");
        jobRepository.save(clean);

        mockMvc.perform(asEmployer(patch("/api/v1/jobs/" + clean.getId() + "/submit")))
                .andExpect(status().isOk());
        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation/" + clean.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiSafety.checks.length()").value(4))
                .andExpect(jsonPath("$.data.aiSafety.score").value(100))
                .andExpect(jsonPath("$.data.aiSafety.checks[0].passed").value(true))
                .andExpect(jsonPath("$.data.aiSafety.checks[0].label").isString())
                // Cột lưu và điểm tính lại phải là MỘT con số.
                .andExpect(jsonPath("$.data.aiSafetyScore").value(100));
        assertEquals(100, jobRepository.findById(clean.getId()).orElseThrow().getAiSafetyScore());

        JobDescription bad = persistJob("Bán hàng thị trường", JobStatus.DRAFT, employer);
        bad.setDescription("Vui lòng liên hệ hung.nguyen@acme.com để biết thêm.");
        jobRepository.save(bad);
        mockMvc.perform(asEmployer(patch("/api/v1/jobs/" + bad.getId() + "/submit")))
                .andExpect(status().isOk());

        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation/" + bad.getId())))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiSafety.score").value(0))
                .andExpect(jsonPath("$.data.aiSafetyScore").value(0))
                .andExpect(jsonPath("$.data.aiSafety.checks[0].key").value("IT_RELEVANT_TITLE"))
                .andExpect(jsonPath("$.data.aiSafety.checks[0].passed").value(false))
                .andExpect(jsonPath("$.data.aiSafety.checks[0].note")
                        .value(org.hamcrest.Matchers.containsString("từ khóa")))
                .andExpect(jsonPath("$.data.aiSafety.checks[3].key").value("NO_CONTACT_LEAKS"))
                .andExpect(jsonPath("$.data.aiSafety.checks[3].note")
                        .value(org.hamcrest.Matchers.containsString("email")));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("AC-7: tin nộp TRƯỚC khi có 009 (cột trống) vẫn đủ 4 thẻ, không 500")
    void testPolicyScreener_LegacyJobWithoutStoredScore() throws Exception {
        JobDescription legacy = persistJob("Data Engineer", JobStatus.PENDING_APPROVAL, employer);

        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation/" + legacy.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiSafetyScore").value(nullValue()))
                .andExpect(jsonPath("$.data.aiSafety.checks.length()").value(4));
    }

    @Test
    @DisplayName("AC-7: sửa nội dung tin từng bị từ chối ⇒ cột điểm lưu viết lại, không giữ điểm bản cũ")
    void testPolicyScreener_RescoreAfterEdit() throws Exception {
        JobDescription stale = persistJob("Bán hàng thị trường", JobStatus.DRAFT, employer);
        stale.setDescription("Vui lòng liên hệ hung.nguyen@acme.com để biết thêm.");
        jobRepository.save(stale);
        submit(stale);
        decide(stale, false, "Lộ thông tin liên lạc");
        assertEquals(0, jobRepository.findById(stale.getId()).orElseThrow().getAiSafetyScore());

        mockMvc.perform(asEmployer(put("/api/v1/jobs/" + stale.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Backend Java Developer\",\"companyName\":\"ACME\","
                                + "\"description\":\"" + LONG_DESCRIPTION + "\","
                                + "\"requirements\":\"Thành thạo Spring Boot, tối thiểu 2 năm với MySQL.\","
                                + "\"requiredSkills\":\"Java, Spring Boot, MySQL\"}")))
                .andExpect(status().isOk());

        assertEquals(100, jobRepository.findById(stale.getId()).orElseThrow().getAiSafetyScore());
        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation/" + stale.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiSafety.score").value(100))
                .andExpect(jsonPath("$.data.aiSafetyScore").value(100));
    }

    // ==================== F4: danh mục ==========

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("AC-9: /admin/categories khớp GROUP BY trên job chưa xóa, bỏ giá trị trống")
    void testCategories_MatchGroupBy() throws Exception {
        jobWithTaxonomy("Backend Java", "Engineering", "Mid", "FULL_TIME", WorkplaceModel.HYBRID);
        jobWithTaxonomy("Backend Go", "Engineering", "Senior", "FULL_TIME", WorkplaceModel.REMOTE);
        jobWithTaxonomy("Sales Lead", "Sales", null, "", null);
        jobWithTaxonomy("Blank Dept", "   ", null, null, null);
        JobDescription deleted = jobWithTaxonomy("Sales Old", "Sales", null, null, null);
        deleted.setDeletedAt(LocalDateTime.now());
        jobRepository.save(deleted);

        mockMvc.perform(asAdmin(get("/api/v1/admin/categories")))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groups.length()").value(4))
                .andExpect(jsonPath("$.data.groups[0].key").value("department"))
                // Engineering 2 job ⇒ dẫn đầu, Sales 1 (job đã xóa mềm không được đếm).
                .andExpect(jsonPath("$.data.groups[0].values[0].value").value("Engineering"))
                .andExpect(jsonPath("$.data.groups[0].values[0].jobCount").value(2))
                .andExpect(jsonPath("$.data.groups[0].values[1].value").value("Sales"))
                .andExpect(jsonPath("$.data.groups[0].values[1].jobCount").value(1))
                .andExpect(jsonPath("$.data.groups[0].values.length()").value(2))
                .andExpect(jsonPath("$.data.groups[1].key").value("experienceLevel"))
                .andExpect(jsonPath("$.data.groups[1].values.length()").value(2))
                .andExpect(jsonPath("$.data.groups[2].key").value("employmentType"))
                .andExpect(jsonPath("$.data.groups[2].values[0].value").value("FULL_TIME"))
                .andExpect(jsonPath("$.data.groups[2].values.length()").value(1))
                .andExpect(jsonPath("$.data.groups[3].key").value("workplaceModel"))
                .andExpect(jsonPath("$.data.groups[3].values[0].value").value("HYBRID"));

        long engineering = jdbcTemplate.queryForObject(
                "select count(*) from job_descriptions where department = 'Engineering' "
                        + "and deleted_at is null", Long.class);
        assertEquals(2L, engineering);
        assertTrue(jobRepository.countGroupedByDepartment().size() == 2,
                "chuỗi khoảng trắng phải bị loại khỏi nhóm");
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("AC-10/FR-009: DB trống ⇒ cấu trúc đầy đủ với 0/null, không 500; số 005 không đổi hợp đồng")
    void testEmptyDatabase_ReturnsZerosNotNulls() throws Exception {
        mockMvc.perform(asAdmin(get("/api/v1/admin/dashboard")))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trends.months.length()").value(12))
                .andExpect(jsonPath("$.data.trends.months[11].mock").value(0))
                .andExpect(jsonPath("$.data.trends.months[11].formal").value(0))
                .andExpect(jsonPath("$.data.interviews.avgDurationSeconds").value(nullValue()))
                .andExpect(jsonPath("$.data.interviews.liveNow").value(0))
                // 005 vẫn còn đó (AC-10).
                .andExpect(jsonPath("$.data.users.total").value(4))
                .andExpect(jsonPath("$.data.users.byRole.ROLE_ADMIN").value(1))
                .andExpect(jsonPath("$.data.jobs.byStatus.OPEN").value(0));

        mockMvc.perform(asAdmin(get("/api/v1/admin/jobs/moderation/stats")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pending").value(0))
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(asAdmin(get("/api/v1/admin/categories")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groups.length()").value(4))
                .andExpect(jsonPath("$.data.groups[0].values.length()").value(0));

        mockMvc.perform(asAdmin(get("/api/v1/admin/users/" + candidate.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activity.mockSessions").value(0))
                .andExpect(jsonPath("$.data.aiReadinessScore").value(nullValue()));

        mockMvc.perform(asAdmin(get("/api/v1/admin/users/" + candidate.getId() + "/activity")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("AC-3: chỉ số sẵn sàng AI null khi mọi session của user chưa có điểm")
    void testReadiness_NullWhenNoScoredSession() throws Exception {
        persistSession(YearMonth.now(), null);
        mockMvc.perform(asAdmin(get("/api/v1/admin/users/" + candidate.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiReadinessScore").value(nullValue()))
                .andExpect(jsonPath("$.data.activity.mockSessions").value(1));
    }

    // ==================== helpers ====================

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

    private JobDescription persistJob(String title, JobStatus status, User owner) {
        return jobRepository.save(JobDescription.builder()
                .employer(owner)
                .title(title)
                .companyName("ACME")
                .position("Dev")
                .status(status)
                .build());
    }

    private JobDescription jobWithTaxonomy(String title, String department, String experienceLevel,
                                           String employmentType, WorkplaceModel model) {
        return jobRepository.save(JobDescription.builder()
                .employer(employer)
                .title(title)
                .companyName("ACME")
                .status(JobStatus.OPEN)
                .department(department)
                .experienceLevel(experienceLevel)
                .employmentType(employmentType)
                .workplaceModel(model)
                .build());
    }

    /** Session của candidate, tạo trong tháng cho trước; overallScore null ⇒ chưa chấm. */
    private InterviewSession persistSession(YearMonth month, Integer overallScore) {
        return sessionOf(candidate, month, overallScore);
    }

    private InterviewSession sessionOf(User owner, YearMonth month, Integer overallScore) {
        InterviewSession session = sessionRepository.save(InterviewSession.builder()
                .candidate(owner)
                .mode(InterviewMode.KNOWLEDGE)
                .topic("Java Spring Boot")
                .status(InterviewSessionStatus.COMPLETED)
                .overallScore(overallScore)
                .build());
        if (overallScore != null) {
            session.setCompletedAt(month.atDay(10).atStartOfDay());
        }
        jdbcTemplate.update("update interview_sessions set created_at = ? where id = ?",
                month.atDay(4).atStartOfDay(), session.getId());
        return sessionRepository.save(session);
    }

    private void stamp(InterviewSession session, LocalDateTime created, LocalDateTime completed) {
        jdbcTemplate.update("update interview_sessions set created_at = ?, completed_at = ? where id = ?",
                created, completed, session.getId());
    }

    private InterviewSlot persistSlot(LocalDateTime startTime, User owner, User bookedBy, boolean cancelled) {
        return slotRepository.save(InterviewSlot.builder()
                .employer(owner)
                .bookedBy(bookedBy)
                .startTime(startTime)
                .endTime(startTime.plusHours(1))
                .cancelled(cancelled)
                .build());
    }

    private void submit(JobDescription job) throws Exception {
        mockMvc.perform(asEmployer(patch("/api/v1/jobs/" + job.getId() + "/submit")))
                .andExpect(status().isOk());
    }

    /** PATCH review cần tin đang PENDING_APPROVAL; gọi submit() trước nếu còn là bản nháp. */
    private void decide(JobDescription job, boolean approved, String reason) throws Exception {
        String body = approved
                ? "{\"approved\":true}"
                : "{\"approved\":false,\"reason\":\"" + reason + "\"}";
        mockMvc.perform(asAdmin(patch("/api/v1/jobs/" + job.getId() + "/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)))
                .andExpect(status().isOk());
    }

    /** Không có getter id ⇒ đánh dấu mốc thời gian bằng IP duy nhất của dòng vừa chèn. */
    private void backdate(String email, String ip, LocalDateTime when) {
        jdbcTemplate.update("update login_attempts set created_at = ? where email = ? and ip_address = ?",
                when, email, ip);
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder call) {
        return call.with(user("admin@example.com").roles("ADMIN")).with(csrf());
    }

    private MockHttpServletRequestBuilder asEmployer(MockHttpServletRequestBuilder call) {
        return call.with(user(employer.getEmail()).roles("INTERVIEWER")).with(csrf());
    }
}
