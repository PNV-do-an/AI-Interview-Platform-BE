# Feature Specification: Ma trận phân quyền chỉ đọc (màn "Phân quyền & Vai trò")

**Feature Branch**: `feature/admin-console-004-008`
**Created**: 2026-10-20 · **Status**: Draft · **Input**: mockup Stitch "Role & Permission
Management" + chốt phạm vi 2026-10-20 (phương án "Chỉ đọc") + kế thừa quyết định Out-of-scope
của 002 (RBAC động để kỳ sau) và US-09 của 002 (FR-010: đọc được ma trận).

## Why file 006

Quyền trong BE là **hard-code 2 lớp**: URL rule `/api/v1/admin/**` trong
`JwtSecurityConfig` + `@PreAuthorize` trên từng method; role là enum cố định
`ROLE_USER | ROLE_STAFF | ROLE_INTERVIEWER | ROLE_ADMIN`. Mockup vẽ ma trận sửa được 9
module × 5 action — làm thật nghĩa là đổi kiến trúc toàn hệ thống (chốt: **không**).
Kỳ này BE trả **ma trận tĩnh mô tả đúng reality hard-code**, để FE render tab; nút "Lưu
cấu hình phân quyền" không có endpoint tương ứng.

## User Stories

**US-01** Admin mở tab phân quyền → thấy 4 vai trò (kèm số người dùng thật) và ma trận
module × action đúng như hệ thống đang vận hành.
**US-02** Ô ma trận phân biệt: được cấp (GRANTED), không có (DENIED), và nhóm module chỉ
admin (đánh dấu để FE khóa hiển thị như mockup "Admin Only").
**US-03** Ma trận không nói dối: mọi ô GRANTED tương ứng một quyền thật mà role đó gọi
thật sự được (test đối chứng bằng HTTP thật).

## Functional Requirements

- **FR-001** `GET /api/v1/admin/permissions/matrix` (chỉ ADMIN, qua URL rule) trả:
  - `roles`: 4 vai trò với `role`, `label` tiếng Việt, `userCount` (đếm thật từ `users`,
    gồm cả user chưa verify/locked — là "số tài khoản thuộc vai trò"), `builtin: true`.
  - `modules`: 9 module ánh xạ **thật** vào controller của BE (danh sách ở plan.md D1),
    mỗi module có `key`, `label`, và 5 action `VIEW|CREATE|UPDATE|DELETE|APPROVE`;
    mỗi ô là `granted: bool` cho từng role + cờ `adminOnly: bool` ở cấp module.
- **FR-002** Không có endpoint ghi (PUT/POST lên matrix không tồn tại ⇒ 405 do Spring tự
  trả). Không có "reset về mặc định", không có "thêm vai trò".
- **FR-003** Ô GRANTED phản ánh đúng code: ví dụ — USER không có gì ngoài module "Hồ sơ
  ứng viên" (candidate-profile, cv, cover-letter, mock-interview của chính họ, xem review,
  apply job); INTERVIEWER là NTD (jobs CRUD của mình, slots, duyệt application, orders);
  STAFF gần như admin trừ `/api/v1/admin/**`; ADMIN toàn quyền.
- **FR-004** Ma trận chứa khẳng định userCount ⇒ phải đếm cùng lúc trong một request,
  một query `group by role`, không N+1.

## Out of scope

- RBAC động (bảng permission, sửa qua UI, check quyền qua DB) — chốt 2026-10-20 giữ hard-code.
- "Thêm vai trò", đổi tên vai trò, role tùy chỉnh.
- Ô trạng thái 3 giá trị "not applicable/disabled" chi tiết từng ô như mockup — quy về
  `granted:false` + `adminOnly` ở cấp module (FE tự render khóa).
- "Xuất ma trận" (export) — làm sau theo mẫu CSV 002 nếu cần.

## Acceptance criteria (đo được)

- AC-1: `roles[].userCount` khớp `SELECT role, COUNT(*) FROM users GROUP BY role` (đối chiếu
  SQL sau khi seed ≥ 2 role).
- AC-2: đối chứng HTTP thật ≥ 6 ô: STAFF `GET /api/v1/admin/users` ⇒ 403 (ô DENIED đúng);
  ADMIN endpoint đó ⇒ 200; INTERVIEWER `POST /api/v1/jobs` ⇒ 200/201 (GRANTED đúng);
  USER `POST /api/v1/jobs` ⇒ 403; USER `GET /api/v1/candidate-profile` ⇒ 200 (GRANTED);
  INTERVIEWER `GET /api/v1/admin/permissions/matrix` ⇒ 403.
- AC-3: anonymous ⇒ 401; STAFF ⇒ 403.
- AC-4: matrix chứa đủ 4 role × 9 module × 5 action (180 ô), không null.
