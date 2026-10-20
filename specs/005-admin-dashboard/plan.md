# Implementation Plan: Dashboard tổng hợp (005)

**Branch**: `feature/admin-console-004-008` | **Date**: 2026-10-20 | **Spec**: [spec.md](./spec.md)

## Summary

Một controller + một service đọc, 5 nhóm số đếm. Không sửa schema, không thêm dependency.
Mọi count query đặt trong các repo có sẵn (`UserRepository`, `JobDescriptionRepository`,
`InterviewSessionRepository`, `LoginAttemptRepository`, `AdminAuditLogRepository` không cần).

## Hợp đồng API (ghi trước khi code)

`GET /api/v1/admin/dashboard?days=30` →

```json
{
  "rangeDays": 30,
  "users":   {"total":0,"active":0,"pendingVerification":0,"locked":0,"deleted":0,
              "byRole":{"USER":0,"STAFF":0,"INTERVIEWER":0,"ADMIN":0},
              "newInPeriod":0,"loginsInPeriod":0},
  "jobs":    {"total":0,"byStatus":{"DRAFT":0,"PENDING_APPROVAL":0,"OPEN":0,"CLOSED":0},
              "pendingModeration":0,"newInPeriod":0},
  "interviews":{"total":0,"inPeriod":0,"completedInPeriod":0,"avgOverallScore":null},
  "logins":  {"successInPeriod":0,"failedInPeriod":0},
  "moderation":{"pendingModeration":0}
}
```

Lỗi: `days` ngoài {7,30,90} ⇒ 400 kèm allowedValues; anonymous ⇒ 401; STAFF ⇒ 403.

## Current-state matrix

| Số trong mockup | Nguồn BE | Ghi chú |
|---|---|---|
| Tổng/role/trạng thái user | `users` + `login_attempts` | dùng predicate 001/002 (FR-003) |
| JD theo trạng thái, chờ duyệt | `job_descriptions.status` (003) | `PENDING_APPROVAL` |
| Phiên phỏng vấn AI, điểm TB | `interview_sessions` (`mode`, `status`, `overall_score`) | mode MOCK vs KNOWLEDGE |
| Lượt đăng nhập kỳ | `login_attempts` (`success`, `created_at`) | đếm dòng, distinct email cho `loginsInPeriod` |
| Uptime/latency/token/storage | **không có** | Out of scope |

## Architecture Decisions

- **D1 — một service `AdminDashboardService`**: dashboard là tập count query thuần, tách
  khỏi `AdminUserService` (miền khác); service không ghi gì.
- **D2 — count bằng JPQL `count(...)` từng nhóm, không load entity**: `interview_sessions`
  EAGER collection — load entity cả bảng để đếm là lỗi; mọi số đều count/aggregate query.
- **D3 — `byRole`/`byStatus` trả đủ key kể cả 0**: FE render label cố định, thiếu key là vỡ
  giao diện; group by + tự lấp 0 trong service.
- **D4 — `moderation.pendingModeration` = `jobs.byStatus.PENDING_APPROVAL`**: một query,
  hai chỗ đứng trong response cho tiện FE (không đếm hai lần).

## Constitution Check

| Nguyên tắc | Đạt chưa | Ghi chú |
|---|---|---|
| II — số đo thật | ☐ | AC-1 bắt buộc spot-check SQL |
| III — chặn lúc ghi | ☑ | endpoint chỉ đọc |
| IV — audit thật | ☑ | không ghi audit cho GET dashboard (đồng bộ 001: GET không audit) |
| VI — phòng vệ nhiều tầng | ☑ sẵn | URL rule ADMIN |
| VII — phạm vi tối thiểu | ☑ | Out of scope 6 mục |
| VIII — đo trước khi vòng | ☑ | FR-003 bám predicate đã đo của 001/002 |

## Testing Strategy

- Integration `AdminDashboardIntegrationTest`: seed dữ liệu lệch kỳ (user/phiên/attempt cũ
  40 ngày, mới 3 ngày) → AC-1, AC-2; DB trống → AC-4; `days=5` 400; STAFF 403, anonymous
  401 (AC-3).
- So khớp: dùng chính repo count query trong test để đối chiếu (không copy SQL thủ công
  trong test — trừ trường hợp bắt buộc thì viết JPQL tương đương).

## Open risks

- **R-1**: `login_attempts` khóa theo email (bài học 002) ⇒ `loginsInPeriod` là distinct
  **email**, user đổi email làm tách chuỗi — chấp nhận, ghi nhãn FE "email đăng nhập thành
  công trong kỳ".
- **R-2**: `avgOverallScore` chưa có đơn vị chuẩn hóa giữa mode MOCK/KNOWLEDGE — trả trung
  bình chung `overall_score`, nếu anh cần tách theo mode thì thêm field sau, không đổi hợp đồng cũ.
