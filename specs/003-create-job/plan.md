# Implementation Plan: BE wizard "Create Job Posting" — draft + vòng đời duyệt + field cấu trúc

**Branch**: `feature/create-job` | **Date**: 2026-10-20 | **Spec**: [spec.md](./spec.md)
**Base**: CRUD job cơ bản đã có (POST/PUT/DELETE/status/my-jobs/suggestions), tạo ngay `OPEN`,
8 field text; `adminListJobs` có ở service nhưng chưa expose endpoint.

## Summary

Mockup không đòi viết lại CRUD — nó đòi **3 khả năng mới**: (1) lưu nháp + field cấu trúc của
wizard 4 bước, (2) vòng đời duyệt `DRAFT → PENDING_APPROVAL → OPEN/DRAFT(kèm lý do)`, (3) cấu hình
AI screening lưu theo job. Bốn câu Clarify C-1..C-4 đã anh Huy chốt 2026-10-20 đều theo hướng tối
thiểu: đủ vòng đời nhưng chỉ endpoint review đơn job; không thêm role; thêm cột nullable + giữ
`salaryRange`; chỉ lưu cấu hình AI, không AI engine. Kế hoạch tách Lô A (schema + lưu wizard) khỏi
Lô B (vòng đời duyệt) và Lô C (cắt bỏ).

## Current-state matrix (đọc code, không đoán)

| Khối trong mockup | BE hiện có | Khoảng trống |
|---|---|---|
| Job Title* | `title` (@NotBlank) | **không còn gì** |
| Department / Experience Level / Employment Type | — | **thiếu cột** |
| Hiring Manager | `employer` = người tạo, response có `employerName` | **không còn gì** (hiring manager = người tạo) |
| Workplace Model (On-site/Hybrid/Remote) | — | **thiếu cột + enum** |
| Base Location / Timezone | `location` có; timezone không | thiếu 1 cột |
| Currency + Min/Max + Equity + Bonus | `salaryRange` 1 chuỗi text | **thiếu cấu trúc** (C-3: thêm cột nullable, giữ chuỗi) |
| Tech-stack tags | `requiredSkills` comma-string | **không còn gì** |
| Role Summary rich-text | `description` TEXT | **không còn gì** |
| Save Draft + "Auto-saved 1m ago" | `PUT` full-update có sẵn; **không có trạng thái DRAFT** — `POST` tạo ngay `OPEN` | **thiếu vòng đời**; auto-save = `PUT` theo bước, mốc là `updatedAt` |
| Submit for Admin Review | `JobStatus` chỉ `OPEN`/`CLOSED`; `PATCH /status` cho đặt tùy ý | **thiếu** `DRAFT`, `PENDING_APPROVAL`, submit/review |
| Duyệt / từ chối (kèm lý do) | — | **thiếu toàn bộ**; audit chỉ có bảng `admin_audit_log` của 001 |
| AI Screening (toggle/track/score 50–95/focus) | — | **thiếu toàn bộ** (C-4: chỉ lưu) |
| AI Job Optimizer (score/bias/forecast/Rephrase) | — | **không làm** (C-4) — không có AI engine, không chế số |
| REQ-2024-884 | chỉ `id` số | FE render `REQ-{năm}-{id}`; không thêm cột (precedent USR-4091) |
| Search public chỉ thấy job đã duyệt | `searchOpenJobs(…, OPEN)` | **không còn gì** — draft tự động không lộ ở list |
| `GET /jobs/{id}` | trả **mọi status cho mọi người** | **lỗ hổng lộ nháp** — FR-007 vá: `!= OPEN` chỉ owner/admin, người khác 404 |

## Technical Context

- Stack không đổi: Java 17, Spring Boot 3.2.5, JPA `ddl-auto: update` (Flyway tắt —
  `application.yml`), JWT + `@PreAuthorize`, envelope `ApiResponse`.
- Cột mới **đều nullable** để ddl-auto thêm được mà không alter cột có sẵn (bài học
  `known-gaps.md` mục 1; ddl-auto không sửa nullability cột cũ — vì vậy **không** nới
  `companyName`, giữ `@NotBlank` như hợp đồng cũ).
- `admin_audit_log` đã có `targetUserId` nullable với comment "nullable for non-user actions" +
  `detail` TEXT ⇒ review job ghi vào được mà **không sửa schema audit**.
- Đo thật không cần Render: DB nhánh local (Laragon 3306) hoặc container + harness như 001/002;
  `mvn -B clean test` qua công thức Docker đã kiểm chứng.

## Scope decision — 3 lô (C-1..C-4 đã chốt 2026-10-20)

### Lô A — schema + wizard lưu được (cột nullable, chưa đụng vòng đời duyệt)

| # | Việc | File |
|---|---|---|
| A1 | `JobStatus` thêm `DRAFT`, `PENDING_APPROVAL` (giữ `OPEN`, `CLOSED` — chỉ thêm, không đổi nghĩa) | `model/enums/JobStatus.java` |
| A2 | Enum mới `WorkplaceModel {ONSITE, HYBRID, REMOTE}` + `AiScreeningTrack {LIVE_CODING, SYSTEM_DESIGN, ASYNC_TECHNICAL, CULTURAL_FIT}` (tập đóng theo precedent `AuditAction`; nhãn tiếng Anh trên UI do FE map) | `model/enums/` (2 file mới) |
| A3 | `JobDescription` + 14 cột **nullable**: `department`, `experienceLevel`, `employmentType`, `workplaceModel`, `timezoneRequirement`, `salaryCurrency`, `salaryMin`, `salaryMax` (Long), `equityBand`, `annualBonus`, `aiScreeningEnabled` (Boolean), `aiScreeningTrack`, `aiScreeningPassScore` (Integer), `aiQuestionFocusAreas` (TEXT comma-string như `requiredSkills`) + `reviewNote` (TEXT) | `model/entity/JobDescription.java` |
| A4 | `JobRequest` + 13 field tùy chọn kèm validation (`@Size`, `@Min(50) @Max(95)` pass score); **không** đụng 2 field `@NotBlank` cũ. `JobDetailResponse` + đủ field mới + `reviewNote` | `model/dto/request/JobRequest.java`, `model/dto/response/JobDetailResponse.java` |
| A5 | Service validate chặn lúc ghi (FR-003): `salaryMin ≤ salaryMax`; bật screening ⇒ track + pass score bắt buộc; enum binding lỗi ⇒ 400 (handler `TypeMismatch`/`HttpMessageNotReadable` của 001 đã có). `POST` tạo **DRAFT** thay `OPEN`, message "Đã lưu bản nháp tin tuyển dụng" | `service/impl/JobServiceImpl.java` |
| A6 | Test Lô A: POST ⇒ DRAFT + SQL thấy cột mới; public list không thấy draft; pass score 96 ⇒ 400; min>max ⇒ 400; hồi quy job `OPEN` cũ vẫn đúng (AC-1, AC-2, AC-6, AC-8) | test integration mới |

Rủi ro A5: `POST /jobs` **đổi hành vi** (OPEN → DRAFT) — hợp đồng có chủ đích, phải đối chiếu với
FE hiện có trước khi merge (R-1). Đây là cái giá của C-1: một con đường duy nhất lên `OPEN` là
submit + duyệt.

### Lô B — vòng đời duyệt (theo sau A)

| # | Việc | Ghi chú hợp đồng |
|---|---|---|
| B1 | Guard trạng thái ở `PUT` + `PATCH /status`: `PUT` khi `PENDING_APPROVAL` ⇒ 400; `/status` chỉ `OPEN↔CLOSED` khi đang `OPEN/CLOSED`, còn lại ⇒ 400 (FR-006) | chặn lúc ghi, không để tự do đi vòng qua duyệt |
| B2 | `PATCH /jobs/{id}/submit` — owner (`findOwnedJobOrThrow` có sẵn), chỉ từ `DRAFT` ⇒ `PENDING_APPROVAL`; role như hiện tại (INTERVIEWER/ADMIN/STAFF — C-2) | message "Đã gửi duyệt tin tuyển dụng"; submit từ trạng thái khác ⇒ 400 |
| B3 | `PATCH /jobs/{id}/review` (ADMIN/STAFF) + `JobReviewRequest {approved, reason @Size(500)}`: duyệt ⇒ `OPEN` + xóa `reviewNote`; từ chối ⇒ **reason bắt buộc** (400 nếu thiếu) ⇒ về `DRAFT` + lưu `reviewNote`; chỉ từ `PENDING_APPROVAL` | `reviewNote` không bao giờ lộ public (job `OPEN` có note NULL; job có note là `DRAFT` — FR-007 che) |
| B4 | Audit review: action mới `APPROVE_JOB`, `REJECT_JOB` vào `AuditAction`; ghi 1 dòng `admin_audit_log` — `adminId` người thật (không `0`/`system`), `targetUserId = null` (cột cho phép non-user), `detail` chứa `job #id + tiêu đề`, `succeeded/reason` như hợp đồng 001 | **không** sửa schema audit |
| B5 | Hardening `GET /jobs/{id}`: status `!= OPEN` ⇒ chỉ owner/admin, người khác **404** (FR-007 — nháp/chờ duyệt mới xuất hiện nên phải vá luôn, không để lộ) | anonymous/ROLE_USER: 404; owner/admin: 200 |
| B6 | Test Lô B: toàn bộ AC-3..AC-7 + submit/review từ sai trạng thái ⇒ 400 + audit đúng 1 dòng `admin_id` thật | test integration |

### Lô C — KHÔNG làm kỳ này (ghi rõ để FE khỏi chờ)

- **AI Job Optimizer / bias flags / applicant forecast / AI Rephrase** (C-4): không có AI engine,
  không chế số giả. FE tự ẩn cụm này hoặc để tĩnh.
- **Màn Job Approvals & Moderation** (KPI, bảng duyệt, filter): feature 004; 003 chỉ có review
  đơn job. `adminListJobs` vẫn chưa expose.
- **REQ code, Hiring Manager chọn người, Preview, auto-save endpoint riêng**: out of scope
  (lý do ở spec).
- **Bulk review, webhook/thông báo cho employer khi bị từ chối**: không có nền tảng notification
  nào; employer thấy lý do qua `GET /{id}` (reviewNote).

## Architecture Decisions

- **D1 — một con đường lên `OPEN`**: `POST` không còn tạo `OPEN`; duyệt là cửa duy nhất. Hết
  trạng thái "tạo xong thành OPEN rồi mới nghĩ xem ai duyệt" — đây chính là hợp đồng mà nút
  "Submit for Admin Review" của mockup hàm ý.
- **D2 — mở rộng bằng cột nullable qua ddl-auto**, không migration Flyway, không alter cột có sẵn
  (known-gaps mục 1). `companyName` giữ `@NotBlank` — nới nullability là alter, ddl-auto không làm được.
- **D3 — DRAFT là giá trị enum trên `job_descriptions.status`, không bảng revisions/autosave**:
  wizard nạp lại mọi dữ liệu qua `GET /{id}` có sẵn; "auto-save" = `PUT`, mốc = `updatedAt`.
  Không thêm bảng, không thêm endpoint poll.
- **D4 — cấu hình AI screening là cột trên job, không bảng con** (quan hệ 1-1, không truy vấn
  riêng); focus areas comma-string theo precedent `requiredSkills`.
- **D5 — review tái dùng `admin_audit_log`** (B4) thay vì bảng job-review riêng: audit của 001 đã
  đo kỹ (`admin_id NOT NULL`, `succeeded/reason`), cột `targetUserId` có comment "non-user actions".
- **D6 — `salaryRange` giữ nguyên làm chuỗi hiển thị**; cột cấu trúc mới là nguồn dữ liệu form
  (C-3). FE dựng lại chuỗi khi cần.

## Constitution Check

| Principle | Gate | Kết quả | Ghi chú |
|---|---|---|---|
| I. Hợp đồng API trước, code sau | Hợp đồng endpoint nằm trong spec/contracts | PASS | `contracts/job-api.md` kèm thư mục này, trước code |
| II. Số đo thật | Verify bằng curl/SQL trên app chạy thật | PASS | AC-1..AC-8 là phép đo; kết quả ghi `quickstart-measure.md` khi build |
| III. Chặn lúc ghi | Rule trả 400/422 ở request ghi | PASS | FR-003 (field) + FR-002/004/005/006 (trạng thái) |
| IV. Audit thật | Không admin giả; không thao tác vô nghĩa | PASS | B4: `admin_id` thật; mockup không đòi actor hệ thống |
| V. Hợp đồng lỗi nhất quán | Một mã lỗi cho một tình huống | PASS | sai trạng thái = 400; sai quyền = 403; không tìm thấy (public) = 404 |
| VI. Phòng vệ nhiều tầng | URL rule + `@PreAuthorize` | PASS | `/api/v1/jobs/**` vẫn nằm sau JWT rule hiện có, review thêm `hasAnyRole(ADMIN,STAFF)` |
| VII. Phạm vi tối thiểu | Không đụng thứ không cần | PASS | Lô C là chỗ cắt; không role mới, không bảng mới, không migration |
| VIII. Kiểm chứng trước workaround | Đọc code trước khi chốt | PASS | matrix trên đọc trực tiếp `JobController/ServiceImpl/DTO`; C-1..C-4 hỏi trước khi viết |

## Testing Strategy

- **Integration (Testcontainers)**: test lifecycle mới cho job — POST→DRAFT, PUT field mới, submit,
  approve/reject, guard trạng thái, 403/401, GET hardening 404. Chạy được local theo công thức
  Docker đã kiểm chứng (mount docker.sock + `TESTCONTAINERS_HOST_OVERRIDE`).
- **Hồi quy**: public search vẫn chỉ `OPEN`; `JobSummaryResponse`/`JobDetailResponse` không mất
  field; các endpoint job cũ (DELETE, suggestions, my-jobs) hành vi không đổi; changestatus cũ
  với `OPEN↔CLOSED` vẫn chạy.
- **Đo thật**: curl từng endpoint ghi số vào `quickstart-measure.md` (Constitution II — không ghi
  "đã chạy").
- **CI**: `mvn -B clean test` trên Actions là cổng chốt trước merge `dev`; `docker build` sạch
  trước push (luật làm việc).

## Phases (thứ tự, không ước lượng thời gian)

1. **Lô A** (A1→A6): schema + DTO + POST-DRAFT → đo AC-1/AC-2/AC-6 phần field → merge độc lập được
   nếu FE chưa kịp (job mới là draft, không vỡ list public).
2. **Lô B** (B1→B6): vòng đời — submit/review/audit/hardening → đo AC-3..AC-7.
3. **Rà hợp đồng FE**: `POST` đổi hành vi (R-1), `JobDetailResponse` thêm field, 2 endpoint mới —
   đối chiếu FE wizard trước khi merge.
4. **CI**: `mvn -B clean test` xanh + `docker build` sạch → push (chỉ khi anh đồng ý) → merge `dev`.

## Open risks

- **R-1 (chấp nhận có chủ đích theo C-1)**: `POST /jobs` đổi từ "tạo OPEN" sang "tạo DRAFT". FE
  hiện có nếu còn gọi POST để đăng job công khai sẽ thấy job không lên danh sách. Phải đối chiếu
  FE trước merge; đây là điểm cần báo anh khi gửi kế hoạch.
- **R-2**: wizard không có ô `companyName` nhưng DTO bắt buộc (C-3 không nới nullability). FE phải
  gửi giá trị (từ hồ sơ tổ chức); quên gửi ⇒ 400 "Tên công ty không được để trống" — message cũ
  giữ nguyên.
- **R-3**: `aiQuestionFocusAreas`/`requiredSkills` comma-string không phân biệt mục chứa dấu phẩy
  — chấp nhận theo precedent; FE chọn từ checklist có kiểm soát, không gõ tự do.
- **R-4**: `experienceLevel`/`employmentType`/`department` lưu String tự do (không enum) để khỏi
  khóa nội dung theo ngành; đổi lại lọc/sort theo các field này (nếu làm sau) phải chuẩn hóa lúc đó.
- **R-5**: `PATCH /status` cũ từng cho đặt `OPEN` tự do; sau khi siết, mọi kịch bản "đẩy job lên
  OPEN thủ công" (nếu FE/Postman đang dùng) sẽ 400 — đúng thiết kế, nhưng phải ghi vào hợp đồng.

## Complexity Tracking

Không thêm dependency, không migration Flyway, không bảng mới, không role mới, không sửa schema
`users`/`admin_audit_log`. Nặng nhất là B1+B3 (guard + transition) và A5 (validate chặn lúc ghi)
— mỗi cái có test riêng trong Testing Strategy.
