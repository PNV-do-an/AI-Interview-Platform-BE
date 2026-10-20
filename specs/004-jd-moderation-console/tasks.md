# Tasks: Console kiểm duyệt JD (004)

**Input**: `spec.md` + `plan.md`. `M-x` = phép đo thật phải ghi vào `quickstart-measure.md`.

## Lô 1 — schema + hiệu ứng ghi

- [ ] T001 [P] Entity `model/entity/JobModerationRecord.java` (`job_moderation_records`):
      `@ManyToOne job` (nullable=false), `actor` (`@ManyToOne User`, nullable=false),
      `actorRole` enum `ModerationActorRole {EMPLOYER, ADMIN, STAFF}`,
      `action` enum `ModerationAction {SUBMITTED, RESUBMITTED, APPROVED, REJECTED}`,
      `note` TEXT nullable, `createdAt` `@PrePersist`. Index `(job_id, created_at)`.
- [ ] T002 [P] `JobDescription`: thêm `submittedAt`, `decidedAt`, `aiSafetyScore`
      (Integer) — nullable hết (FR-005).
- [ ] T003 [P] `JobModerationRecordRepository`: `findByJobIdOrderByCreatedAtDesc`,
      `search(decision, from, to, moderatorId, keyword, pageable)` (bản ghi chỉ
      APPROVED/REJECTED), `countStats()` trả tổng/approved/rejected, `avgProcessing()`.
- [ ] T004 `submitForReview`: set `submittedAt=now`; ghi record SUBMITTED; nếu JD từng có
      record REJECTED thì action = RESUBMITTED (đếm theo record tồn tại, không cột mới).
      Xóa dead code `adminListJobs` (D3).
- [ ] T005 `reviewJob`: set `decidedAt=now`; ghi record APPROVED (note=null) / REJECTED
      (note=reason, `submittedAt=null` để tin sửa xong phải submit lại mới có mốc mới);
      mọi thứ khác của 003 giữ nguyên (status, reviewNote, audit, message).
- [ ] T006 [P] Test Lô 1: submit ⇒ 1 record SUBMITTED actor EMPLOYER; reject ⇒ record
      REJECTED + `decidedAt` + tin DRAFT + `submittedAt` null; resubmit ⇒ RESUBMITTED;
      approve ⇒ OPEN + record APPROVED; luồng 003 hồi quy message/permission nguyên vẹn.

## Lô 2 — console API

- [ ] T007 [P] DTO: `ModerationJobResponse` (E1), `ModerationRecordResponse`,
      `JobModerationHistoryResponse`, `ModerationHistoryStatsResponse` (E4).
- [ ] T008 `JobDescriptionRepository`: query E1 (`status` + keyword trên
      title/companyName/employer.email + `deletedAt IS NULL`, sort `createdAt DESC`);
      query E3 join record + job + employer + moderator, filter như hợp đồng.
- [ ] T009 `JobController` (hoặc controller mới `AdminJobModerationController` map
      `/api/v1/admin/jobs/moderation`): E1, E2 (detail = `JobDetailResponse` + 3 field
      mới + timeline), E3, E4. Console không tự check quyền thêm — dựa URL rule ADMIN
      (như `AdminUserController`, FR-008).
- [ ] T010 [P] Test Lô 2 (AC-1..AC-6): queue khớp SQL 3 bộ lọc; detail 404 với tin xóa
      mềm; history lọc decision/from/to/moderatorId; stats khớp đếm SQL; STAFF 403 +
      anonymous 401 trên 4 endpoint console; `status=FOO` ⇒ 400 kèm allowedValues;
      search `%` không 500; `PATCH review` STAFF vẫn 200 (R-2).

## Polish + cổng chốt

- [ ] T011 [P] Scan tiếng Việt message mới (luật làm việc: không Hán, không từ EN lọt câu).
- [ ] T012 `mvn -B test` qua Docker (công thức project-run-maven-tests-docker) — xanh cả
      118 test cũ + test mới; `docker build` sạch.
- [ ] T013 Ghi `quickstart-measure.md` (AC-1..AC-6, M-1..M-n) — cấm ghi "đã chạy" không số.
- [ ] T014 Push branch — **chỉ khi anh Huy đồng ý**.

## Dependencies & order

T001→T003→T004/T005 (record phải tồn tại trước khi ghi). T007/T008 song song với Lô 1
được nhưng E1..E4 chỉ code sau khi T005 xong (E2 đọc timeline). Lô 2 test cần dữ liệu từ
Lô 1 → chạy sau.
