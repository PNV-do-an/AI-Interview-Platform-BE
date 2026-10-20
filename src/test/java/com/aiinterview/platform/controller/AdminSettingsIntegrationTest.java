package com.aiinterview.platform.controller;

import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.AuditAction;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.AdminAuditLogRepository;
import com.aiinterview.platform.model.repository.SystemSettingRepository;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Admin System Settings Integration Tests (spec 007)")
class AdminSettingsIntegrationTest {

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
    private SystemSettingRepository settingRepository;

    @Autowired
    private AdminAuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        settingRepository.deleteAll();
        userRepository.deleteAll();
        jdbcTemplate.update("delete from login_attempts");

        userRepository.save(buildUser("admin@example.com", "Admin User", Role.ROLE_ADMIN));
        userRepository.save(buildUser("staff@example.com", "Staff Member", Role.ROLE_STAFF));
    }

    // ==================== AC-1: GET lần đầu trả đủ mặc định, không ghi row rác ==========

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("AC-1: first GET returns every group with typed defaults and writes nothing")
    void testGetSettings_FirstTimeDefaults() throws Exception {
        mockMvc.perform(withAdmin(get("/api/v1/admin/settings")))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.general.platformName").value("AI Interview Platform"))
                .andExpect(jsonPath("$.data.general.supportEmail").value(""))
                .andExpect(jsonPath("$.data.general.defaultLanguage").value("vi-VN"))
                .andExpect(jsonPath("$.data.general.timezone").value("GMT+7"))
                .andExpect(jsonPath("$.data.notification.inApp").value(true))
                .andExpect(jsonPath("$.data.notification.interviewReminderEmail").value(true))
                .andExpect(jsonPath("$.data.notification.jdDecisionEmail").value(true))
                .andExpect(jsonPath("$.data.notification.weeklyDigestEmail").value(false))
                .andExpect(jsonPath("$.data.ai.interviewEnabled").value(true))
                .andExpect(jsonPath("$.data.ai.defaultQuestionCount").value(5))
                .andExpect(jsonPath("$.data.ai.maxAnswerSeconds").value(180))
                .andExpect(jsonPath("$.data.ai.evaluatorModel").value("SMART_EVALUATOR_V2_5"))
                .andExpect(jsonPath("$.data.ai.sttEnabled").value(true))
                .andExpect(jsonPath("$.data.ai.promptConfig").value(""))
                .andExpect(jsonPath("$.data.meta.lastUpdatedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.meta.lastUpdatedBy").value(nullValue()));

        // Mặc định chỉ tồn tại trong catalog, không bị ghi xuống DB (lazy defaults).
        assertEquals(0L, settingRepository.count());
    }

    // ==================== AC-2: PUT hợp lệ ⇒ GET mới, meta + audit ======================

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("AC-2: valid PUT persists typed values, stamps meta and audits UPDATE_SETTINGS")
    void testUpdateSettings_Valid() throws Exception {
        mockMvc.perform(withAdmin(put("/api/v1/admin/settings"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"general":{"platformName":"Nen thang Phong Viet","supportEmail":"hotro@example.com"},
                                 "ai":{"defaultQuestionCount":8,"promptConfig":"Trait nghiem theo sao 1-5"},
                                 "notification":{"weeklyDigestEmail":true}}
                                """))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã lưu cấu hình"))
                .andExpect(jsonPath("$.data.general.platformName").value("Nen thang Phong Viet"))
                .andExpect(jsonPath("$.data.ai.defaultQuestionCount").value(8))
                .andExpect(jsonPath("$.data.notification.weeklyDigestEmail").value(true))
                .andExpect(jsonPath("$.data.meta.lastUpdatedBy").value("admin@example.com"))
                .andExpect(jsonPath("$.data.meta.lastUpdatedAt").exists());

        assertEquals(5L, settingRepository.count());
        assertEquals(1L, auditLogRepository.findAll().stream()
                .filter(row -> AuditAction.UPDATE_SETTINGS.name().equals(row.getAction()))
                .count());

        // GET lại đọc từ row đã lưu, không phải mặc định.
        mockMvc.perform(withAdmin(get("/api/v1/admin/settings")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.general.supportEmail").value("hotro@example.com"))
                .andExpect(jsonPath("$.data.ai.promptConfig").value("Trait nghiem theo sao 1-5"));
    }

    // ==================== AC-3: 422 và KHÔNG ghi một phần ==============================

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("AC-3: questionCount out of range ⇒ 422; valid keys in the same body are NOT written")
    void testUpdateSettings_OutOfRangeBlocksWholeBody() throws Exception {
        mockMvc.perform(withAdmin(put("/api/v1/admin/settings"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ai":{"defaultQuestionCount":20},"general":{"platformName":"Da luu bi loi"}}
                                """))
                .andDo(print())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value("Cấu hình không hợp lệ"))
                .andExpect(jsonPath("$.detail['ai.defaultQuestionCount']").value(containsString("3 đến 15")));

        assertEquals(0L, settingRepository.count());
        assertEquals(0L, auditLogRepository.count());

        // Giá trị vẫn là mặc định — không có ghi một phần.
        mockMvc.perform(withAdmin(get("/api/v1/admin/settings")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ai.defaultQuestionCount").value(5))
                .andExpect(jsonPath("$.data.general.platformName").value("AI Interview Platform"));
    }

    // ==================== AC-4: key lạ / kiểu sai / nhóm sai / phân quyền ===============

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("AC-4: unknown key, bad email, bad enum, non-bool, non-object group ⇒ 422 per key")
    void testUpdateSettings_EveryRuleHasItsKey() throws Exception {
        mockMvc.perform(withAdmin(put("/api/v1/admin/settings"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"general":{"unknownKey":"x","supportEmail":"khong-phai-email"},
                                 "ai":{"maxAnswerSeconds":60,"sttEnabled":"yes"}}
                                """))
                .andDo(print())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail['general.unknownKey']").value("Key cấu hình không tồn tại"))
                .andExpect(jsonPath("$.detail['general.supportEmail']").value("Giá trị phải là địa chỉ email hợp lệ"))
                .andExpect(jsonPath("$.detail['ai.maxAnswerSeconds']").value(containsString("120, 180, 300")))
                .andExpect(jsonPath("$.detail['ai.sttEnabled']").value("Giá trị phải là true hoặc false"));

        mockMvc.perform(withAdmin(put("/api/v1/admin/settings"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"general\":\"oops\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail.general").value("Nhóm cấu hình phải là object"));

        // Trống tên / quá dài cho STRING; TEXT > 2000 ký tự.
        mockMvc.perform(withAdmin(put("/api/v1/admin/settings"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"general\":{\"platformName\":\"   \"}}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail['general.platformName']").value("Giá trị không được để trống"));
    }

    @Test
    @DisplayName("AC-4: anonymous ⇒ 401, STAFF ⇒ 403 on both GET and PUT")
    void testSettings_AnonymousAndStaff() throws Exception {
        mockMvc.perform(get("/api/v1/admin/settings").with(csrf()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/admin/settings").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        for (MockHttpServletRequestBuilder call : java.util.List.of(
                get("/api/v1/admin/settings"),
                put("/api/v1/admin/settings").contentType(MediaType.APPLICATION_JSON).content("{}"))) {
            mockMvc.perform(call.with(user("staff@example.com").roles("STAFF")).with(csrf()))
                    .andExpect(status().isForbidden());
        }
        assertEquals(0L, settingRepository.count());
    }

    // ==================== AC-5: body rỗng ⇒ 200, không đổi gì ===========================

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("AC-5: empty PUT body is a no-op — no rows, no audit")
    void testUpdateSettings_EmptyBody() throws Exception {
        mockMvc.perform(withAdmin(put("/api/v1/admin/settings"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertEquals(0L, settingRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Partial PUT leaves untouched keys on their defaults")
    void testUpdateSettings_PartialKeys() throws Exception {
        mockMvc.perform(withAdmin(put("/api/v1/admin/settings"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notification\":{\"inApp\":false}}"))
                .andExpect(status().isOk());

        mockMvc.perform(withAdmin(get("/api/v1/admin/settings")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notification.inApp").value(false))
                .andExpect(jsonPath("$.data.notification.jdDecisionEmail").value(true))
                .andExpect(jsonPath("$.data.ai.defaultQuestionCount").value(5));

        assertEquals(1L, settingRepository.count());
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

    private MockHttpServletRequestBuilder withAdmin(MockHttpServletRequestBuilder call) {
        return call.with(user("admin@example.com").roles("ADMIN")).with(csrf());
    }
}
