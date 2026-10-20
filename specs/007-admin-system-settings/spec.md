# Feature Specification: Cấu hình hệ thống — tham số nền tảng & AI Interview (màn "Cấu hình")

**Feature Branch**: `feature/admin-console-004-008`
**Created**: 2026-10-20 · **Status**: Draft · **Input**: mockup Stitch "Admin System
Settings" + chốt phạm vi 2026-10-20 (phương án "Settings CRUD" — **không** wire vào
MockInterview ở kỳ này).

## Why file 007

BE hiện không có bất kỳ khái niệm "cấu hình động" nào (grep `SystemSetting` = 0 kết quả) —
mọi tham số là hard-code hoặc env. Màn settings đòi một bảng key-value + API đọc/ghi có
validate chặn lúc ghi. Phạm vi chốt: chỉ CRUD + validate; việc các service nghiệp vụ
**đọc** cấu hình này để đổi hành vi (số câu hỏi mock interview…) là kỳ sau — ghi rõ để FE
và người đọc không kỳ vọng sai.

## User Stories

**US-01** Admin mở tab cấu hình → thấy giá trị hiện hành của 3 nhóm: thông tin nền tảng
(tên, email hỗ trợ, ngôn ngữ, múi giờ), 4 công tắc thông báo, tham số AI Interview
(bật/tắt, số câu hỏi, thời gian trả lời, model, STT, prompt cấu hình).
**US-02** Admin sửa và lưu ⇒ giá trị mới được persist; lần đọc sau trả giá trị mới, kèm
"lần cập nhật cuối bởi ai, lúc nào".
**US-03** Giá trị vi phạm rule (số câu hỏi 20, thời gian 90s, model lạ, email sai định dạng)
bị **chặn ngay lúc ghi** với mã 422 + message chỉ rõ key sai — không lưu xong mới lộ ở GET.
**US-04** Lần gọi GET đầu tiên trên môi trường chưa có dữ liệu ⇒ trả bộ giá trị mặc định
(không lỗi, không bắt ai seed tay).

## Functional Requirements

- **FR-001** Entity `SystemSetting`: `settingKey` (unique), `settingValue` TEXT,
  `updatedBy` (email admin), `updatedAt`. Cấu hình chia nhóm trong DTO, key phẳng trong DB.
- **FR-002** Danh mục key + rule (validate ở service, chặn lúc ghi):
  | Key | Kiểu | Mặc định | Rule |
  |---|---|---|---|
  | `general.platformName` | string | `AI Interview Platform` | 1–100 ký tự |
  | `general.supportEmail` | string | `''` | email hợp lệ |
  | `general.defaultLanguage` | enum | `vi-VN` | `vi-VN, en-US, ja-JP` |
  | `general.timezone` | enum | `GMT+7` | `GMT+7, GMT+8, GMT+9, GMT+0` |
  | `notification.inApp` | bool | `true` | — |
  | `notification.interviewReminderEmail` | bool | `true` | — |
  | `notification.jdDecisionEmail` | bool | `true` | — |
  | `notification.weeklyDigestEmail` | bool | `false` | — |
  | `ai.interviewEnabled` | bool | `true` | — |
  | `ai.defaultQuestionCount` | int | `5` | 3–15 |
  | `ai.maxAnswerSeconds` | enum | `180` | `120, 180, 300` |
  | `ai.evaluatorModel` | enum | `SMART_EVALUATOR_V2_5` | `SMART_EVALUATOR_V2_5, DEEPSEEK_FAST_ENGINE, GPT4O_ENTERPRISE` |
  | `ai.sttEnabled` | bool | `true` | — |
  | `ai.promptConfig` | string | `''` | ≤ 2000 ký tự |
- **FR-003** `GET /api/v1/admin/settings` — object theo nhóm, giá trị **đúng kiểu**
  (bool/int/string), kèm `meta: {lastUpdatedAt, lastUpdatedBy}` (lấy từ row mới nhất).
  Row thiếu ⇒ trả mặc định (US-04).
- **FR-004** `PUT /api/v1/admin/settings` — nhận object cùng hình dạng; validate toàn bộ
  key có trong body: key lạ ⇒ 422; sai kiểu/giá trị ⇒ 422 kèm `{key, message}`;
  **không có hiện tượng lưu một phần**: cả body hợp lệ mới ghi. Ghi từng row với
  `updatedBy` = admin hiện tại. Trả object mới nhất.
- **FR-005** Chỉ ADMIN (URL rule). Ghi audit `UPDATE_SETTINGS` (detail = danh sách key đổi).
- **FR-006** Không service nghiệp vụ nào đọc settings ở kỳ này (chốt phạm vi) — entity/API
  đứng độc lập, không chạm `MockInterviewServiceImpl`.

## Out of scope

- Wire tham số AI vào luồng MockInterview (đã chốt "không" kỳ này).
- Logo upload (màn có ô upload logo — cần storage file; không có infra lưu file).
- Tab "Quy trình kiểm duyệt", "Bảo mật & Phân quyền" trong mockup chỉ là nav anchor không
  có nội dung — không làm.
- Chỉ số hạ tầng (API latency, db pool) — không có nguồn (như 005).
- Đa ngôn ngữ thật cho nội dung BE (key `defaultLanguage` chỉ là cấu hình lưu sẵn).

## Acceptance criteria (đo được)

- AC-1: GET lần đầu (DB chưa có row) ⇒ trả đủ 13 key theo nhóm, đúng kiểu + mặc định.
- AC-2: PUT hợp lệ ⇒ GET sau đó trả giá trị mới, `meta.lastUpdatedBy` = email admin vừa ghi;
  audit có dòng `UPDATE_SETTINGS`.
- AC-3: PUT `ai.defaultQuestionCount=20` ⇒ **422**, message nêu key + rule "3–15", GET sau
  đó vẫn giá trị cũ (không lưu một phần — cả khi body có key khác hợp lệ).
- AC-4: PUT key lạ / model lạ / email sai ⇒ 422 với message tương ứng; anonymous ⇒ 401;
  STAFF ⇒ 403 (cả GET lẫn PUT).
- AC-5: PUT body rỗng `{}` ⇒ 200, không đổi gì, không sinh row rác.
