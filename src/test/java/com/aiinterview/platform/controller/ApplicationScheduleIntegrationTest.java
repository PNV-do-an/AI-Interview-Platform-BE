package com.aiinterview.platform.controller;

import com.aiinterview.platform.model.entity.InterviewSlot;
import com.aiinterview.platform.model.entity.JobDescription;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.ApplicationStatus;
import com.aiinterview.platform.model.enums.JobStatus;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.ApplicationRepository;
import com.aiinterview.platform.model.repository.ApplicationStatusHistoryRepository;
import com.aiinterview.platform.model.repository.InterviewSlotRepository;
import com.aiinterview.platform.model.repository.JobDescriptionRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lô 4 — FR-SCH-02: ứng viên/employer hủy hoặc đổi riêng buổi đã đặt mà không phải hủy cả đơn.
 * Trước khi có hai endpoint này, mọi đường hủy/sửa slot đều bị SlotServiceImpl chặn khi slot đã
 * có người đặt, nên lịch đã chốt không cách nào gỡ.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Application schedule: unschedule, reschedule")
class ApplicationScheduleIntegrationTest {

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
    private InterviewSlotRepository slotRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper json = JsonMapper.builder().build();

    private User employer;
    private User otherEmployer;
    private User candidate;
    private User stranger;
    private User admin;
    private JobDescription job;

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
        applicationRepository.deleteAll();
        slotRepository.deleteAll();
        jobRepository.deleteAll();
        userRepository.deleteAll();

        employer = save("employer@example.com", "Employer One", Role.ROLE_INTERVIEWER);
        otherEmployer = save("other-employer@example.com", "Employer Two", Role.ROLE_INTERVIEWER);
        candidate = save("candidate@example.com", "Candidate One", Role.ROLE_USER);
        stranger = save("stranger@example.com", "Stranger", Role.ROLE_USER);
        admin = save("admin@example.com", "Admin User", Role.ROLE_ADMIN);
        job = jobRepository.save(JobDescription.builder()
                .employer(employer)
                .title("Backend Java Developer")
                .companyName("ACME")
                .status(JobStatus.OPEN)
                .build());
    }

    // ==================== PATCH /applications/{id}/unschedule ==========

    @Test
    @DisplayName("SCH-1: ứng viên hủy lịch, đơn về PENDING, khung giờ mở lại cho người khác chọn")
    void testUnschedule_ByCandidate_ReleasesSlotAndKeepsApplication() throws Exception {
        long applicationId = apply();
        long slotId = book(applicationId, slotId(futureHour(48)));

        mockMvc.perform(as(candidate, "USER", patch("/api/v1/applications/" + applicationId + "/unschedule")))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.slot").value(nullValue()));

        assertEquals(ApplicationStatus.PENDING,
                applicationRepository.findById(applicationId).orElseThrow().getStatus());
        Map<String, Object> released = jdbcTemplate.queryForMap(
                "select booked_by, booked_at, cancelled from interview_slots where id = ?", slotId);
        // Buổi bị gỡ chứ không phải slot bị đóng: cancelled vẫn 0 để khung giờ còn dùng lại được.
        assertNull(released.get("booked_by"));
        assertNull(released.get("booked_at"));
        assertEquals(false, released.get("cancelled"));

        mockMvc.perform(as(candidate, "USER", get("/api/v1/slots/available").param("employerId",
                        String.valueOf(employer.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + slotId + ")]").isNotEmpty());

        // Timeline phải ghi đúng vòng lùi về, actor là ứng viên.
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select from_status, to_status, actor_user_id from application_status_history "
                        + "where application_id = ? order by acted_at asc, id asc", applicationId);
        Map<String, Object> last = rows.get(rows.size() - 1);
        assertEquals("SCHEDULED", last.get("from_status"));
        assertEquals("PENDING", last.get("to_status"));
        assertEquals(candidate.getId(), last.get("actor_user_id"));
    }

    @Test
    @DisplayName("SCH-2: employer cũng gỡ được buổi của chính mình — đường trước đây SlotService chặn cứng")
    void testUnschedule_ByInterviewerAllowed() throws Exception {
        long applicationId = apply();
        long slotId = book(applicationId, slotId(futureHour(72)));

        mockMvc.perform(asEmployer(patch("/api/v1/applications/" + applicationId + "/unschedule")))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        assertNull(jdbcTemplate.queryForObject(
                "select booked_by from interview_slots where id = ?", Object.class, slotId));
        assertNull(applicationRepository.findById(applicationId).orElseThrow().getSlot());
    }

    // ==================== PATCH /applications/{id}/reschedule ==========

    @Test
    @DisplayName("SCH-3: đổi sang khung khác — slot cũ trống lại, slot mới mang tên ứng viên của đơn, không sinh dòng timeline")
    void testReschedule_MovesBookingWithoutAddingTimelineRow() throws Exception {
        long applicationId = apply();
        long oldSlotId = book(applicationId, slotId(futureHour(48)));
        long newSlotId = slotId(futureHour(96));
        int historyBefore = historyCount(applicationId);

        mockMvc.perform(as(candidate, "USER", patch("/api/v1/applications/" + applicationId + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":" + newSlotId + "}")))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.slot.id").value((int) newSlotId))
                .andExpect(jsonPath("$.data.slot.booked").value(true))
                .andExpect(jsonPath("$.data.slot.bookedByName").value("Candidate One"));

        Map<String, Object> oldSlot = jdbcTemplate.queryForMap(
                "select booked_by, booked_at from interview_slots where id = ?", oldSlotId);
        assertNull(oldSlot.get("booked_by"));
        assertNull(oldSlot.get("booked_at"));
        Map<String, Object> newSlot = jdbcTemplate.queryForMap(
                "select booked_by, booked_at from interview_slots where id = ?", newSlotId);
        assertEquals(candidate.getId(), newSlot.get("booked_by"));
        // Đổi giờ không phải đổi vòng: vòng vẫn SCHEDULED nên lịch sử không được nhân bản.
        assertEquals(historyBefore, historyCount(applicationId));
    }

    @Test
    @DisplayName("SCH-4: mọi slot vi phạm (trùng / đã có người / của employer khác / thiếu slotId) bị chặn lúc ghi, đơn giữ nguyên lịch cũ")
    void testReschedule_RejectionsLeaveBookingUntouched() throws Exception {
        long applicationId = apply();
        long bookedSlotId = book(applicationId, slotId(futureHour(48)));
        long takenByOther = slotId(futureHour(120));
        slotRepository.findById(takenByOther).ifPresent(slot -> {
            slot.setBookedBy(stranger);
            slot.setBookedAt(LocalDateTime.now().minusDays(1));
            slotRepository.save(slot);
        });
        long foreignEmployerSlot = slotId(futureHour(120), otherEmployer);

        mockMvc.perform(as(candidate, "USER", patch("/api/v1/applications/" + applicationId + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"slotId\":" + bookedSlotId + "}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("trùng")));
        mockMvc.perform(as(candidate, "USER", patch("/api/v1/applications/" + applicationId + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"slotId\":" + takenByOther + "}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("đã có người đặt")));
        mockMvc.perform(as(candidate, "USER", patch("/api/v1/applications/" + applicationId + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":" + foreignEmployerSlot + "}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("không thuộc")));
        mockMvc.perform(as(candidate, "USER", patch("/api/v1/applications/" + applicationId + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")))
                .andExpect(status().isBadRequest());

        // Không request nào được để lại trạng thái nửa vời: lịch cũ còn nguyên, slot lạ còn trống.
        assertEquals(bookedSlotId, applicationRepository.findById(applicationId).orElseThrow().getSlot().getId());
        assertNull(jdbcTemplate.queryForObject(
                "select booked_by from interview_slots where id = ?", Object.class, foreignEmployerSlot));
    }

    @Test
    @DisplayName("SCH-5: buổi đã diễn ra không hủy và không đổi được")
    void testPastAppointment_CannotUnscheduleOrReschedule() throws Exception {
        long applicationId = apply();
        long slotId = book(applicationId, slotId(futureHour(48)));
        // book-slot chặn giờ quá khứ, nên buổi "đã diễn ra" phải đạt được bằng cách trôi thời gian.
        slotRepository.findById(slotId).ifPresent(slot -> {
            slot.setStartTime(LocalDateTime.now().minusHours(3));
            slot.setEndTime(LocalDateTime.now().minusHours(2));
            slotRepository.save(slot);
        });
        long target = slotId(futureHour(24));

        mockMvc.perform(as(candidate, "USER", patch("/api/v1/applications/" + applicationId + "/unschedule")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("đã diễn ra")));
        mockMvc.perform(as(candidate, "USER", patch("/api/v1/applications/" + applicationId + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"slotId\":" + target + "}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("đã diễn ra")));

        // Lịch sử không được sửa nửa vời: slot cũ vẫn mang tên ứng viên đã đặt.
        assertEquals(candidate.getId(), jdbcTemplate.queryForObject(
                "select booked_by from interview_slots where id = ?", Long.class, slotId));
        assertEquals(slotId, applicationRepository.findById(applicationId).orElseThrow().getSlot().getId());
    }

    // ==================== quyền truy cập ==========

    @Test
    @DisplayName("SCH-7: hủy cả đơn cũng phải thả khung giờ, không để lại buổi ma trong lịch employer")
    void testCancelApplication_ReleasesBookedSlot() throws Exception {
        long applicationId = apply();
        long slotId = book(applicationId, slotId(futureHour(48)));

        mockMvc.perform(as(candidate, "USER", patch("/api/v1/applications/" + applicationId + "/cancel")))
                .andDo(print())
                .andExpect(status().isOk());

        assertNull(jdbcTemplate.queryForObject(
                "select booked_by from interview_slots where id = ?", Object.class, slotId));
        assertNull(applicationRepository.findById(applicationId).orElseThrow().getSlot());
        // Khung giờ còn sống và trống: ứng viên khác đặt được.
        mockMvc.perform(as(stranger, "USER", get("/api/v1/slots/available").param("employerId",
                        String.valueOf(employer.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + slotId + ")]").isNotEmpty());
    }

    @Test
    @DisplayName("SCH-6: đơn chưa có lịch bị 400, ứng viên khác không thao tác được, anonymous 401")
    void testAccessRules() throws Exception {
        // Một ứng viên chỉ nộp được một đơn cho một tin, nên hai đơn phải thuộc hai người khác nhau.
        long withoutBooking = applyAs(stranger);

        mockMvc.perform(as(stranger, "USER", patch("/api/v1/applications/" + withoutBooking + "/unschedule")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("chưa có lịch")));
        mockMvc.perform(as(stranger, "USER", patch("/api/v1/applications/" + withoutBooking + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"slotId\":" + slotId(futureHour(24)) + "}")))
                .andExpect(status().isBadRequest());

        long applicationId = apply();
        book(applicationId, slotId(futureHour(48)));

        mockMvc.perform(as(stranger, "USER", patch("/api/v1/applications/" + applicationId + "/unschedule")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(stranger, "USER", patch("/api/v1/applications/" + applicationId + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"slotId\":" + slotId(futureHour(60)) + "}")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/applications/" + applicationId + "/unschedule"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/v1/applications/" + applicationId + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"slotId\":1}"))
                .andExpect(status().isUnauthorized());

        // Admin được can thiệp để gỡ bế tắc lịch, theo đúng hai role admin/staff đã dùng ở evaluate().
        mockMvc.perform(asAdmin(patch("/api/v1/applications/" + applicationId + "/unschedule")))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
        assertTrue(applicationRepository.findById(applicationId).orElseThrow().getSlot() == null);
    }

    // ==================== helpers ==========

    private long apply() throws Exception {
        return applyAs(candidate);
    }

    private long applyAs(User actor) throws Exception {
        MvcResult result = mockMvc.perform(as(actor, "USER",
                        post("/api/v1/applications/jobs/" + job.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"coverLetter\":\"Em ứng tuyển vị trí backend.\"}")))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private LocalDateTime futureHour(int hours) {
        return LocalDateTime.now().plusHours(hours).withNano(0);
    }

    private long slotId(LocalDateTime start) {
        return slotId(start, employer);
    }

    private long slotId(LocalDateTime start, User owner) {
        return slotRepository.save(InterviewSlot.builder()
                .employer(owner)
                .startTime(start)
                .endTime(start.plusHours(1))
                .build()).getId();
    }

    /** Đặt lịch qua chính endpoint book-slot để đơn đi đúng vòng PENDING -> SCHEDULED. */
    private long book(long applicationId, long slotId) throws Exception {
        mockMvc.perform(as(candidate, "USER", post("/api/v1/applications/book-slot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":" + slotId + ",\"applicationId\":" + applicationId + "}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.slot.id").value((int) slotId));
        return slotId;
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
        return as(employer, "INTERVIEWER", call);
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder call) {
        return as(admin, "ADMIN", call);
    }

    private MockHttpServletRequestBuilder as(User actor, String role, MockHttpServletRequestBuilder call) {
        return call.with(user(actor.getEmail()).roles(role)).with(csrf());
    }
}
