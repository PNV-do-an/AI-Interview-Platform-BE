# Bộ đo 003 — wizard Create Job Posting (đo 2026-10-20)

## Môi trường đo

- Toàn bộ phép đo dưới đây chạy trong container maven với Testcontainers MySQL 8.0 thật
  (công thức Docker trong bộ nhớ dự án / `specs/001-admin-user-management/quickstart.md`):
  mount docker.sock + `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`
  + `TESTCONTAINERS_RYUK_DISABLED=true`.
- `POST/PUT/PATCH/GET` đo qua MockMvc trong `JobControllerIntegrationTest` (16 test),
  assert trực tiếp cả HTTP code lẫn SQL qua `JdbcTemplate` — không có test nào chỉ dừng ở HTTP.

## Kết quả tổng (lần chạy chốt trước khi push)

`mvn -B -o test` full suite — **Tests run: 118, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS**:

| Class | Tests | Thời gian |
|---|---|---|
| `JobControllerIntegrationTest` (mới, 003) | 16/16 | 38.7s |
| `AdminUserControllerIntegrationTest` (hồi quy 001+002) | 63/63 | 151.9s |
| `PersistenceTest` (hồi quy register/verify) | 12/12 | 167.5s |
| 6 class unit còn lại | 27/27 | < 10s |

## Phép đo theo AC (tên test = bằng chứng)

| AC | Phép đo trong `JobControllerIntegrationTest` | Kết quả |
|---|---|---|
| AC-1 | `createDraft_savesDraft_hiddenFromPublic_visibleInMyJobs` | POST → 200 + SQL `status=DRAFT`; public list `content.length=0`; my-jobs thấy 1 dòng `DRAFT` |
| AC-2 | `updateWizardFields_persistsAllNewColumns` | PUT wizard → SQL: `department=Platform Infrastructure`, `workplace_model=HYBRID`, `salary_currency=USD`, `salary_min=165000`, `salary_max=210000`, `ai_screening_enabled=true`, `ai_screening_track=LIVE_CODING`, `ai_screening_pass_score=80`, focus areas giữ nguyên chuỗi |
| AC-3 | `submit_fromDraft_movesToPending_twiceFails`, `updateJob_whilePendingApproval_returns400` | submit → `PENDING_APPROVAL`; submit lần 2 → 400 "Chỉ bản nháp mới gửi duyệt được"; PUT khi chờ duyệt → 400 "Tin đang chờ duyệt, không thể sửa" |
| AC-4 | `review_approve_publishesJob_publiclyVisible` | duyệt → `OPEN`; public list thấy đúng tiêu đề; anonymous `GET /{id}` → 200 |
| AC-5 | `review_reject_requiresReason_backToDraft_resubmittable` | thiếu reason → 400 "Vui lòng nêu lý do từ chối"; đủ reason → `DRAFT` + `reviewNote`; sửa + submit + duyệt lại → `reviewNote` biến mất khỏi response (NON_NULL) |
| AC-6 | `createJob_passScoreOutOfRange_returns400`, `updateJob_salaryMinGreaterThanMax_returns400`, `createJob_unknownWorkplaceModel_returns400`, `changeStatus_draftBlocked_openClosedStillWorks` | passScore 96 → 400 "Dữ liệu không hợp lệ" và **không lưu** (`jobRepository.count()=0`); min 300k > max 210k → 400 đúng message; `workplaceModel: "ONSITEE"` → 400; `PATCH /status` từ DRAFT → 400 "Trạng thái không hợp lệ cho tin tuyển dụng này"; OPEN→CLOSED→OPEN vẫn chạy như cũ |
| AC-7 | `getJobDetail_draft_hiddenFromStrangers_ownerSees`, `writeEndpoints_forbiddenForCandidateRole`, `writeEndpoints_unauthenticated_returns401`, `review_writesAuditWithRealAdmin`, `review_wrongInitialState_returns400` | anonymous + ROLE_USER xem nháp → 404, owner → 200; ROLE_USER 4 endpoint ghi → 403; anonymous → 401; review sai trạng thái → 400; audit đúng 1 dòng `APPROVE_JOB`, `adminId` = id admin thật, `targetUserId=null`, detail chứa `job #id` |
| AC-8 | `regression_openJobDetail_stillPublic` + 63 test admin + 12 test persistence | GET job OPEN anonymous → 200; không vỡ gì của 001/002 |

## Sai lệch so với plan (có chủ đích)

- **Lần chạy đầu FAIL 13/16**: `createJob` mất 8 field cũ khi dựng builder → INSERT
  `company_name = NULL` văng `Column 'company_name' cannot be null` (stack thật ở log container,
  dòng `Caused by: java.sql.SQLIntegrityConstraintViolationException`). Sửa bằng cách gộp mọi field
  vào `applyWizardFields(job, request)` dùng chung cho create + update. Bài học:(MockMvc 500 không
  hiện trong surefire report client-side — phải đọc log server trong output container.
- Test assert SQL `ai_screening_enabled` lần đầu cast `Number` → `ClassCastException` vì JDBC trả
  `Boolean` cho TINYINT(1); sửa thành so sánh `Boolean.TRUE.equals(...)`.

## Chưa đo (nằm ngoài kỳ này)

- Endpoint nào cũng chưa đo trên Render staging — deploy theo superrepo, sau merge `dev`.
- Màn "Job Approvals & Moderation" của admin (feature 004) chưa có gì để đo.
