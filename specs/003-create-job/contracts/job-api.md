# Hợp đồng API — Jobs (wizard Create Job Posting + vòng đời duyệt)

**Base**: `/api/v1/jobs` · Envelope: `ApiResponse` như hiện có ·xac thực JWT, message nghiệp vụ tiếng Việt.
**Trạng thái job**: `DRAFT | PENDING_APPROVAL | OPEN | CLOSED` (thêm 2 giá trị đầu ở 003).

## Endpoint hiện có — giữ nguyên

| Method | Path | Who | Ghi chú 003 |
|---|---|---|---|
| GET | `/jobs?page&size&search` | public | chỉ trả `OPEN` (không đổi) |
| GET | `/jobs/my-jobs` | INTERVIEWER/ADMIN/STAFF | giờ thấy cả `DRAFT`/`PENDING_APPROVAL` của mình |
| DELETE | `/jobs/{id}` | owner/admin | không đổi |
| GET | `/jobs/{id}/suggestions` | owner/admin | không đổi |

## Endpoint hiện có — ĐỔI HÀNH VI

| Method | Path | Đổi gì |
|---|---|---|
| POST | `/jobs` | tạo **DRAFT** (trước đây tạo OPEN). Message: "Đã lưu bản nháp tin tuyển dụng". Body `JobRequest`: bắt buộc `title`, `companyName` (như cũ); còn lại tùy chọn — **thêm 13 field mới** (bảng dưới) |
| PUT | `/jobs/{id}` | body như POST; **400 khi job `PENDING_APPROVAL`** ("Đang chờ duyệt, không thể sửa"); owner/admin như cũ |
| PATCH | `/jobs/{id}/status?status=` | **chỉ `OPEN ↔ CLOSED`** khi đang `OPEN/CLOSED`; từ `DRAFT`/`PENDING_APPROVAL` hoặc truyền giá trị khác ⇒ 400 "Trạng thái không hợp lệ cho tin tuyển dụng này" |
| GET | `/jobs/{id}` | job `!= OPEN`: chỉ owner/admin ⇒ 200; người khác (kể cả anonymous) ⇒ **404** |

## Endpoint MỚI

| Method | Path | Who | Body | 200 | Lỗi |
|---|---|---|---|---|---|
| PATCH | `/jobs/{id}/submit` | owner (INTERVIEWER/ADMIN/STAFF) | — | `DRAFT → PENDING_APPROVAL`, "Đã gửi duyệt tin tuyển dụng" | 400 nếu job không ở `DRAFT`; 403 nếu không phải owner |
| PATCH | `/jobs/{id}/review` | ADMIN/STAFF | `{approved: boolean, reason?: string ≤500}` | duyệt: `→ OPEN`, "Tin tuyển dụng đã được duyệt"; từ chối: `→ DRAFT` + `reviewNote`, "Tin tuyển dụng đã bị từ chối" | 400 nếu không ở `PENDING_APPROVAL`, hoặc từ chối mà thiếu `reason`; 403 nếu không ADMIN/STAFF |

Mỗi lần review ghi 1 dòng `admin_audit_log` (`APPROVE_JOB`/`REJECT_JOB`, `admin_id` người thật,
`targetUserId = null`, `detail` chứa job id + tiêu đề).

## Field mới trong `JobRequest` / `JobDetailResponse` (đều tùy chọn, chặn lúc ghi)

| Field | Kiểu | Rule |
|---|---|---|
| `department`, `experienceLevel`, `employmentType` | String | `@Size(max 100)` |
| `workplaceModel` | enum `ONSITE \| HYBRID \| REMOTE` | giá trị lạ ⇒ 400 |
| `timezoneRequirement` | String | `@Size(max 100)` |
| `salaryCurrency` | String (vd "USD") | `@Size(max 8)` |
| `salaryMin`, `salaryMax` | Long | min ≤ max ⇒ ngược lại 400 |
| `equityBand`, `annualBonus` | String | `@Size(max 200)` |
| `aiScreeningEnabled` | Boolean | `true` ⇒ bắt buộc `aiScreeningTrack` + `aiScreeningPassScore` |
| `aiScreeningTrack` | enum `LIVE_CODING \| SYSTEM_DESIGN \| ASYNC_TECHNICAL \| CULTURAL_FIT` | giá trị lạ ⇒ 400 |
| `aiScreeningPassScore` | Integer | 50–95 (ngoài khoảng ⇒ 400) |
| `aiQuestionFocusAreas` | TEXT comma-string | như `requiredSkills` |
| `reviewNote` | String | chỉ đọc (response), do review endpoint ghi |

Giữ nguyên: `salaryRange` (chuỗi hiển thị), `requiredSkills`, `description`, `requirements`,
`position`, `location`, `companyName`, `title`. `JobSummaryResponse` **không** thêm field.
