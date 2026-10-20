# Implementation Plan + Tasks: Cấu hình hệ thống (007)

**Branch**: `feature/admin-console-004-008` | **Date**: 2026-10-20 | **Spec**: [spec.md](./spec.md)

## Hợp đồng API

```json
GET/PUT /api/v1/admin/settings
{
  "general":      {"platformName":"...","supportEmail":"...","defaultLanguage":"vi-VN","timezone":"GMT+7"},
  "notification": {"inApp":true,"interviewReminderEmail":true,"jdDecisionEmail":true,"weeklyDigestEmail":false},
  "ai":           {"interviewEnabled":true,"defaultQuestionCount":5,"maxAnswerSeconds":180,
                   "evaluatorModel":"SMART_EVALUATOR_V2_5","sttEnabled":true,"promptConfig":""},
  "meta":         {"lastUpdatedAt":null,"lastUpdatedBy":null}
}
```

PUT nhận 3 nhóm (không nhận `meta` — key lạ ⇒ 422). Lỗi 422 body:
`{"status":422,"message":"Cấu hình không hợp lệ","detail":{"ai.defaultQuestionCount":"Giá trị phải từ 3 đến 15"}}`.

## Architecture Decisions

- **D1 — validate bằng bảng rule trong code, không bean validation trên DTO lồng**: key lạ
  và sai giá trị đều đi qua một hàm `validate(GroupedSettings)` trả map key→message; map
  khác rỗng ⇒ 422. Bean validation (`@Valid`) giữ cho các feature cũ, không trộn.
- **D2 — 422 là mã riêng của settings**: `GlobalExceptionHandler` thêm handler cho
  `SettingsValidationException` ⇒ 422 với `detail` là map key→message. Các feature khác
  giữ nguyên 400 (không đụng hợp đồng cũ).
- **D3 — giá trị lưu TEXT dạng chuỗi kiểu hóa** (`"true"`, `"5"`, `"vi-VN"`), serialize/parse
  qua `SettingType` enum (STRING/INT/BOOL/ENUM) gắn với từng key definition — một nguồn
  sự thật về danh mục key + rule + default: lớp `SettingDefinitions`.
- **D4 — GET lazy-default**: thiếu row ⇒ trả default, **không tự ghi row** (tránh ghi ẩn khi
  ai đó cứ GET); chỉ PUT ghi.
- **D5 — ghi từng row trong một transaction**: PUT hợp lệ ⇒ upsert đủ các key có trong body,
  `updatedBy`/`updatedAt` cập nhật trên row bị đổi; `meta` = max(updatedAt) + `updatedBy`
  của row đó.

## Tasks

- [ ] T001 [P] Entity `SystemSetting` + repo (`findBySettingKey`, `findAll`).
- [ ] T002 [P] `SettingDefinitions` (key → type/rule/default) + exception
      `SettingsValidationException` + handler 422 trong `GlobalExceptionHandler`.
- [ ] T003 DTO grouped + `AdminSettingsService(Impl)` (get với default, put với validate +
      upsert + audit `UPDATE_SETTINGS` — thêm enum `AuditAction.UPDATE_SETTINGS`) +
      `AdminSettingsController` (GET/PUT).
- [ ] T004 [P] Integration test `AdminSettingsIntegrationTest`: AC-1..AC-5 + PUT hai lần ⇒
      meta đúng admin sau cùng; STAFF 403 GET/PUT; anonymous 401.
- [ ] T005 Scan tiếng Việt message; ghi `quickstart-measure.md`; suite + `docker build`.

## Open risks

- **R-1**: 422 là mã mới trong hệ thống — FE phải xử lý riêng (hợp đồng nói rõ shape).
  Nếu anh muốn đồng bộ 400 cho dễ thì đổi 1 dòng handler, báo FE lại mã.
- **R-2**: settings chưa wire vào nghiệp vụ ⇒ có thể bị hiểu nhầm "lưu rồi không có tác
  dụng". Ghi chú này nằm ngay spec và known-gaps.
