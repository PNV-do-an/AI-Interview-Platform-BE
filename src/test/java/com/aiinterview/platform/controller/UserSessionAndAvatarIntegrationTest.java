package com.aiinterview.platform.controller;

import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.AdminAuditLogRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.security.jwt.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec 011: phiên đăng nhập (user_session + claim sid) và ảnh đại diện, chạy trên MySQL thật.
 * Mọi case đi qua endpoint thật, không mock service — thu hồi phiên phải cắt được
 * access token còn hạn (24h), đó là toàn bộ lý do của wave này.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Spec 011 — phiên đăng nhập & ảnh đại diện")
class UserSessionAndAvatarIntegrationTest {

    /** PNG 1x1 thật; chữ ký tệp bắt đầu bằng 89 50 4E 47 nên qua được kiểm tra magic bytes. */
    private static final String PNG_1PX =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
    /** GIF thật nhưng dán nhãn png — phải bị từ chối vì chữ ký tệp không khớp nhãn. */
    private static final String GIF_LABELED_PNG =
            "data:image/png;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw==";

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
    private AdminAuditLogRepository auditLogRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User member;
    private User admin;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
        jdbcTemplate.update("delete from user_session");
        jdbcTemplate.update("delete from login_attempts");

        member = userRepository.save(User.builder()
                .email("member@example.com").password(passwordEncoder.encode("password123"))
                .fullName("Member").role(Role.ROLE_USER).enabled(true).build());
        admin = userRepository.save(User.builder()
                .email("admin@example.com").password(passwordEncoder.encode("password123"))
                .fullName("Admin").role(Role.ROLE_ADMIN).enabled(true).build());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** Đăng nhập thật, trả nguyên khối data (accessToken + refreshToken). */
    private JsonNode loginData(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return read(result).get("data");
    }

    /** Đăng nhập thật, trả về access token (token này mang claim sid). */
    private String login(String email) throws Exception {
        return loginData(email).get("accessToken").asText();
    }

    private long liveSessions(Long userId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from user_session where user_id = ? and revoked_at is null",
                Long.class, userId);
        return count == null ? 0L : count;
    }

    private MvcResult sessions(String accessToken) throws Exception {
        return mockMvc.perform(get("/api/user/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andReturn();
    }

    /** id của một phiên KHÔNG phải phiên đang gọi; thứ tự danh sách sắp theo lastSeenAt nên không đoán được. */
    private long otherSessionId(String accessToken) throws Exception {
        for (JsonNode row : read(sessions(accessToken)).get("data")) {
            if (!row.get("current").asBoolean()) {
                return row.get("id").asLong();
            }
        }
        throw new IllegalStateException("Test này cần tài khoản có ít nhất hai phiên");
    }

    // ── phiên ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Đăng nhập ghi một dòng user_session và token mang sid của dòng đó")
    void loginCreatesSessionAndPutsSidInToken() throws Exception {
        String accessToken = login(member.getEmail());

        assertNotNull(JwtUtil.validateAccessToken(accessToken).get("sid", String.class),
                "access token phải mang claim sid");
        assertEquals(1L, liveSessions(member.getId()));

        MvcResult list = sessions(accessToken);
        assertEquals(200, list.getResponse().getStatus());
        JsonNode data = read(list).get("data");
        assertEquals(1, data.size());
        assertTrue(data.get(0).get("current").asBoolean(), "đúng phiên đang gọi phải được đánh dấu current");
        assertNotNull(data.get(0).get("deviceLabel").asText());
    }

    @Test
    @DisplayName("Logout giết cả access token còn hạn lẫn refresh token của phiên đó")
    void logoutKillsTheLiveAccessToken() throws Exception {
        JsonNode tokens = loginData(member.getEmail());
        String accessToken = tokens.get("accessToken").asText();
        String refreshToken = tokens.get("refreshToken").asText();
        assertEquals(200, sessions(accessToken).getResponse().getStatus());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken).with(csrf()))
                .andExpect(status().isOk());

        assertEquals(0L, liveSessions(member.getId()));
        assertTrue(sessions(accessToken).getResponse().getStatus() >= 401,
                "token của phiên đã thu hồi không còn quyền gọi API");

        // Refresh mà còn cấp được token thì thiết bị đó xin lại access token mang sid hỏng tới 7 ngày.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Đổi mật khẩu cắt mọi phiên đang hoạt động trên mọi thiết bị")
    void changePasswordRevokesEverySession() throws Exception {
        String firstDevice = login(member.getEmail());
        String secondDevice = login(member.getEmail());
        assertEquals(2L, liveSessions(member.getId()));

        mockMvc.perform(post("/api/user/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstDevice).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password123\",\"newPassword\":\"NewPass1!x\"}"))
                .andExpect(status().isOk());

        assertEquals(0L, liveSessions(member.getId()));
        assertTrue(sessions(secondDevice).getResponse().getStatus() >= 401);
    }

    @Test
    @DisplayName("Admin khóa tài khoản: phiên của người đó mất quyền trong cùng request cycle")
    void adminLockRevokesTargetSessions() throws Exception {
        String victimToken = login(member.getEmail());
        String adminToken = JwtUtil.generateAccessToken(
                admin.getEmail(), admin.getRole().name(), admin.getTokenVersion(), null);

        mockMvc.perform(patch("/api/v1/admin/users/" + member.getId() + "/lock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Điều tra truy cập bất thường\",\"notifyUser\":false}"))
                .andExpect(status().isOk());

        assertEquals(0L, liveSessions(member.getId()));
        assertTrue(sessions(victimToken).getResponse().getStatus() >= 401);
    }

    @Test
    @DisplayName("Thu hồi một thiết bị cụ thể, các thiết bị khác vẫn sống")
    void revokeSingleDeviceKeepsOthers() throws Exception {
        String keep = login(member.getEmail());
        String drop = login(member.getEmail());

        long dropId = otherSessionId(keep);

        mockMvc.perform(delete("/api/user/sessions/" + dropId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + keep))
                .andExpect(status().isOk());

        assertEquals(200, sessions(keep).getResponse().getStatus());
        assertTrue(sessions(drop).getResponse().getStatus() >= 401);
    }

    @Test
    @DisplayName("Không thu hồi được phiên của tài khoản khác")
    void cannotRevokeAnotherUsersSession() throws Exception {
        String otherToken = login(member.getEmail());
        long otherSessionId = Long.parseLong(objectMapper
                .readTree(sessions(otherToken).getResponse().getContentAsString())
                .get("data").get(0).get("id").asText());
        String adminToken = login(admin.getEmail());

        mockMvc.perform(delete("/api/user/sessions/" + otherSessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound());

        assertEquals(200, sessions(otherToken).getResponse().getStatus());
    }

    // ── ảnh đại diện ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT avatar lưu data URL, GET hồ sơ đọc lại đúng ảnh")
    void avatarRoundTrip() throws Exception {
        String token = login(member.getEmail());

        mockMvc.perform(put("/api/user/avatar")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataUrl\":\"" + PNG_1PX + "\"}"))
                .andExpect(status().isOk());

        assertEquals(PNG_1PX, userRepository.findById(member.getId()).orElseThrow().getAvatar());

        String adminToken = JwtUtil.generateAccessToken(
                admin.getEmail(), admin.getRole().name(), admin.getTokenVersion(), null);
        mockMvc.perform(get("/api/v1/admin/users/" + member.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatar").value(PNG_1PX));

        mockMvc.perform(delete("/api/user/avatar")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).with(csrf()))
                .andExpect(status().isOk());

        assertNull(userRepository.findById(member.getId()).orElseThrow().getAvatar());
    }

    @Test
    @DisplayName("Ảnh sai quy cách bị chặn ngay lúc ghi, không lưu chuỗi hỏng")
    void invalidAvatarIsRejectedAtWriteTime() throws Exception {
        String token = login(member.getEmail());

        // Nhãn png nhưng chữ ký tệp là GIF.
        mockMvc.perform(put("/api/user/avatar")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataUrl\":\"" + GIF_LABELED_PNG + "\"}"))
                .andExpect(status().isBadRequest());

        // Ngoài danh sách MIME cho phép.
        mockMvc.perform(put("/api/user/avatar")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataUrl\":\"data:image/svg+xml;base64,AAAA\"}"))
                .andExpect(status().isBadRequest());

        // Chuỗi rỗng.
        mockMvc.perform(put("/api/user/avatar")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataUrl\":\"\"}"))
                .andExpect(status().isBadRequest());

        // Vượt ngưỡng 2 MB.
        mockMvc.perform(put("/api/user/avatar")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataUrl\":\"data:image/png;base64," + "A".repeat(2_900_000) + "\"}"))
                .andExpect(status().isBadRequest());

        assertNull(userRepository.findById(member.getId()).orElseThrow().getAvatar(),
                "request bị từ chối không được để lại ảnh");
    }
}
