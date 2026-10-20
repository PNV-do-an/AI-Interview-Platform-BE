# Implementation Plan + Tasks: Ma trận phân quyền chỉ đọc (006)

**Branch**: `feature/admin-console-004-008` | **Date**: 2026-10-20 | **Spec**: [spec.md](./spec.md)

## Hợp đồng API

`GET /api/v1/admin/permissions/matrix` →

```json
{
  "roles": [{"role":"ROLE_USER","label":"Ứng viên","userCount":0,"builtin":true}, ...],
  "modules": [{
    "key":"jobs","label":"Tin tuyển dụng","adminOnly":false,
    "actions":[{"action":"VIEW","label":"Xem","granted":{"ROLE_USER":false,
    "ROLE_STAFF":true,"ROLE_INTERVIEWER":true,"ROLE_ADMIN":true}}, ...5 action]
  }, ...9 module]
}
```

## Architecture Decisions

- **D1 — 9 module ánh xạ thật** (đọc `@PreAuthorize` từng controller, không theo tên module
  mockup khi BE không có miền đó):
  1. `candidate-profile` — Hồ sơ & CV ứng viên (USER tự quản; view công khai review)
  2. `jobs` — Tin tuyển dụng (003: DRAFT/submit; review = ADMIN/STAFF)
  3. `applications` — Ứng tuyển & pipeline
  4. `slots` — Lịch phỏng vấn
  5. `mock-interviews` — Phỏng vấn AI tự luyện
  6. `packages-orders` — Gói dịch vụ & đơn hàng
  7. `reviews` — Đánh giá & phản hồi
  8. `bug-reports` — Báo lỗi
  9. `admin-console` — Quản trị hệ thống (users, audit, dashboard, settings, moderation,
     matrix) — `adminOnly: true`
- **D2 — ma trận là code tĩnh trong một lớp `PermissionMatrixDefinition`**, không DB, không
  config file: nguồn sự thật duy nhất là chữ ký `@PreAuthorize` hiện hành; ghi chú mỗi module
  trỏ về file controller chứng minh.
- **D3 — `userCount` một query `group by role`** trong `UserRepository`; không đếm từng role
  một lần gọi (FR-004).
- **D4 — không expose `adminListJobs` kiểu chết**: module jobs mô tả hành vi 003 thật.

## Tasks

- [ ] T001 [P] DTO: `PermissionMatrixResponse`, `RoleSummaryResponse`, `ModulePermissionResponse`,
      `ActionPermissionResponse` (record).
- [ ] T002 [P] `UserRepository.countByRoleGrouped()` — `List<Object[]> {role, count}`.
- [ ] T003 Lớp `PermissionMatrixDefinition` (9 module × 5 action × 4 role, kèm comment trỏ
      controller nguồn) + `AdminPermissionController` map `GET /api/v1/admin/permissions/matrix`.
- [ ] T004 [P] Integration test: AC-1 (userCount khớp SQL), AC-2 (6 cặp đối chứng HTTP),
      AC-3 (401/403), AC-4 (180 ô đủ, không null).
- [ ] T005 Ghi `quickstart-measure.md`; chạy suite + `docker build` (cổng chung branch).

## Open risks

- **R-1**: ma trận tĩnh có thể **lạc sau code** khi ai đó đổi `@PreAuthorize` mà không sửa
  definition. Giảm rủi ro: AC-2 đối chứng HTTP thật + comment trỏ file nguồn trong
  `PermissionMatrixDefinition`; test fails khi hành vi thật đổi.
