package com.aiinterview.platform.controller;

import com.aiinterview.platform.model.entity.Application;
import com.aiinterview.platform.model.entity.InterviewSlot;
import com.aiinterview.platform.model.entity.JobDescription;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.ApplicationStatus;
import com.aiinterview.platform.model.enums.JobStatus;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.ApplicationRepository;
import com.aiinterview.platform.model.repository.InterviewAnswerRepository;
import com.aiinterview.platform.model.repository.InterviewReportRepository;
import com.aiinterview.platform.model.repository.InterviewSessionRepository;
import com.aiinterview.platform.model.repository.InterviewSlotRepository;
import com.aiinterview.platform.model.repository.JobDescriptionRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Spec 012: hợp đồng dữ liệu + quyền cho luồng phỏng vấn có avatar (mode AVATAR).
 * Chạy trên MySQL thật theo pattern 011 — mọi case đi qua endpoint thật, không mock service,
 * vì hai điều cần đo là ràng buộc ghi ngay lúc write và mã trạng thái 403 của phiên người khác.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Spec 012 — phiên AVATAR trên /api/v1/mock-interviews")
class AvatarInterviewApiIntegrationTest {

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
    private InterviewAnswerRepository answerRepository;
    @Autowired
    private InterviewReportRepository reportRepository;
    @Autowired
    private ApplicationRepository applicationRepository;
    @Autowired
    private InterviewSlotRepository slotRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User candidate;
    private User stranger;
    private User employer;
    private User admin;
    private JobDescription job;

    @BeforeEach
    void setUp() {
        reportRepository.deleteAll();
        answerRepository.deleteAll();
        slotRepository.deleteAll();
        applicationRepository.deleteAll();
        sessionRepository.deleteAll();
        jobRepository.deleteAll();
        userRepository.deleteAll();
        jdbcTemplate.update("delete from login_attempts");

        candidate = save("candidate@example.com", "Candidate One", Role.ROLE_USER);
        stranger = save("stranger@example.com", "Stranger Two", Role.ROLE_USER);
        employer = save("employer@example.com", "Employer One", Role.ROLE_INTERVIEWER);
        admin = save("admin@example.com", "Admin User", Role.ROLE_ADMIN);
        job = jobRepository.save(JobDescription.builder()
                .employer(employer).title("Backend Java Developer").companyName("ACME")
                .status(JobStatus.OPEN).build());
    }

    // ==================== 1. start ==========

    @Test
    @DisplayName("01: start AVATAR thiếu jobId thì 400; hợp lệ thì session IN_PROGRESS, questions rỗng")
    void testStartAvatar() throws Exception {
        MvcResult noJob = call(post("/api/v1/mock-interviews", "{\"mode\":\"AVATAR\"}"));
        expect(noJob, 400, "Vui lòng chọn tin tuyển dụng cho phiên phỏng vấn avatar");
        assertEquals(0, count("select count(*) from interview_sessions"), "400 thì không được lưu session");

        MvcResult started = call(post("/api/v1/mock-interviews", startAvatarBody(job.getId())), candidate);
        JsonNode data = read(started).path("data");
        assertEquals(200, started.getResponse().getStatus());
        assertEquals("AVATAR", data.path("mode").asText());
        assertEquals("IN_PROGRESS", data.path("status").asText());
        assertEquals(0, data.path("questions").size(), "BE không sinh câu hỏi trước cho AVATAR");
        assertEquals(0, data.path("currentQuestionIndex").asInt());
        assertEquals(job.getTitle(), data.path("jobTitle").asText());
        assertEquals(0, count("select count(*) from interview_session_questions where session_id = "
                + data.path("id").asLong()), "questions rỗng phải là 0 dòng, không phải [null]");
    }

    // ==================== 2–3. nháp và chốt ==========

    @Test
    @DisplayName("02: ba lần confirm=false cùng lượt chỉ ghi một dòng, currentQuestionIndex vẫn 0")
    void testDraftUpsertKeepsOneRow() throws Exception {
        long id = startAvatar();
        for (int i = 0; i < 3; i++) {
            expect(turn(id, "Câu hỏi " + i, "Bản nháp " + i, false, null), 200, null);
        }
        assertEquals(1, answerCount(id), "nháp phải upsert, không tạo bản sao");
        assertEquals("Bản nháp 2", jdbcTemplate.queryForObject(
                "select answer_text from interview_answers where session_id = ? and question_index = 0",
                String.class, id));
        assertEquals(0, currentQuestionIndex(id));
        assertEquals(0, count("select count(*) from interview_answers where session_id = ? "
                + "and confirmed_at is not null", id), "confirm=false thì chưa chốt");

        JsonNode transcript = read(call(get("/api/v1/mock-interviews/" + id))).path("data").path("transcript");
        assertEquals(1, transcript.size());
        assertEquals(0, transcript.get(0).path("index").asInt());
        assertTrue(transcript.get(0).path("confirmed").isBoolean());
        assertFalse(transcript.get(0).path("confirmed").asBoolean());
        assertEquals("Bản nháp 2", transcript.get(0).path("answerText").asText());
    }

    @Test
    @DisplayName("03: confirm=true sang lượt 1; ghi lại lượt 0 đã chốt thì 400, transcript không đổi")
    void testConfirmAdvancesAndLocksTurn() throws Exception {
        long id = startAvatar();
        expect(turn(id, "Câu 0", "Trả lời 0", true, null), 200, null);
        assertEquals(1, currentQuestionIndex(id));
        assertEquals(1, answerCount(id));

        MvcResult redo = turn(id, "Câu 0 bản khác", "Trả lời 0 khác", true, 0);
        expect(redo, 400, "Lượt này đã được chốt");
        assertEquals(1, currentQuestionIndex(id), "request bị từ chối không được dịch con trỏ");
        assertEquals(1, answerCount(id), "cũng không được thêm dòng");

        // nháp lại lượt đã chốt cũng bị chặn — ứng viên không đổi được lời đã gửi
        expect(turn(id, "Câu 0", "Sửa âm thầm", false, 0), 400, "Lượt này đã được chốt");

        // lượt hiện tại vẫn ghi bình thường
        expect(turn(id, "Câu 1", "Trả lời 1", true, null), 200, null);
        assertEquals(2, currentQuestionIndex(id));
        assertEquals(2, answerCount(id));

        MvcResult future = turn(id, "Câu 9", "Nhảy trước", true, 9);
        expect(future, 400, "lượt chưa tới");
        assertEquals(2, answerCount(id));
    }

    // ==================== 4. biên độ dài ==========

    @Test
    @DisplayName("04: questionText 2001 ký tự và answerText vượt trần đều 400, không ghi dòng nào")
    void testFieldSizeCeilings() throws Exception {
        long id = startAvatar();
        expect(turn(id, "x".repeat(2001), "Trả lời bình thường", true, null), 400, "Dữ liệu không hợp lệ");
        assertEquals(0, answerCount(id));
        assertEquals(0, currentQuestionIndex(id));

        expect(turn(id, "Câu 0", "a".repeat(10001), true, null), 400, "Dữ liệu không hợp lệ");
        assertEquals(0, answerCount(id));

        // đúng trần thì nhận — chứng minh con số 400 ở trên là do trần, không phải do code sai kiểu
        expect(turn(id, "x".repeat(2000), "a".repeat(10000), true, null), 200, null);
        assertEquals(1, answerCount(id));
    }

    // ==================== 5. questionText vào luồng khác ==========

    @Test
    @DisplayName("05: gửi questionText vào phiên MOCK thì 400, đề bài do BE sinh không đổi được")
    void testQuestionTextRejectedForOtherModes() throws Exception {
        long mockId = startSession("{\"mode\":\"MOCK\",\"jobId\":" + job.getId() + ",\"questionCount\":3}");
        MvcResult res = turn(mockId, "Câu hỏi do FE đặt", "Trả lời", true, null);
        expect(res, 400, "Chế độ này không nhận câu hỏi từ client");
        assertEquals(0, answerCount(mockId));

        long knowledgeId = startSession("{\"mode\":\"KNOWLEDGE\",\"topic\":\"Java Spring Boot\",\"questionCount\":3}");
        expect(turn(knowledgeId, "Câu hỏi do FE đặt", "Trả lời", true, null), 400,
                "Chế độ này không nhận câu hỏi từ client");
        // đường cũ của MOCK/KNOWLEDGE vẫn chạy y như trước khi có 012
        expect(turn(knowledgeId, null, "Trả lời thường", null, null), 200, null);
        assertEquals(1, answerCount(knowledgeId));
        assertEquals(1, currentQuestionIndex(knowledgeId));
    }

    // ==================== 6 & 10. sở hữu phiên ==========

    @Test
    @DisplayName("06: GET/answer/evaluation trên phiên của ứng viên khác đều 403, không phải 404")
    void testForeignSessionIsForbidden() throws Exception {
        long id = startAvatar();
        expect(call(get("/api/v1/mock-interviews/" + id), stranger), 403,
                "Bạn không có quyền truy cập phiên phỏng vấn này");
        expect(turn(id, "Câu 0", "Trả lời", true, null, stranger), 403,
                "Bạn không có quyền truy cập phiên phỏng vấn này");
        expect(call(post("/api/v1/mock-interviews/" + id + "/evaluation", evaluationBody(goodRubric())), stranger),
                403, "Bạn không có quyền truy cập phiên phỏng vấn này");
        expect(call(post("/api/v1/mock-interviews/" + id + "/finish", null), stranger), 403,
                "Bạn không có quyền truy cập phiên phỏng vấn này");
        assertEquals(0, answerCount(id), "request bị chặn không được ghi gì");
        assertEquals("IN_PROGRESS", sessionRepository.findById(id).orElseThrow().getStatus().name());
    }

    @Test
    @DisplayName("10: INTERVIEWER/ADMIN và ứng viên khác đều không đọc được report (chỉ chính chủ)")
    void testReportIsOwnerOnly() throws Exception {
        long id = startAvatar();
        expect(turn(id, "Câu 0", "Trả lời 0", true, null), 200, null);
        expect(call(post("/api/v1/mock-interviews/" + id + "/finish", null), candidate), 200, null);
        expect(call(post("/api/v1/mock-interviews/" + id + "/evaluation", evaluationBody(goodRubric())), candidate),
                200, null);

        expect(call(get("/api/v1/mock-interviews/" + id + "/report"), employer), 403, null);
        expect(call(get("/api/v1/mock-interviews/" + id + "/report"), admin), 403, null);
        expect(call(get("/api/v1/mock-interviews/" + id + "/report"), stranger), 403,
                "Bạn không có quyền truy cập phiên phỏng vấn này");
        // chính chủ đọc được
        JsonNode report = read(call(get("/api/v1/mock-interviews/" + id + "/report"), candidate)).path("data");
        assertEquals(74, report.path("overallScore").asInt());
    }

    // ==================== 7–8. evaluation và report ==========

    @Test
    @DisplayName("07: rubric đúng → report có, tổng = trung bình 5 chiều; overallScore=999 trong body bị bỏ qua")
    void testEvaluationComputesTotal() throws Exception {
        long id = startAvatar();
        expect(turn(id, "Câu 0", "Trả lời 0", true, null), 200, null);
        expect(call(post("/api/v1/mock-interviews/" + id + "/finish", null), candidate), 200, null);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rubric", goodRubric());
        body.put("rubricVersion", "2026-10-v1");
        body.put("strengths", "Nói rõ kiến trúc, có ví dụ dự án.");
        body.put("improvements", "Cần chốt phương án cache.");
        body.put("overallScore", 999);
        body.put("audioBase64", "aGVsbG8=");
        JsonNode data = read(call(post("/api/v1/mock-interviews/" + id + "/evaluation",
                objectMapper.writeValueAsString(body)), candidate)).path("data");

        // (80+70+75+60+85)/5 = 74 — client nói 999 cũng không ghi được
        assertEquals(74, data.path("overallScore").asInt());
        assertEquals(80, data.path("chuyenMon").asInt());
        assertEquals(85, data.path("mucKhop").asInt());
        assertEquals("2026-10-v1", data.path("rubricVersion").asText());
        assertEquals(id, data.path("sessionId").asLong());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select overall_score, chuyen_mon, du_an, tu_duy, truyen_dat, muc_khop, rubric_version "
                        + "from interview_report where session_id = ?", id);
        assertEquals(74, row.get("overall_score"));
        assertEquals("2026-10-v1", row.get("rubric_version"));

        // nộp lần hai bị chặn: báo cáo là snapshot một lần
        expect(call(post("/api/v1/mock-interviews/" + id + "/evaluation", evaluationBody(goodRubric())), candidate),
                400, "Báo cáo đã được nộp");
        assertEquals(1, count("select count(*) from interview_report"), "một phiên tối đa một report");
    }

    @Test
    @DisplayName("08: rubric thiếu chiều / 101 / 75.5 / thừa chiều / strengths quá trần / nộp khi chưa finish thì 400, interview_report không có dòng")
    void testEvaluationRejectsBadRubric() throws Exception {
        long id = startAvatar();
        expect(turn(id, "Câu 0", "Trả lời 0", true, null), 200, null);

        // còn IN_PROGRESS
        expect(call(post("/api/v1/mock-interviews/" + id + "/evaluation", evaluationBody(goodRubric())), candidate),
                400, "Phiên chưa kết thúc");
        assertEquals(0, count("select count(*) from interview_report"));

        expect(call(post("/api/v1/mock-interviews/" + id + "/finish", null), candidate), 200, null);

        Map<String, Object> missing = goodRubric();
        missing.remove("duAn");
        expect(call(post("/api/v1/mock-interviews/" + id + "/evaluation", evaluationBody(missing)), candidate),
                400, "thiếu chiều [duAn]");
        assertEquals(0, count("select count(*) from interview_report"));

        Map<String, Object> outOfRange = goodRubric();
        outOfRange.put("duAn", 101);
        expect(call(post("/api/v1/mock-interviews/" + id + "/evaluation", evaluationBody(outOfRange)), candidate),
                400, "không phải số nguyên 0..100 [duAn]");

        Map<String, Object> notInteger = goodRubric();
        notInteger.put("tuDuy", 75.5);
        expect(call(post("/api/v1/mock-interviews/" + id + "/evaluation", evaluationBody(notInteger)), candidate),
                400, "không phải số nguyên 0..100 [tuDuy]");

        Map<String, Object> extra = goodRubric();
        extra.put("ngoaiHinh", 90);
        expect(call(post("/api/v1/mock-interviews/" + id + "/evaluation", evaluationBody(extra)), candidate),
                400, "chiều không có trong thang chấm [ngoaiHinh]");

        Map<String, Object> negative = goodRubric();
        negative.put("truyenDat", -1);
        expect(call(post("/api/v1/mock-interviews/" + id + "/evaluation", evaluationBody(negative)), candidate),
                400, "không phải số nguyên 0..100 [truyenDat]");

        assertEquals(0, count("select count(*) from interview_report"),
                "mọi rubric sai phải kết thúc ở 0 dòng, không phải nửa rubric được lưu");

        // rubricVersion bắt buộc
        expect(call(post("/api/v1/mock-interviews/" + id + "/evaluation",
                evaluationBody(goodRubric(), "  ")), candidate), 400, null);
        assertEquals(0, count("select count(*) from interview_report"));

        // report chưa có thì 404 đúng thông điệp
        expect(call(get("/api/v1/mock-interviews/" + id + "/report"), candidate), 404,
                "Chưa có báo cáo cho phiên phỏng vấn này");

        // trần của hai ô TEXT: không chặn ở DTO thì chết ở DB và trả 500
        Map<String, Object> tooLong = new LinkedHashMap<>();
        tooLong.put("rubric", goodRubric());
        tooLong.put("rubricVersion", "2026-10-v1");
        tooLong.put("strengths", "s".repeat(4001));
        expect(call(post("/api/v1/mock-interviews/" + id + "/evaluation",
                objectMapper.writeValueAsString(tooLong)), candidate), 400, "Dữ liệu không hợp lệ");
        assertEquals(0, count("select count(*) from interview_report"));
        tooLong.put("strengths", "s".repeat(4000));
        expect(call(post("/api/v1/mock-interviews/" + id + "/evaluation",
                objectMapper.writeValueAsString(tooLong)), candidate), 200, null);
        assertEquals(1, count("select count(*) from interview_report"));
    }

    // ==================== 9. trần 40 lượt ==========

    @Test
    @DisplayName("09: lượt thứ 41 bị 400 'Phiên đã đạt số lượt tối đa', interview_answers vẫn 40 dòng")
    void testTurnCapAtForty() throws Exception {
        long id = startAvatar();
        for (int i = 0; i < 40; i++) {
            MvcResult res = turn(id, "Câu " + i, "Trả lời " + i, true, null);
            assertEquals(200, res.getResponse().getStatus(),
                    "lượt " + i + " phải chốt được, actual=" + text(res));
        }
        assertEquals(40, currentQuestionIndex(id));
        assertEquals(40, answerCount(id));

        expect(turn(id, "Câu 40", "Trả lời 40", true, null), 400, "Phiên đã đạt số lượt tối đa, hãy kết thúc");
        // kể cả nháp cũng không ghi nổi: trần chặn mọi write, không chỉ confirm
        expect(turn(id, "Câu 40", "Trả lời 40", false, null), 400, "Phiên đã đạt số lượt tối đa, hãy kết thúc");
        assertEquals(40, answerCount(id));
        assertEquals(40, currentQuestionIndex(id));

        // giữa chừng vẫn finish được, không buộc phải trả lời hết
        JsonNode finished = read(call(post("/api/v1/mock-interviews/" + id + "/finish", null), candidate))
                .path("data");
        assertEquals("COMPLETED", finished.path("session").path("status").asText());
        assertEquals(40, finished.path("transcript").size());
    }

    // ==================== 11. không đụng luồng có chủ ==========

    @Test
    @DisplayName("11: cả luồng avatar không sửa một dòng applications hay interview_slots nào")
    void testAvatarFlowLeavesApplicationsAndSlotsUntouched() throws Exception {
        Application application = applicationRepository.save(Application.builder()
                .candidate(candidate).job(job).status(ApplicationStatus.PENDING).build());
        InterviewSlot slot = slotRepository.save(InterviewSlot.builder()
                .employer(employer).bookedBy(candidate)
                .startTime(LocalDateTime.now().plusDays(2))
                .endTime(LocalDateTime.now().plusDays(2).plusHours(1))
                .cancelled(false).build());
        // snapshot nguyên dòng, kể cả updated_at: "không đổi bản ghi" phải đo từng cột
        Map<String, Object> appRow = row("applications", application.getId());
        Map<String, Object> slotRow = row("interview_slots", slot.getId());

        long id = startAvatar();
        expect(turn(id, "Câu 0", "Trả lời 0", true, null), 200, null);
        expect(turn(id, "Câu 1", "Bản nháp 1", false, null), 200, null);
        expect(call(post("/api/v1/mock-interviews/" + id + "/finish", null), candidate), 200, null);
        expect(call(post("/api/v1/mock-interviews/" + id + "/evaluation", evaluationBody(goodRubric())), candidate),
                200, null);
        expect(call(get("/api/v1/mock-interviews/" + id + "/report"), candidate), 200, null);

        // điểm avatar là decision-support: không tự đẩy ApplicationStatus, không đụng slot đã hẹn
        assertEquals(appRow, row("applications", application.getId()),
                "applications phải y nguyên sau toàn bộ luồng avatar");
        assertEquals(slotRow, row("interview_slots", slot.getId()),
                "interview_slots phải y nguyên sau toàn bộ luồng avatar");
        // overallScore của session để trung: điểm thật nằm ở interview_report, không phải con số 0 giả
        assertNull(sessionRepository.findById(id).orElseThrow().getOverallScore());
        assertEquals(74, reportRepository.findBySession(sessionRepository.findById(id).orElseThrow())
                .orElseThrow().getOverallScore());
    }

    // ==================== 12. xóa phiên có report ==========

    @Test
    @DisplayName("12: DELETE phiên avatar xóa cả report lẫn transcript, không để FK chặn (interview_report là bảng mới)")
    void testDeleteSessionRemovesReportAndAnswers() throws Exception {
        long id = startAvatar();
        expect(turn(id, "Câu 0", "Trả lời 0", true, null), 200, null);
        expect(turn(id, "Câu 1", "Bản nháp 1", false, null), 200, null);
        expect(call(post("/api/v1/mock-interviews/" + id + "/finish", null), candidate), 200, null);
        expect(call(post("/api/v1/mock-interviews/" + id + "/evaluation", evaluationBody(goodRubric())), candidate),
                200, null);
        assertEquals(1, count("select count(*) from interview_report"));

        expect(call(delete("/api/v1/mock-interviews/" + id), candidate), 200, "Đã xóa phiên luyện tập");

        assertTrue(sessionRepository.findById(id).isEmpty());
        assertEquals(0, answerCount(id));
        assertEquals(0, count("select count(*) from interview_report"),
                "còn dòng report là FK chặn xóa, endpoint trả 500");
        expect(call(get("/api/v1/mock-interviews/" + id), candidate), 404, "Không tìm thấy phiên phỏng vấn");
    }

    // ==================== helpers ==========

    private String startAvatarBody(long jobId) {
        return "{\"mode\":\"AVATAR\",\"jobId\":" + jobId + "}";
    }

    private long startAvatar() throws Exception {
        return startSession(startAvatarBody(job.getId()));
    }

    private long startSession(String body) throws Exception {
        MvcResult result = call(post("/api/v1/mock-interviews", body), candidate);
        assertEquals(200, result.getResponse().getStatus(), text(result));
        return read(result).path("data").path("id").asLong();
    }

    /** Một lượt của phiên AVATAR; questionIndex == null nghĩa là FE không chỉ định lượt tường minh. */
    private MvcResult turn(long sessionId, String questionText, String answerText,
                           Boolean confirm, Integer questionIndex) throws Exception {
        return turn(sessionId, questionText, answerText, confirm, questionIndex, candidate);
    }

    private MvcResult turn(long sessionId, String questionText, String answerText, Boolean confirm,
                           Integer questionIndex, User actor) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        if (questionText != null) {
            body.put("questionText", questionText);
        }
        body.put("answerText", answerText);
        if (confirm != null) {
            body.put("confirm", confirm);
        }
        if (questionIndex != null) {
            body.put("questionIndex", questionIndex);
        }
        body.put("durationSec", 42);
        return call(post("/api/v1/mock-interviews/" + sessionId + "/answer",
                objectMapper.writeValueAsString(body)), actor);
    }

    private Map<String, Object> goodRubric() {
        Map<String, Object> rubric = new LinkedHashMap<>();
        rubric.put("chuyenMon", 80);
        rubric.put("duAn", 70);
        rubric.put("tuDuy", 75);
        rubric.put("truyenDat", 60);
        rubric.put("mucKhop", 85);
        return rubric;
    }

    private String evaluationBody(Map<String, Object> rubric) throws Exception {
        return evaluationBody(rubric, "2026-10-v1");
    }

    private String evaluationBody(Map<String, Object> rubric, String rubricVersion) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rubric", rubric);
        body.put("rubricVersion", rubricVersion);
        return objectMapper.writeValueAsString(body);
    }

    private MvcResult call(MockHttpServletRequestBuilder builder) throws Exception {
        return call(builder, candidate);
    }

    private MvcResult call(MockHttpServletRequestBuilder builder, User actor) throws Exception {
        return mockMvc.perform(as(actor, builder)).andReturn();
    }

    private MockHttpServletRequestBuilder post(String path, String body) {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(path)
                .contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            builder.content(body);
        }
        return builder;
    }

    private MockHttpServletRequestBuilder get(String path) {
        return MockMvcRequestBuilders.get(path);
    }

    private MockHttpServletRequestBuilder as(User actor, MockHttpServletRequestBuilder call) {
        return call.with(user(actor.getEmail()).roles(actor.getRole().name().replace("ROLE_", ""))).with(csrf());
    }

    private void expect(MvcResult result, int status, String messagePart) {
        assertEquals(status, result.getResponse().getStatus(), "response body=" + text(result));
        if (messagePart != null) {
            String message = read(result).path("message").asText();
            assertTrue(message.contains(messagePart),
                    "mong message chứa '" + messagePart + "', actual='" + message + "'");
        }
    }

    private JsonNode read(MvcResult result) {
        try {
            return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Không đọc được JSON response: " + text(result), e);
        }
    }

    private String text(MvcResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "<không đọc được body>";
        }
    }

    /** Nguyên một dòng để so "không đổi bản ghi" ở cấp cột, không chỉ cấp trạng thái. */
    private Map<String, Object> row(String table, long id) {
        return jdbcTemplate.queryForMap("select * from " + table + " where id = ?", id);
    }

    private int answerCount(long sessionId) {
        return count("select count(*) from interview_answers where session_id = " + sessionId);
    }

    private int currentQuestionIndex(long sessionId) {
        return jdbcTemplate.queryForObject(
                "select current_question_index from interview_sessions where id = ?", Integer.class, sessionId);
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private User save(String email, String fullName, Role role) {
        return userRepository.save(User.builder().email(email).password(passwordEncoder.encode(PASSWORD))
                .fullName(fullName).role(role).enabled(true).locked(false).build());
    }
}
