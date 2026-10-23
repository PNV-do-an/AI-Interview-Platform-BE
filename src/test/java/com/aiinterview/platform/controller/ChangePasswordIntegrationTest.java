package com.aiinterview.platform.controller;

import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.AdminAuditLogRepository;
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

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Change Password Integration Tests (spec 008 AC-5)")
class ChangePasswordIntegrationTest {

    private static final String EMAIL = "c1@example.com";
    private static final String OLD_PASSWORD = "password123";
    private static final String NEW_PASSWORD = "NewPass1!x";

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
    private AdminAuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
        jdbcTemplate.update("delete from login_attempts");

        // Tài khoản đã xác thực email (verificationToken null, enabled) thì mới login được.
        userRepository.save(User.builder()
                .email(EMAIL)
                .password(passwordEncoder.encode(OLD_PASSWORD))
                .fullName("Candidate One")
                .role(Role.ROLE_USER)
                .enabled(true)
                .locked(false)
                .build());
    }

    // ==================== AC-5: luồng thật — phiên cũ chết ngay ==========================

    @Test
    @DisplayName("AC-5: sau khi đổi mật khẩu, mật khẩu cũ ⇒ 401, mật khẩu mới ⇒ 200, refresh token cũ ⇒ 401")
    void testChangePassword_RevokeEverySession() throws Exception {
        String[] tokens = login(EMAIL, OLD_PASSWORD);

        mockMvc.perform(post("/api/user/change-password").with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens[0])
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + OLD_PASSWORD + "\",\"newPassword\":\""
                                + NEW_PASSWORD + "\"}"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("Đổi mật khẩu thành công")));

        assertEquals(1, userRepository.findByEmail(EMAIL).orElseThrow().getTokenVersion());

        mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + OLD_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Mật khẩu sai"));

        String[] fresh = login(EMAIL, NEW_PASSWORD);

        mockMvc.perform(post("/api/v1/auth/refresh").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokens[1] + "\"}"))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(containsString("Phiên đăng nhập")));

        // Access token cũ chết ngay chứ không sống tới hết TTL 24h: JwtFilter so claim "tv"
        // với users.token_version vừa tăng khi đổi mật khẩu.
        mockMvc.perform(get("/api/v1/auth/me").with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens[0]))
                .andDo(print())
                .andExpect(status().isUnauthorized());

        // Control: token vừa cấp sau lần đăng nhập lại vẫn đi qua filter.
        mockMvc.perform(get("/api/v1/auth/me").with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fresh[0]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(EMAIL));

        assertEquals(0, auditLogRepository.count(), "đổi mật khẩu tự thân không ghi audit admin");
    }

    // ==================== AC-5: bốn rule, message chỉ đúng rule bị vi phạm ===============

    @Test
    @WithMockUser(username = EMAIL)
    @DisplayName("AC-5: sai mật khẩu hiện tại ⇒ 400; từng rule mật khẩu mới có message riêng")
    void testChangePassword_RulesNameTheViolatedOne() throws Exception {
        mockMvc.perform(changePassword("wrong-current", "NewPass1!x"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mật khẩu hiện tại không đúng"));

        mockMvc.perform(changePassword(OLD_PASSWORD, "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mật khẩu mới phải có tối thiểu 8 ký tự"));

        mockMvc.perform(changePassword(OLD_PASSWORD, "abcdefgh"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("chữ hoa và một chữ thường")));

        mockMvc.perform(changePassword(OLD_PASSWORD, "Abcdefgh"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("một chữ số")));

        mockMvc.perform(changePassword(OLD_PASSWORD, "Abcdefg1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("ký tự đặc biệt")));

        mockMvc.perform(changePassword("", ""))
                .andExpect(status().isBadRequest());

        // Toàn bộ request trên bị chặn lúc ghi ⇒ không có gì thay đổi.
        assertEquals(0, userRepository.findByEmail(EMAIL).orElseThrow().getTokenVersion());
        assertTrue(passwordEncoder.matches(OLD_PASSWORD,
                userRepository.findByEmail(EMAIL).orElseThrow().getPassword()));

        mockMvc.perform(changePassword(OLD_PASSWORD, NEW_PASSWORD))
                .andExpect(status().isOk());
        assertEquals(1, userRepository.findByEmail(EMAIL).orElseThrow().getTokenVersion());
    }

    @Test
    @DisplayName("AC-6: anonymous ⇒ 401 trên endpoint đổi mật khẩu")
    void testChangePassword_AnonymousRejected() throws Exception {
        mockMvc.perform(changePassword(OLD_PASSWORD, NEW_PASSWORD))
                .andDo(print())
                .andExpect(status().isUnauthorized());

        // Body không hợp lệ cũng bị chặn trước khi ghi: vẫn không có thay đổi nào.
        assertEquals(0, userRepository.findByEmail(EMAIL).orElseThrow().getTokenVersion());
    }

    // ==================== helpers ====================

    private String[] login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
        assertFalse(data.path("accessToken").asText().isBlank(), "login phải trả accessToken");
        return new String[]{data.path("accessToken").asText(), data.path("refreshToken").asText()};
    }

    private MockHttpServletRequestBuilder changePassword(String current, String next) {
        return post("/api/user/change-password").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}");
    }
}
