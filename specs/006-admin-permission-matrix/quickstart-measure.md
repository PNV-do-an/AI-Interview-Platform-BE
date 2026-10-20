# Bộ đo 006 — Ma trận phân quyền chỉ đọc (đo 2026-10-20)

## Môi trường đo

- Container maven + Testcontainers MySQL 8.0 thật; `GET /api/v1/admin/permissions/matrix` qua MockMvc.
- Seed 4 user sống (mỗi role một người) + 1 user `ROLE_USER` đã xóa mềm để đo phần đếm.
- Điểm chính của kỳ này: ma trận **không tự ban quyền**, nên test không chỉ đọc JSON — nó bắn
  HTTP thật rồi so kết quả với ô tương ứng trong ma trận.

## Kết quả

Class `AdminPermissionMatrixIntegrationTest` — **5/5 pass**, 51.6s (lần chạy chốt 2026-10-20).

Nằm trong suite đầy đủ `mvn -B -o test` (chạy gộp 21h45 2026-10-20): **154 test**, mọi class khác
xanh ở cùng lần chạy; 15 error duy nhất là `JobControllerIntegrationTest.setUp` vướng FK bảng 004,
đã sửa và chạy lại riêng class đó (22h53) ⇒ **16/16**. Tổng **154/154**.

| AC | Test | Số đo được |
|---|---|---|
| AC-1 | `testMatrix_RoleCountsMatchSql` | Với cả 4 role: `roles[].userCount` bằng `select count(*) from users where role = ? and deleted_at is null`. `ROLE_USER` = 1 (user xóa mềm không đếm). `matrix.roles` đủ 4 phần tử, label "Ứng viên", `ROLE_ADMIN.builtin = true` |
| AC-2 | `testMatrix_ProbesAgreeWithReality` | 6 probe thật, mỗi probe đối chiếu đúng ô ma trận nó mô tả: STAFF `GET /api/v1/admin/users` ⇒ 403; ADMIN cùng endpoint ⇒ 200; INTERVIEWER `POST /api/v1/jobs` ⇒ 200 và `data.status=DRAFT`; USER `POST /api/v1/jobs` ⇒ 403; USER `GET /api/v1/candidate-profile` ⇒ 200; INTERVIEWER `GET /api/v1/admin/permissions/matrix` ⇒ 403 |
| AC-3 | `testMatrix_Guards` | anonymous ⇒ 401 "Yêu cầu đăng nhập"; `ROLE_STAFF` ⇒ 403 "Bạn không có quyền truy cập chức năng quản trị" |
| AC-4 | `testMatrix_IsComplete` | 9 module × 5 action × 4 role = **180 ô**, mọi ô là boolean, không null; `admin-console` có `adminOnly=true` và chỉ role ADMIN bật; module `jobs` chỉ STAFF+ADMIN có quyền DUYỆT; `candidate-profile` chỉ USER có quyền XEM |
| — | `testMatrix_ReadOnly` | Gọi endpoint 2 lần ⇒ `admin_audit_log` vẫn 0 dòng: đọc ma trận không để lại side-effect |

## Sai lệch so với plan (có chủ đích)

- **Không thứ tự hóa matrix bằng `Map.of`**: `Map.of` không đảm bảo thứ tự, test tra theo key
  chứ không theo index — tránh assertion mong manh.
- `roles(...)` trả `Set<String>` dựng bằng stream + `LinkedHashSet` để thứ tự role ổn định
  trên wire (USER, INTERVIEWER, STAFF, ADMIN).

## Chưa đo (nằm ngoài kỳ này)

- Ma trận là bảng tĩnh theo chốt phạm vi "Chỉ đọc": thêm/bớt ô trong matrix **không** đổi hành vi
  thật, quyền thật vẫn do `JwtSecurityConfig` + `@PreAuthorize`. RBAC động để kỳ sau (002 C-3).
- Chưa đo trên Render staging.
