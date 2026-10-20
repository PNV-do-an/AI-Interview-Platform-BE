# Implementation Plan + Tasks: Bổ sung hành động admin trên user (008)

**Branch**: `feature/admin-console-004-008` | **Date**: 2026-10-20 | **Spec**: [spec.md](./spec.md)
**Base**: 001/002 đã build (`AdminUserServiceImpl` có sẵn `attempt()` wrap audit,
`requireAdmin()`, `findLiveUserOrThrow`, `rejectSelfAction`).

## Hợp đồng API (delta trên 001)

| Endpoint | Request | Response | Lỗi |
|---|---|---|---|
| `PATCH /users/{id}/lock` | body tùy chọn `{reason≤500, notifyUser=true}` | như cũ `UserDetailResponse` | 400: đã khóa / tự khóa / target là ADMIN; 404 |
| `POST /users/bulk-lock` | `{ids[1..100], reason≤500, notifyUser}` | `{requested, succeeded, failed:[{id,message}]}` | 400: ids rỗng/>100; 401/403 |
| `POST /users/bulk-unlock` | `{ids[1..100]}` | như trên | như trên |
| `PATCH /users/bulk-role` | `{ids[1..100], role}` | như trên | 400 thêm: role lạ |
| `POST /api/v1/user/change-password` | `{currentPassword, newPassword}` | `{message}` | 400: mật khẩu hiện tại sai / rule mới fail |

## Architecture Decisions

- **D1 — bulk là vòng lặp gọi logic đơn trong cùng service**, mỗi id bắt riêng exception
  chảy vào `failed`; **không** đánh dấu cả batch fail vì 1 id (US-03). Audit vẫn per-id
  thành công — giữ nguyên hợp đồng 001 "mỗi hành động là một dòng audit có admin thật".
- **D2 — `reason` đi vào detail audit**, không thêm cột `users.lock_reason`: audit là nơi
  001 đã ghi mọi lý do từ chối (`reason` field), tra được từ `GET /audit-logs`; thêm cột là
  sửa schema `users` — điều 002 đã cố tình tránh.
- **D3 — body tùy chọn bằng `@RequestBody(required=false)` + DTO nullable** để FE cũ không
  gởi body vẫn chạy (AC-2). Không tạo endpoint lock mới song song.
- **D4 — change-password nằm `ProfileController`/`UserService`** (miền "tài khoản của
  tôi"), verify current bằng `PasswordEncoder.matches`, rule mới bằng 4 check tường minh
  trả message riêng từng rule (không dùng regex một khối — message phải nói đúng rule fail).
- **D5 — chặn khóa ADMIN ở service** (`target.getRole() == ROLE_ADMIN` ⇒ 400) áp cho cả
  lock đơn lẻ lẫn bulk (bulk thì per-id fail với message đó).

## Tasks

- [ ] T001 DTO: `LockAccountRequest` (nullable fields), `BulkUserActionRequest` (ids,
      reason, notifyUser), `BulkRoleChangeRequest` (ids, role), `BulkUserActionResult`
      (`requested, succeeded, failed:[{id,message}]`), `ChangePasswordRequest`.
- [ ] T002 `AdminUserServiceImpl.lockUser(id, LockAccountRequest)`: giữ chữ ký cũ làm
      overload rỗng; thêm nhánh reason/notifyUser + chặn ADMIN (D5). Unlock: chỉ thêm
      chặn ADMIN không cần thiết (unlock trên ADMIN không có hại — bỏ qua, giữ nguyên).
- [ ] T003 Bulk: `bulkLock`, `bulkUnlock`, `bulkRole` trong `AdminUserService(Impl)` +
      3 endpoint trong `AdminUserController` (validate `1 ≤ ids.size ≤ 100`, role enum).
- [ ] T004 `changePassword` trong `UserService(Impl)` + `ProfileController`
      `POST /api/v1/user/change-password` + bump `tokenVersion`.
- [ ] T005 [P] Integration test mở rộng `AdminUserControllerIntegrationTest` + test mới
      cho change-password: AC-1..AC-6.
- [ ] T006 Scan tiếng Việt message; cập nhật hợp đồng 001 (`admin-user-api.md`) với delta
      lock-body + 3 bulk + change-password.
- [ ] T007 Ghi `quickstart-measure.md`; suite + `docker build` (cổng chung branch).

## Open risks

- **R-1**: FE cũ gởi body rỗng `{}` ⇒ `reason=null, notifyUser=true` — chạy như hôm nay
  (mail vẫn đi); hợp đồng ghi rõ `notifyUser` mặc định true, FE phải chủ động false.
- **R-2**: `change-password` kill cả phiên hiện tại sau khi access token hết hạn (max 24h,
  cấu hình `app.jwt.expiration`) — FE nên chủ động logout + mở trang login sau 200.
