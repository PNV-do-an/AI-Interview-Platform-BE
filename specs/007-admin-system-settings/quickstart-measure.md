# Bộ đo 007 — Cấu hình hệ thống (Settings CRUD) (đo 2026-10-20)

## Môi trường đo

- Container maven + Testcontainers MySQL 8.0 thật; `GET/PUT /api/v1/admin/settings` qua MockMvc.
- `system_settings` là bảng KV kiểu String, mọi giá trị sinh ra từ catalogue
  `SettingDefinition` (kiểu + allowed values + mặc định) — test đọc JSON rồi đối chiếu số row
  trong DB bằng `settingRepository.count()` và `JdbcTemplate`.

## Kết quả

Class `AdminSettingsIntegrationTest` — **7/7 pass**, 93.6s (lần chạy chốt 2026-10-20).

Nằm trong suite đầy đủ `mvn -B -o test` (chạy gộp 21h45 2026-10-20): **154 test**, mọi class khác
xanh ở cùng lần chạy; 15 error duy nhất là `JobControllerIntegrationTest.setUp` vướng FK bảng 004,
đã sửa và chạy lại riêng class đó (22h53) ⇒ **16/16**. Tổng **154/154**.

| AC | Test | Số đo được |
|---|---|---|
| AC-1 | `testGetSettings_FirstTimeDefaults` | DB chưa có row ⇒ GET trả đủ **14 key** trong 3 nhóm `general/notification/ai`, đúng kiểu (bool/enum/int/string/email/text), `meta` null, `settingRepository.count() = 0` (đọc lần đầu không ghi rác) |
| AC-2 | `testUpdateSettings_Valid` | PUT hợp lệ ⇒ message "Đã lưu cấu hình", `meta.lastUpdatedBy = admin@example.com`, đúng **5 row** được ghi, `admin_audit_log` có **1** dòng `UPDATE_SETTINGS`; GET lại thấy giá trị đã lưu thay cho mặc định |
| AC-3 | `testUpdateSettings_OutOfRangeBlocksWholeBody` | `ai.defaultQuestionCount = 20` ⇒ **422**, body `ErrorModel` (`$.status = 422`, `$.message = "Cấu hình không hợp lệ"`), `$.detail['ai.defaultQuestionCount']` chứa "3 đến 15"; **cả body không được ghi**: 0 row, GET vẫn trả mặc định. Đây là rule chặn lúc ghi, không phải để lưu rồi mới lộ khi đọc |
| AC-4 | `testUpdateSettings_EveryRuleHasItsKey` | Mỗi rule nêu đúng key bị lỗi: key lạ `general.unknownKey`, email sai định dạng, `ai.maxAnswerSeconds = 60` (ngoài allowed values), `ai.sttEnabled = "yes"` (không phải bool), group không phải object, `general.platformName` chỉ có space ⇒ 422 |
| AC-5 | `testUpdateSettings_EmptyBody` | PUT `{}` ⇒ 200, không sinh row, không sinh audit |
| — | `testUpdateSettings_PartialKeys` | PUT chỉ vài key ⇒ các key còn lại vẫn lấy mặc định catalogue (không bị xóa về rỗng) |
| AC-4 | `testSettings_AnonymousAndStaff` | anonymous ⇒ 401 cho cả GET và PUT; `ROLE_STAFF` ⇒ 403 cho cả GET và PUT |

## Sai lệch so với plan (có chủ đích)

- **14 key, không phải 13** như AC-1 viết: catalogue có thêm `ai.promptConfig` (kiểu TEXT).
  Test đếm theo catalogue để số này không trôi khi thêm setting.
- 422 trả theo `ErrorModel` (`status/message/path/timestamp/detail`) chứ không có trường `success`
  như envelope thành công — test assert `$.status`, không assert `$.success`.
- Settings **chưa được MockInterview đọc lại** (chốt phạm vi kỳ này): đổi
  `ai.defaultQuestionCount` không làm số câu hỏi của buổi mock thật thay đổi.

## Chưa đo (nằm ngoài kỳ này)

- Chưa wire sang `MockInterviewServiceImpl`, chưa đo ảnh hưởng runtime.
- Chưa đo trên Render staging.
