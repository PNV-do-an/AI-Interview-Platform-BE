# Tasks: BE wizard "Create Job Posting"

**Input**: `spec.md` + `plan.md` + `contracts/job-api.md` trong thư mục này. **Clarify đã chốt 4/4**
(C-1..C-4, 2026-10-20). Không thêm role, không migration Flyway, không bảng mới, không thêm dependency.

Ký hiệu: `[P]` = chạy song song được; `[A]`/`[B]` = theo lô; `M-x` = phép đo thật phải ghi số vào
`quickstart-measure.md` của thư mục này (Constitution II — cấm ghi "đã chạy").

## Lô A — schema + wizard lưu được

- [x] T001 [A] `model/enums/JobStatus.java`: thêm `DRAFT`, `PENDING_APPROVAL` (giữ nguyên 2 giá trị
      cũ, không đổi nghĩa — hồi quy AC-8).
- [x] T002 [P] [A] `model/enums/WorkplaceModel.java` (ONSITE/HYBRID/REMOTE) +
      `model/enums/AiScreeningTrack.java` (LIVE_CODING/SYSTEM_DESIGN/ASYNC_TECHNICAL/CULTURAL_FIT) —
      2 enum mới, tập đóng (A2).
- [x] T003 [A] `JobDescription`: + 15 cột **nullable** (A3: department, experienceLevel,
      employmentType, workplaceModel, timezoneRequirement, salaryCurrency, salaryMin, salaryMax,
      equityBand, annualBonus, aiScreeningEnabled, aiScreeningTrack, aiScreeningPassScore,
      aiQuestionFocusAreas, reviewNote). **Chỉ thêm cột**, không đụng cột có sẵn (R-2).
- [x] T004 [A] `JobRequest`: + 13 field tùy chọn + validation (@Size, @Min(50) @Max(95) pass score);
      **không** đụng `@NotBlank` của title/companyName (R-2). `JobDetailResponse`: + đủ field mới +
      `reviewNote`; `from()` map đầy đủ (A4).
- [x] T005 [A] `JobServiceImpl.createJob`: builder gán field mới, status = **DRAFT**; validate chặn
      lúc ghi (A5): salaryMin>max ⇒ 400; enabled=true thiếu track/passScore ⇒ 400. `updateJob` gán
      thêm field mới + cùng bộ validate. Message POST: "Đã lưu bản nháp tin tuyển dụng".
- [x] T006 [A] Test Lô A: POSTwizard ⇒ DRAFT + SQL cột mới (AC-1/AC-2); passScore 96 ⇒ 400 (AC-6);
      salaryMin>max ⇒ 400 (AC-6); public list không thấy draft (AC-1); job OPEN cũ vẫn hiển thị đúng
      (AC-8). Ghi M-1..M-6.

## Lô B — vòng đời duyệt (theo sau A)

- [x] T007 [B] Guard trạng thái (B1): `updateJob` khi `PENDING_APPROVAL` ⇒ 400; `changeStatus`
      whitelist `OPEN↔CLOSED` khi đang `OPEN/CLOSED`, còn lại ⇒ 400 (FR-006). Test: `PATCH /status`
      từ DRAFT ⇒ 400 (AC-6), `OPEN→CLOSED→OPEN` vẫn chạy (hồi quy).
- [x] T008 [B] `PATCH /jobs/{id}/submit` (B2): owner qua `findOwnedJobOrThrow`, chỉ từ `DRAFT`;
      message "Đã gửi duyệt tin tuyển dụng". Test: submit ⇒ `PENDING_APPROVAL` (AC-3); submit từ
      OPEN ⇒ 400; submit lần 2 ⇒ 400.
- [x] T009 [P] [B] `model/dto/request/JobReviewRequest.java`: `approved @NotNull`, `reason
      @Size(500)` (B3).
- [x] T010 [B] `PATCH /jobs/{id}/review` (B3): ADMIN/STAFF; chỉ từ `PENDING_APPROVAL`; duyệt ⇒
      `OPEN` + `reviewNote=null`; từ chối ⇒ reason bắt buộc (400) ⇒ `DRAFT` + lưu `reviewNote`.
      Test: AC-4 (duyệt ⇒ OPEN + public thấy + anonymous GET 200), AC-5 (từ chối thiếu reason 400;
      đủ reason ⇒ DRAFT + note; submit lại + duyệt ⇒ note NULL).
- [x] T011 [B] Audit review (B4): `AuditAction` + `APPROVE_JOB`, `REJECT_JOB`; ghi 1 dòng
      `admin_audit_log` (`adminId` thật, `targetUserId=null`, `detail` "job #id: title"). Test: đúng
      1 dòng/1 lần review, `admin_id` không `0`/`system` (AC-7).
- [x] T012 [B] Hardening `GET /jobs/{id}` (B5): `status != OPEN` ⇒ chỉ owner/admin, người khác 404
      (FR-007). Test: anonymous GET draft ⇒ 404 (AC-7), ROLE_USER GET draft ⇒ 404, owner ⇒ 200.
- [x] T013 [B] Test Lô B còn lại: ROLE_USER gọi POST/PUT/submit/review ⇒ 403, anonymous ⇒ 401
      (AC-7); không tổ hợp input nào 500 (AC-6/AC-9 ma trận: id lạ, enum lạ, `%`). Ghi M-7..M-12.

## Polish

- [x] T014 [P] Scan tiếng Việt toàn bộ message mới (ký tự Hán + từ EN lọt câu) theo luật làm việc.
- [x] T015 [P] Ghi `known-gaps.md`: POST đổi hành vi OPEN→DRAFT (R-1); AI Optimizer mockup đòi
      nhưng BE không có AI engine (C-4); focus areas comma-string không Escape dấu phẩy (R-3).
- [x] T016 `mvn -B clean test` qua công thức Docker **xanh cả bộ**, rồi `docker build` sạch.
- [x] T017 Push branch + chờ Actions — **chỉ khi anh Huy đồng ý**; đối chiếu FE wizard (R-1) trước
      khi merge `dev`.

## Dependencies & order

T001→T003→T004→T005 (cùng chuỗi entity/DTO/service, không parallel). T002 song song được.
Lô B chỉ bắt đầu sau khi Lô A đo xong AC-1/AC-2. T008→T010→T011 tuần tự (chung service),
T009 song song trước T010. T012 độc lập với T008–T011 nhưng cần DRAFT tồn tại (T005).

## Definition of done cho từng task

Số đo thật (HTTP code + output SQL) ghi vào `quickstart-measure.md`; test integration chạy trong
container Testcontainers; không có ô nào của ma trận input trả 500.
