package com.aiinterview.platform.service.admin.impl;

import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.common.exception.SettingsValidationException;
import com.aiinterview.platform.model.dto.response.AdminSettingsResponse;
import com.aiinterview.platform.model.entity.AdminAuditLog;
import com.aiinterview.platform.model.entity.SystemSetting;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.repository.AdminAuditLogRepository;
import com.aiinterview.platform.model.repository.SystemSettingRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GET/PUT /api/v1/admin/settings (spec 007 FR-002) — catalog cấu hình là enum SettingDefinition,
 * nên phần đáng test nhất là bộ validate theo kiểu dữ liệu và việc audit đúng một lần.
 * <p>
 * Repository được giả lập bằng một Map thật: nhờ vậy test thấy được vòng "validate -> ghi -> đọc lại"
 * giống production, thay vì stub cứng từng lần gọi.
 */
class AdminSettingsServiceImplTest {

    private static final String ADMIN_EMAIL = "admin@aiinterview.vn";

    private final Map<String, SystemSetting> table = new LinkedHashMap<>();

    private SystemSettingRepository settingRepository;
    private AdminAuditLogRepository auditLogRepository;
    private UserRepository userRepository;
    private AdminSettingsServiceImpl service;

    @BeforeEach
    void setUp() {
        settingRepository = mock(SystemSettingRepository.class);
        auditLogRepository = mock(AdminAuditLogRepository.class);
        userRepository = mock(UserRepository.class);
        service = new AdminSettingsServiceImpl(settingRepository, auditLogRepository, userRepository);

        when(settingRepository.findBySettingKey(anyString()))
                .thenAnswer(call -> Optional.ofNullable(table.get(call.getArgument(0, String.class))));
        when(settingRepository.findBySettingKeyIn(anyList()))
                .thenAnswer(call -> new ArrayList<>(table.values()));
        when(settingRepository.findAll()).thenAnswer(call -> new ArrayList<>(table.values()));
        when(settingRepository.save(any())).thenAnswer(call -> {
            SystemSetting row = call.getArgument(0);
            table.put(row.getSettingKey(), row);
            return row;
        });

        when(userRepository.findByEmail(ADMIN_EMAIL))
                .thenReturn(Optional.of(User.builder().id(1L).email(ADMIN_EMAIL).fullName("Admin").build()));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(ADMIN_EMAIL, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── đọc cấu hình ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Bảng rỗng: trả toàn bộ giá trị mặc định của catalog, không phải null")
    void getSettings_emptyTableReturnsCatalogDefaults() {
        AdminSettingsResponse settings = service.getSettings();

        assertThat(settings.getGeneral().getPlatformName()).isEqualTo("AI Interview Platform");
        assertThat(settings.getGeneral().getSupportEmail()).isEmpty();
        assertThat(settings.getGeneral().getDefaultLanguage()).isEqualTo("vi-VN");
        assertThat(settings.getGeneral().getTimezone()).isEqualTo("GMT+7");

        assertThat(settings.getNotification().isInApp()).isTrue();
        assertThat(settings.getNotification().isInterviewReminderEmail()).isTrue();
        assertThat(settings.getNotification().isJdDecisionEmail()).isTrue();
        assertThat(settings.getNotification().isWeeklyDigestEmail()).isFalse();

        assertThat(settings.getAi().isInterviewEnabled()).isTrue();
        assertThat(settings.getAi().getDefaultQuestionCount()).isEqualTo(5);
        assertThat(settings.getAi().getMaxAnswerSeconds()).isEqualTo(180);
        assertThat(settings.getAi().getEvaluatorModel()).isEqualTo("SMART_EVALUATOR_V2_5");
        assertThat(settings.getAi().isSttEnabled()).isTrue();
        assertThat(settings.getAi().getPromptConfig()).isEmpty();

        assertThat(settings.getMeta().getLastUpdatedAt()).isNull();
        assertThat(settings.getMeta().getLastUpdatedBy()).isNull();
    }

    @Test
    @DisplayName("Giá trị trong DB thắng giá trị mặc định, và được ép đúng kiểu (bool/int/enum)")
    void getSettings_storedValuesOverrideDefaults() {
        store("general.platformName", "MyAI");
        store("ai.defaultQuestionCount", "8");
        store("notification.weeklyDigestEmail", "true");
        store("ai.maxAnswerSeconds", "300");

        AdminSettingsResponse settings = service.getSettings();

        assertThat(settings.getGeneral().getPlatformName()).isEqualTo("MyAI");
        assertThat(settings.getAi().getDefaultQuestionCount()).isEqualTo(8);
        assertThat(settings.getNotification().isWeeklyDigestEmail()).isTrue();
        assertThat(settings.getAi().getMaxAnswerSeconds()).isEqualTo(300);
    }

    @Test
    @DisplayName("Giá trị int bị hỏng trong DB không làm sập màn hình: quay về default")
    void getSettings_brokenIntFallsBackToDefault() {
        store("ai.defaultQuestionCount", "abc");

        assertThat(service.getSettings().getAi().getDefaultQuestionCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("meta lấy dòng cập nhật mới nhất và đúng người sửa dòng đó")
    void getSettings_metaUsesLatestRowAndItsAuthor() {
        store("general.platformName", "A", LocalDateTime.of(2026, 1, 5, 9, 0), "old@aiinterview.vn");
        store("general.timezone", "GMT+8", LocalDateTime.of(2026, 3, 9, 9, 0), "new@aiinterview.vn");

        AdminSettingsResponse.Meta meta = service.getSettings().getMeta();

        assertThat(meta.getLastUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 3, 9, 9, 0));
        assertThat(meta.getLastUpdatedBy()).isEqualTo("new@aiinterview.vn");
    }

    // ── ghi cấu hình ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Giá trị hợp lệ: trim, ghi đúng key, set updatedBy=admin, và audit đúng 1 lần")
    void update_validValuesArePersistedAndAudited() {
        AdminSettingsResponse after = service.updateSettings(Map.of(
                "general", Map.of("platformName", "  MyAI  ", "supportEmail", " support@myai.vn "),
                "ai", Map.of("defaultQuestionCount", 8)));

        verify(settingRepository, org.mockito.Mockito.times(3)).save(any());
        assertThat(table.get("general.platformName").getSettingValue()).isEqualTo("MyAI");
        assertThat(table.get("general.supportEmail").getSettingValue()).isEqualTo("support@myai.vn");
        assertThat(table.get("ai.defaultQuestionCount").getSettingValue()).isEqualTo("8");
        assertThat(table.values()).allSatisfy(row -> assertThat(row.getUpdatedBy()).isEqualTo(ADMIN_EMAIL));

        ArgumentCaptor<AdminAuditLog> audit = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditLogRepository).save(audit.capture());
        assertThat(audit.getValue().getAdminEmail()).isEqualTo(ADMIN_EMAIL);
        assertThat(audit.getValue().getAdminId()).isEqualTo(1L);
        assertThat(audit.getValue().getAction()).isEqualTo("UPDATE_SETTINGS");
        assertThat(audit.getValue().getDetail())
                .contains("general.platformName")
                .contains("general.supportEmail")
                .contains("ai.defaultQuestionCount");
        assertThat(audit.getValue().isSucceeded()).isTrue();

        // Trả về chính trạng thái sau khi ghi, để FE khỏi phải gọi GET thêm lần nữa.
        assertThat(after.getGeneral().getPlatformName()).isEqualTo("MyAI");
        assertThat(after.getAi().getDefaultQuestionCount()).isEqualTo(8);
    }

    @Test
    @DisplayName("Key đã có trong DB thì cập nhật tại chỗ, không tạo dòng trùng")
    void update_existingRowIsUpdatedInPlace() {
        store("general.platformName", "Old");

        service.updateSettings(Map.of("general", Map.of("platformName", "New")));

        assertThat(table).hasSize(1);
        assertThat(table.get("general.platformName").getSettingValue()).isEqualTo("New");
    }

    @Test
    @DisplayName("Body rỗng: không ghi gì, không audit, vẫn trả cấu hình hiện tại")
    void update_emptyBodyWritesNothing() {
        AdminSettingsResponse after = service.updateSettings(Map.of());

        verify(settingRepository, never()).save(any());
        verify(auditLogRepository, never()).save(any());
        assertThat(after.getGeneral().getPlatformName()).isEqualTo("AI Interview Platform");
    }

    @Test
    @DisplayName("Không có phiên đăng nhập: nổ 404 trước khi validate (admin là bắt buộc)")
    void update_withoutAuthenticationThrows() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> service.updateSettings(Map.of("general", Map.of("platformName", "X"))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("chưa đăng nhập");
    }

    // ── validate ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Key lạ bị báo theo dạng group.field để FE highlight đúng ô")
    void update_unknownKeyIsReportedAsFlatKey() {
        assertViolation(Map.of("ai", Map.of("notAKey", 1)), "ai.notAKey",
                "Key cấu hình không tồn tại");
    }

    @Test
    @DisplayName("Nhóm không phải object (vd gửi string) bị chặn riêng, không lẫn với lỗi field")
    void update_groupMustBeObject() {
        assertViolation(Map.of("general", "oops"), "general",
                "Nhóm cấu hình phải là object");
    }

    @Test
    @DisplayName("STRING: không được rỗng và tối đa 100 ký tự")
    void update_stringRules() {
        assertViolation(Map.of("general", Map.of("platformName", "   ")),
                "general.platformName", "Giá trị không được để trống");
        assertViolation(Map.of("general", Map.of("platformName", "x".repeat(101))),
                "general.platformName", "Giá trị tối đa 100 ký tự");
    }

    @Test
    @DisplayName("TEXT: cho phép rỗng nhưng tối đa 2000 ký tự (prompt AI)")
    void update_textRules() {
        assertViolation(Map.of("ai", Map.of("promptConfig", "x".repeat(2001))),
                "ai.promptConfig", "Giá trị tối đa 2000 ký tự");

        service.updateSettings(Map.of("ai", Map.of("promptConfig", "")));
        assertThat(table.get("ai.promptConfig").getSettingValue()).isEmpty();
    }

    @Test
    @DisplayName("EMAIL: rỗng được phép (tắt hỗ trợ), sai định dạng thì không")
    void update_emailRules() {
        assertViolation(Map.of("general", Map.of("supportEmail", "khong-phai-email")),
                "general.supportEmail", "Giá trị phải là địa chỉ email hợp lệ");

        service.updateSettings(Map.of("general", Map.of("supportEmail", "")));
        assertThat(table.get("general.supportEmail").getSettingValue()).isEmpty();
    }

    @Test
    @DisplayName("BOOL: chỉ nhận boolean thật, nhận chuỗi \"true\" là lỗi")
    void update_boolMustBeBoolean() {
        assertViolation(Map.of("notification", Map.of("inApp", "true")),
                "notification.inApp", "Giá trị phải là true hoặc false");
    }

    @Test
    @DisplayName("INT: nhận số, không nhận chuỗi số")
    void update_intMustBeNumber() {
        assertViolation(Map.of("ai", Map.of("defaultQuestionCount", "8")),
                "ai.defaultQuestionCount", "Giá trị phải là số");
    }

    @Test
    @DisplayName("defaultQuestionCount bị chặn ngoài khoảng 3..15 để AI không hỏi 1 hay 50 câu")
    void update_questionCountRangeIsEnforced() {
        assertViolation(Map.of("ai", Map.of("defaultQuestionCount", 2)),
                "ai.defaultQuestionCount", "Giá trị phải từ 3 đến 15");
        assertViolation(Map.of("ai", Map.of("defaultQuestionCount", 16)),
                "ai.defaultQuestionCount", "Giá trị phải từ 3 đến 15");
    }

    @Test
    @DisplayName("ENUM: giá trị ngoài danh sách trắng bị chặn và báo rõ các giá trị cho phép")
    void update_enumMustBeInWhitelist() {
        assertViolation(Map.of("general", Map.of("defaultLanguage", "fr-FR")),
                "general.defaultLanguage", "Giá trị được phép: vi-VN, en-US, ja-JP");
    }

    @Test
    @DisplayName("Nhiều lỗi trong một request được gom lại (422), không dừng ở lỗi đầu tiên")
    void update_collectsEveryViolationInOnePass() {
        SettingsValidationException ex = catchViolation(Map.of(
                "general", Map.of("defaultLanguage", "fr-FR"),
                "ai", Map.of("defaultQuestionCount", 99)));

        assertThat(ex.getViolations()).containsOnlyKeys(
                "general.defaultLanguage", "ai.defaultQuestionCount");
    }

    @Test
    @DisplayName("Có lỗi thì không ghi bất cứ dòng nào (all-or-nothing)")
    void update_invalidBodyWritesNothing() {
        catchViolation(Map.of(
                "general", Map.of("platformName", "OK", "defaultLanguage", "fr-FR")));

        verify(settingRepository, never()).save(any());
        verify(auditLogRepository, never()).save(any());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void store(String key, String value) {
        table.put(key, SystemSetting.builder()
                .settingKey(key).settingValue(value).build());
    }

    private void store(String key, String value, LocalDateTime updatedAt, String updatedBy) {
        table.put(key, SystemSetting.builder()
                .settingKey(key).settingValue(value)
                .updatedAt(updatedAt).updatedBy(updatedBy).build());
    }

    private SettingsValidationException catchViolation(Map<String, Object> body) {
        return (SettingsValidationException) org.assertj.core.api.Assertions
                .catchThrowable(() -> service.updateSettings(body));
    }

    private void assertViolation(Map<String, Object> body, String key, String message) {
        SettingsValidationException ex = catchViolation(body);

        assertThat(ex.getViolations()).containsEntry(key, message);
    }
}
