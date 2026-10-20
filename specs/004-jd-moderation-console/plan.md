# Implementation Plan: Console kiểm duyệt JD (004)

**Branch**: `feature/admin-console-004-008` | **Date**: 2026-10-20 | **Spec**: [spec.md](./spec.md)
**Base**: dev 889b119 — vòng đời JD 003 đã chạy (DRAFT→PENDING_APPROVAL→OPEN/DRAFT), 118 test xanh.

## Summary

Vòng đời đã có; 004 chỉ thêm **góc nhìn admin**: 4 endpoint console + bảng
`job_moderation_records` + 3 cột nullable trên `job_descriptions`. Không đổi bất kỳ hợp
đồng 003 nào mà FE đang đọc (POST/PUT/submit/review/status giữ nguyên hình dạng).

## Current-state matrix (đọc code, không đoán)

| Khối trong mockup | BE hiện có | Khoảng trống |
|---|---|---|
| Hàng đợi chờ duyệt (màn 18) | `JobServiceImpl.adminListJobs` (dòng 165) — **dead code**, không controller nào expose; chỉ search, không lọc trạng thái | expose + lọc `status` + trả đủ field console |
| Chi tiết + timeline (18.1) | `GET /api/v1/jobs/{id}` che tin chưa OPEN với người lạ; `reviewNote` trên entity | không có timeline; admin xem qua endpoint công khai là sai semantics |
| Duyệt / từ chối | `PATCH /{id}/review` (ADMIN/STAFF) + audit `APPROVE_JOB`/`REJECT_JOB` | **không còn gì** — chỉ bổ sung ghi record |
| Lịch sử + thống kê (18.2) | audit log có action nhưng `detail` là chuỗi tự do, không lọc theo job được | bảng record riêng + endpoint lọc/đếm |
| "Thời gian xử lý 1h45m" | không có `submitted_at`/`decided_at` | 2 cột nullable mới |
| Điểm AI an toàn | `aiScreeningEnabled/Track/PassScore` là **cấu hình** do NTD nhập, không phải kết quả | cột `ai_safety_score` nullable, không writer |

## Hợp đồng API (ghi trước khi code)

| # | Endpoint | Request | Response data | Lỗi |
|---|---|---|---|---|
| E1 | `GET /api/v1/admin/jobs/moderation?status=&search=&page=0&size=20` | `status` enum `JobStatus` (mặc định `PENDING_APPROVAL`), `search ≤100`, `size ≤100` | `Page<ModerationJobResponse>`: `id,title,companyName,employerName,employerEmail,status,reviewNote,submittedAt,decidedAt,aiSafetyScore,createdAt` | 400 status sai (allowedValues), 401/403 |
| E2 | `GET /api/v1/admin/jobs/moderation/{id}` | — | E1 field + full `JobDetailResponse` fields + `timeline: [ModerationRecordResponse]` | 404 id/xóa mềm |
| E3 | `GET /api/v1/admin/jobs/moderation/history?decision=&from=&to=&moderatorId=&search=&page=&size=` | `decision ∈ APPROVED,REJECTED`, `from/to` date-only, `moderatorId` Long | `Page<JobModerationHistoryResponse>`: `id,jobId,jobTitle,companyName,employerName,employerEmail,action,moderatorId,moderatorEmail,note,processingSeconds,createdAt` | 400 sai enum/ngày, 401/403 |
| E4 | `GET /api/v1/admin/jobs/moderation/history/stats` | — | `{totalDecisions,approved,rejected,avgProcessingSeconds}` | 401/403 |

`ModerationRecordResponse`: `id,actorId,actorName,actorRole,action,note,createdAt`.
`PATCH /api/v1/jobs/{id}/review` giữ nguyên request/response 003, chỉ thêm hiệu ứng ghi.

## Architecture Decisions

- **D1 — mở rộng `JobService(Impl)` thay vì service mới**: review đã nằm đó; queue/history là
  đọc trên cùng 2 repo (`JobDescriptionRepository` + repo record mới). Không tạo
  `JobModerationService` riêng (Constitution VII).
- **D2 — record là bảng riêng, không nhét vào `admin_audit_log`**: audit có ràng buộc
  `admin_id NOT NULL` (R10 của 001) trong khi SUBMITTED/RESUBMITTED do **NTD** thực hiện —
  hai bảng, hai nghĩa; `admin_audit_log` vẫn ghi như cũ cho hành động admin.
- **D3 — xóa `adminListJobs` dead code**, thay bằng query mới của E1 (cùng file service);
  không giữ phương thức không ai gọi.
- **D4 — sắp xếp và lọc cố định, tối giản**: `createdAt DESC` cho queue,
  `created_at DESC` cho history; không nhận tham số sort kỳ này (mockup có sort "điểm tín
  nhiệm" nhưng không có nguồn dữ liệu — Out of scope).
- **D5 — thời gian xử lý tính tại chỗ đọc**: `processingSeconds` trả dạng số giây đã tính
  (`record.created_at − job.submitted_at`), FE định dạng "1h 45m"; record thiếu mốc trả null.

## Constitution Check

| Nguyên tắc | Đạt chưa | Ghi chú |
|---|---|---|
| II — số đo thật | ☐ | E1..E4 + hiệu ứng ghi đều cần dòng M-x trong quickstart |
| III — chặn lúc ghi | ☐ | review giữ validate 003 (từ chối phải có lý do); record ghi trong cùng transaction |
| IV — audit thật | ☑ | audit admin giữ nguyên; record kiểm duyệt cho phép actor NTD vì là bảng khác (D2) |
| VI — phòng vệ nhiều tầng | ☑ sẵn | console dưới URL rule `/api/v1/admin/**`; review giữ `@PreAuthorize` |
| VII — phạm vi tối thiểu | ☐ | Out of scope liệt kê 7 mục cắt; không thêm dependency |
| VIII — đo trước khi vòng | ☑ | D2 dựa trên R10 đã đo của 001 (admin_id NOT NULL), không đoán |

## Testing Strategy

- Mở rộng integration test mới `JobModerationConsoleIntegrationTest` (Testcontainers MySQL,
  MockMvc, mẫu của `AdminUserControllerIntegrationTest`): phủ AC-1..AC-6 + hồi quy luồng 003
  (create→submit→review→OPEN vẫn xanh, message không đổi).
- Hồi quy: `searchJobs` công khai vẫn chỉ thấy OPEN; `getJobDetail` người lạ vẫn 404 với tin
  chưa OPEN.
- Đo thật HTTP bằng harness local (MySQL Laragon 3307 + fake Gmail không cần cho 004 vì
  không có mail mới) — ghi số vào quickstart.

## Phases (thứ tự, không ước lượng thời gian)

1. Entity `JobModerationRecord` + repo + 3 cột nullable trên `JobDescription` (+DTO).
2. Hiệu ứng ghi: submit/resubmit/review ghi record + `submittedAt`/`decidedAt`.
3. 4 endpoint console (E1..E4) + xóa dead code (D3).
4. Test integration + đo AC-1..AC-6, ghi quickstart.

## Open risks

- **R-1**: JD tạo trước 004 không có `submitted_at` ⇒ history của chúng thiếu thời gian xử lý
  (null) — chấp nhận, FE hiển thị "—".
- **R-2**: STAFF duyệt được qua endpoint cũ nhưng không thấy console (FR-008) — bất hợp lý
  về UX nhưng là hợp đồng đang chạy; nếu anh muốn thu về ADMIN-only thì là thay đổi hợp đồng
  phải báo FE, để kỳ sau.
- **R-3**: `ai_safety_score` không có writer ⇒ mọi response null. Nếu FE render "0/100" là
  sai — hợp đồng nói rõ null = chưa có đánh giá.
