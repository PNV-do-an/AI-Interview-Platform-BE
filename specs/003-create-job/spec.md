# Feature Specification: Wizard "Create Job Posting" — phần BE hoàn thiện tạo job

**Feature Branch**: `feature/create-job`
**Created**: 2026-10-20 · **Status**: Draft · **Input**: mockup HTML "Create Job Posting"
(wizard 4 bước — Basic Info / Role & Responsibilities / Compensation / AI Screening, gửi 2026-10-20)
+ code job hiện có (`JobController`, `JobDescription`, `JobStatus`).

## Bối cảnh

BE đã có CRUD job cơ bản (`POST/PUT/DELETE /api/v1/jobs`, đổi trạng thái, my-jobs, suggestions)
nhưng `POST` tạo job **ngay `OPEN`** và `JobRequest` chỉ có 8 field text. Mockup mô tả một wizard
4 bước với Save Draft + auto-save, Submit for Admin Review, cụm cấu hình AI screening và các
field cấu trúc (lương, workplace model…). Sidebar admin còn có màn "Job Approvals & Moderation"
— đó là feature riêng, không nằm ở 003 (giống cách 002 đã cắt Job Approvals).

## User Stories

**US-01** Employer tạo bản nháp từ wizard: chỉ cần tiêu đề (kèm companyName theo hợp đồng hiện có,
xem Clarify C-3) là lưu được; các bước sau save tiếp mà không mất dữ liệu; quay lại vẫn thấy đủ
nội dung đã nhập (`updatedAt` là mốc "Auto-saved … ago" FE hiển thị).
**US-02** Employer hoàn thành 4 bước → bấm "Submit for Admin Review" → job chuyển sang trạng thái
chờ duyệt, **không** hiện với ứng viên công khai.
**US-03** Admin/Staff duyệt: duyệt xong job `OPEN` hiện công khai trong danh sách; từ chối thì
**bắt buộc có lý do**, job về lại DRAFT kèm lý do, employer sửa được và submit lại được.
**US-04** Employer cấu hình AI screening cho job: bật/tắt, chọn track, ngưỡng đậu (50–95),
chọn focus areas (multi-select). Cấu hình này là **dữ liệu lưu theo job**, chưa chạy AI nào.
**US-05** Ứng viên công khai chỉ thấy job `OPEN` — cả ở danh sách lẫn vào thẳng URL job đang nháp/
chờ duyệt đều không thấy.
**US-06** Candidate (`ROLE_USER`) không gọi được bất kỳ endpoint ghi job nào.

## Functional Requirements

- **FR-001** `POST /api/v1/jobs` tạo **DRAFT** (không còn tạo `OPEN` — thay đổi hợp đồng có chủ đích,
  xem Clarify C-1). Validation như cũ: `title`, `companyName` bắt buộc; các field wizard còn lại
  tùy chọn. Response trả đủ field mới.
- **FR-002** `PUT /api/v1/jobs/{id}` cập nhật được mọi field wizard: department, experience level,
  employment type, workplace model, timezone, lương cấu trúc (currency/min/max), equity, bonus,
  tech-stack tags, mô tả rich-text, cấu hình AI screening. Chỉ owner (hoặc admin) sửa được;
  job đang `PENDING_APPROVAL` ⇒ **400** "đang chờ duyệt" (chặn lúc ghi).
- **FR-003** Mọi rule nghiệp vụ chặn **ngay ở request ghi** (Constitution III), không để lưu rồi lộ ở GET:
  workplace model / screening track chỉ nhận giá trị enum định sẵn; pass score trong 50–95;
  `salaryMin ≤ salaryMax`; bật screening ⇒ bắt buộc có track + pass score. Vi phạm ⇒ 400/422.
- **FR-004** `PATCH /api/v1/jobs/{id}/submit` (owner, chỉ từ `DRAFT`) ⇒ `PENDING_APPROVAL`.
  Submit từ trạng thái khác ⇒ 400.
- **FR-005** `PATCH /api/v1/jobs/{id}/review` (ADMIN/STAFF, chỉ từ `PENDING_APPROVAL`):
  duyệt ⇒ `OPEN` (xóa reviewNote); từ chối ⇒ **bắt buộc** `reason` ⇒ về `DRAFT` + lưu `reviewNote`.
  Mỗi lần duyệt/từ chối ghi **một dòng audit với admin người thật** (không `0`/`system` — R10 của 001).
- **FR-006** `PATCH /api/v1/jobs/{id}/status` (endpoint cũ) chỉ cho phép `OPEN ↔ CLOSED` khi job đang
  `OPEN/CLOSED`; gọi từ `DRAFT`/`PENDING_APPROVAL` hoặc truyền giá trị đó vào ⇒ 400
  (đường tắt qua đầu vòng đời duyệt phải bị chặn).
- **FR-007** Công khai chỉ thấy `OPEN`: danh sách public đã lọc sẵn (giữ nguyên); `GET /jobs/{id}`
  với job `!= OPEN` chỉ owner/admin xem được, người lạ ⇒ **404** (hiện đang lộ mọi status cho mọi
  người — vá trong phạm vi này vì nháp/chờ duyệt xuất hiện).
- **FR-008** Lương: giữ nguyên `salaryRange` (chuỗi hiển thị, FE đang đọc — C-3), thêm cột cấu trúc
  nullable riêng; response chỉ **thêm** field, không xóa field cũ.
- **FR-009** Không tổ hợp input nào (id lạ, enum lạ, `%` trong search, pass score 999…) trả 500.

## Out of scope (đề xuất, đã bám Clarify)

- **Màn "Job Approvals & Moderation" của admin** (KPI, bộ lọc, bảng): feature riêng 004; 003 chỉ
  cung cấp endpoint review đơn job. Danh sách job cho admin (`adminListJobs`) đã có ở service,
  sẽ expose khi làm feature đó.
- **AI Job Optimizer** (score 92, bias flags, applicant forecast, "AI Rephrase"): BE chưa có AI
  engine, **không chế số giả** (C-4). FE tự ẩn hoặc để tĩnh.
- **"Live Board Preview"** + nút **Preview**: render client-side, không cần endpoint.
- **Mã REQ-2024-884**: FE tự render `REQ-{năm}-{id}` từ `id` + `createdAt` (precedent "USR-4091"
  của 002 — không thêm cột `public_id`).
- **Hiring Manager chọn người khác**: hiring manager = employer tạo job (đã có `employerName` trong
  response); không thêm field chọn người.
- **Auto-save endpoint riêng**: FE dùng `PUT` sẵn có theo từng bước; "Auto-saved 1m ago" suy từ
  `updatedAt`. Không endpoint/polling mới.
- Không thêm role, không sửa schema `users`, không migration Flyway (ddl-auto vẫn tắt — thêm cột
  nullable qua ddl-auto như bài học known-gaps).

## Clarification — ĐÃ CHỐT 2026-10-20 (anh Huy trả lời 4/4 theo hướng tối thiểu)

- **C-1 Vòng đời**: **đủ vòng đời** `DRAFT → PENDING_APPROVAL → OPEN` (duyệt) hoặc về `DRAFT` kèm
  lý do (từ chối), **có endpoint duyệt tối thiểu** để job không kẹt; màn "Job Approvals &
  Moderation" đầy đủ là feature riêng.
- **C-2 Role**: **giữ nguyên** `INTERVIEWER/ADMIN/STAFF` như code đang chạy; **không** thêm
  `ROLE_EMPLOYER`. Nhãn "Employer Workspace" là chuyện hiển thị của FE.
- **C-3 Lương**: **thêm cột nullable mới** (currency/min/max/equity/bonus), **giữ nguyên
  `salaryRange`** — không phá hợp đồng `JobSummaryResponse`/`JobDetailResponse` đang có FE đọc.
  (Ghi chú: wizard không có ô companyName nhưng DTO giữ `@NotBlank` như hợp đồng cũ — FE gửi kèm
  giá trị từ hồ sơ tổ chức; **không** nới nullability cột DB vì ddl-auto không alter cột có sẵn.)
- **C-4 AI**: **chỉ lưu cấu hình** screening (toggle/track/pass score/focus areas) như dữ liệu job;
  Optimizer/forecast/AI Rephrase **không làm** — chưa có AI engine, không chế số giả.

## Acceptance criteria (đo được)

- **AC-1**: `POST` wizard ⇒ 200; SQL `job_descriptions.status` = `DRAFT`; `GET /api/v1/jobs` (public)
  **không** thấy job này; `GET /my-jobs` thấy.
- **AC-2**: `PUT` đủ field wizard ⇒ SQL thấy `salary_min=165000`, `workplace_model=HYBRID`,
  `ai_screening_pass_score=80`…; `updatedAt` tăng (nguồn "Auto-saved").
- **AC-3**: `PUT` khi `PENDING_APPROVAL` ⇒ 400; `submit` từ `DRAFT` ⇒ `PENDING_APPROVAL`;
  submit lần nữa ⇒ 400.
- **AC-4**: review **duyệt** ⇒ `OPEN`; job xuất hiện ở `GET /api/v1/jobs` public; anonymous
  `GET /{id}` ⇒ 200.
- **AC-5**: review **từ chối thiếu reason** ⇒ 400; đủ reason ⇒ về `DRAFT` + `review_note` có nội dung;
  submit lại được; duyệt lần sau ⇒ `review_note` về NULL.
- **AC-6**: chặn lúc ghi: `passScore=96` ⇒ 400; `salaryMin > salaryMax` ⇒ 400;
  `workplaceModel` lạ ⇒ 400; `PATCH /status` từ `DRAFT` ⇒ 400.
- **AC-7**: anonymous `GET /{id}` khi job `DRAFT`/`PENDING_APPROVAL` ⇒ 404; `ROLE_USER` gọi
  POST/PUT/submit/review ⇒ 403; review ghi đúng 1 dòng `admin_audit_log` với `admin_id` thật.
- **AC-8**: hồi quy — `GET /api/v1/jobs` vẫn chỉ trả `OPEN`, các job `OPEN` cũ hiển thị đúng,
  `JobSummaryResponse` không mất field nào.
