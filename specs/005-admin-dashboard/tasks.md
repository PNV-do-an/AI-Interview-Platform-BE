# Tasks: Dashboard tổng hợp (005)

**Input**: `spec.md` + `plan.md`. `M-x` = phép đo thật ghi vào `quickstart-measure.md`.

## Tasks

- [ ] T001 [P] DTO `model/dto/response/DashboardResponse.java` (nested record theo hợp đồng
      trong plan.md — 5 nhóm, key đầy đủ).
- [ ] T002 Count query: `UserRepository` (total/active/pending/locked/deleted/byRole/
      newInPeriod), `JobDescriptionRepository` (total/byStatus/newInPeriod),
      `InterviewSessionRepository` (total/inPeriod/completedInPeriod/avgScore),
      `LoginAttemptRepository` (success/failed/distinct-email trong kỳ). Tất cả JPQL
      count/aggregate, không load entity (D2).
- [ ] T003 `AdminDashboardService` + `AdminDashboardController` map
      `GET /api/v1/admin/dashboard` — validate `days ∈ {7,30,90}` (400 + allowedValues),
      lấp key = 0 (D3), `days` mặc định 30.
- [ ] T004 [P] Integration `AdminDashboardIntegrationTest`: AC-1 (≥6 số đối chiếu SQL),
      AC-2 (7 vs 30 ngày), AC-3 (400/401/403), AC-4 (DB trống ⇒ 0/null, không 500).
- [ ] T005 [P] Scan tiếng Việt message mới.
- [ ] T006 Ghi `quickstart-measure.md`.
- [ ] T007 Chạy lại cả suite qua Docker + `docker build` sạch (cổng chung của branch).

## Dependencies

T001/T002 song song được; T003 sau cả hai; T004 sau T003.
