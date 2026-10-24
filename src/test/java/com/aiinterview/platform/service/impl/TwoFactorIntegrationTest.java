package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.RecoveryCodeRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.security.jwt.JwtUtil;
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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full-loop 2FA on a real MySQL: setup → enable → login challenge → verify-otp → recovery → disable. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("2FA API integration tests")
class TwoFactorIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
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
    private RecoveryCodeRepository recoveryCodeRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TotpVerifier totpVerifier;

    private User member;
    private User admin;

    @BeforeEach
    void setUp() {
        recoveryCodeRepository.deleteAll();
        userRepository.deleteAll();
        jdbcTemplate.update("delete from login_attempts");
        jdbcTemplate.update("delete from admin_audit_log");

        member = userRepository.save(User.builder()
                .email("member@example.com").password(passwordEncoder.encode("password123"))
                .fullName("Member").role(Role.ROLE_USER).enabled(true).build());
        admin = userRepository.save(User.builder()
                .email("admin@example.com").password(passwordEncoder.encode("password123"))
                .fullName("Admin").role(Role.ROLE_ADMIN).enabled(true).build());
    }

    private String bearerFor(User user) {
        return "Bearer " + JwtUtil.generateAccessToken(
                user.getEmail(), user.getRole().name(), user.getTokenVersion(), null);
    }

    /** setup + enable qua API thật, trả về secret đã bật thành công. */
    private String enable2fa(User user) throws Exception {
        MvcResult setup = mockMvc.perform(post("/api/v1/auth/2fa/setup")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(user)).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        String secret = JsonNode.class.cast(read(setup).get("data")).get("secret").asText();
        mockMvc.perform(post("/api/v1/auth/2fa/enable")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(user)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + totpVerifier.currentCode(secret) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recoveryCodes.length()").value(8));
        return secret;
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return read(result).get("data").toString();
    }

    @Test
    void anonymousCannotTouchSelfServiceEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/auth/2fa"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithout2faStillReturnsTokensDirectly() throws Exception {
        String data = login("member@example.com", "password123");
        assertTrue(data.contains("accessToken"));
        assertFalse(data.contains("\"mfaRequired\":true"));
    }

    @Test
    void fullLoopSetupEnableLoginChallengeVerify() throws Exception {
        enable2fa(member);

        // Bước một: mật khẩu đúng nhưng chỉ nhận về challenge, KHÔNG có JWT.
        MvcResult first = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"member@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.mfaRequired").value(true))
                .andReturn();
        String challenge = read(first).get("data").get("challengeToken").asText();
        assertNotNull(challenge);

        // Challenge token không được hoạt động như access token.
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + challenge))
                .andExpect(status().isUnauthorized());

        // Bước hai: OTP đúng → JWT thật, dùng được ngay.
        MvcResult second = mockMvc.perform(post("/api/v1/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeToken\":\"" + challenge + "\",\"code\":\""
                                + totpVerifier.currentCode(reloadSecret()) + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = read(second).get("data").get("accessToken").asText();
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("member@example.com"))
                .andExpect(jsonPath("$.data.twoFactorEnabled").value(true));
    }

    @Test
    void wrongOtpIsRejectedAndChallengeStaysSinglePurpose() throws Exception {
        enable2fa(member);
        MvcResult first = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"member@example.com\",\"password\":\"password123\"}"))
                .andReturn();
        String challenge = read(first).get("data").get("challengeToken").asText();

        mockMvc.perform(post("/api/v1/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeToken\":\"" + challenge + "\",\"code\":\"000000\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recoveryCodeWorksOnceThenOtpRequiredAgain() throws Exception {
        MvcResult setup = mockMvc.perform(post("/api/v1/auth/2fa/setup")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(member)).with(csrf()))
                .andReturn();
        String secret = read(setup).get("data").get("secret").asText();
        MvcResult enabled = mockMvc.perform(post("/api/v1/auth/2fa/enable")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(member)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + totpVerifier.currentCode(secret) + "\"}"))
                .andReturn();
        String recovery = read(enabled).get("data").get("recoveryCodes").get(0).asText();

        MvcResult firstLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"member@example.com\",\"password\":\"password123\"}"))
                .andReturn();
        String challenge = read(firstLogin).get("data").get("challengeToken").asText();

        mockMvc.perform(post("/api/v1/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeToken\":\"" + challenge + "\",\"code\":\"" + recovery + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists());

        // Dùng lại đúng mã đó: recovery code một lần, phải bị từ chối.
        MvcResult secondLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"member@example.com\",\"password\":\"password123\"}"))
                .andReturn();
        String challenge2 = read(secondLogin).get("data").get("challengeToken").asText();
        mockMvc.perform(post("/api/v1/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeToken\":\"" + challenge2 + "\",\"code\":\"" + recovery + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void memberCannotForceDisableSomeoneElses2fa() throws Exception {
        enable2fa(member);
        mockMvc.perform(patch("/api/v1/admin/users/" + member.getId() + "/2fa")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"test\"}"))
                .andExpect(status().isForbidden());
        assertTrue(member.isTotpEnabled() || reloadMember().isTotpEnabled());
    }

    @Test
    void adminForceDisableClearsEverythingAndAudits() throws Exception {
        enable2fa(member);

        mockMvc.perform(patch("/api/v1/admin/users/" + member.getId() + "/2fa")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"mat thiet bi xac thuc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.twoFactorEnabled").value(false));

        assertFalse(reloadMember().isTotpEnabled());
        assertNull(reloadMember().getTotpSecret());
        assertEquals(0, recoveryCodeRepository.findByUserIdAndUsedAtIsNull(member.getId()).size());

        // Sau khi bị force-disable, login thường trả JWT thẳng — user mất thiết bị đã thoát được.
        assertTrue(login("member@example.com", "password123").contains("accessToken"));
    }

    @Test
    void forceDisableWithoutReasonIsRejected() throws Exception {
        enable2fa(member);
        mockMvc.perform(patch("/api/v1/admin/users/" + member.getId() + "/2fa")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private User reloadMember() {
        return userRepository.findById(member.getId()).orElseThrow();
    }

    private String reloadSecret() {
        return userRepository.findById(member.getId()).orElseThrow().getTotpSecret();
    }
}
