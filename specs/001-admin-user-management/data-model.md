# Phase 1 Data Model: Quản lý tài khoản (admin)

Không tạo bảng mới. Thay đổi trên 2 entity đang có; cột mới đều `nullable` hoặc có default để
`ddl-auto: update` thêm được mà không phá dữ liệu hiện có (Flyway đang tắt — xem `spec/known-gaps.md:52`).

## 1. `User` (bảng `users`)

18 field hiện có (`model/entity/User.java:22-77`) + **1 cột mới `locked_at`** (`lockedAt`, nullable) để
phân biệt "admin khóa lúc nào" với chặn brute-force. **Không** có `locked_by_admin_id` (ai khóa đã có
trong audit log, không duplicated sang `users`).
Lý do không cần cột "snapshot trước khi xóa": xóa mềm chỉ đụng `deletedAt` + `tokenVersion`, còn
`enabled` / `locked` / `role` giữ nguyên giá trị, nên bỏ `deletedAt` là đủ khôi phục trạng thái (R7 đã đính chính).

Trạng thái suy diễn (đây là hợp đồng hiển thị, không phải cột):

| Điều kiện | `status` trả về |
|---|---|
| `deletedAt != null` | `DELETED` |
| `locked == true` | `LOCKED` |
| `enabled == false` | `INACTIVE` |
| còn lại | `ACTIVE` |

Ranh giới khóa (hai tín hiệu tách nhau, spec US2.4):
- `locked == true` → **khóa bởi admin** (đường brute-force không set cờ này, nó dùng `lockUntil`).
- `lockUntil > now()` → **chặn tạm vì đăng nhập sai** (`LoginInterfaceImpl:69-86`), tự hết hạn.
- `lockUntil <= now()` → không còn hiệu lực chặn.

Ràng buộc nghiệp vụ phải chặn lúc ghi (Constitution III):

| Rule | Nơi chặn | Mã lỗi |
|---|---|---|
| Không tác động (khóa/mở khóa/reset/đổi role/xóa/restore) lên user `deletedAt != null` | service | 404 "Không tìm thấy tài khoản (đã bị xóa)" — **nhất quán mọi thao tác** (FR-001) |
| Không tự khóa / tự xóa / tự đổi role / tự tắt | service | 400 |
| Không mở khóa khi `locked == false` | service | 400 |
| Không restore khi `deletedAt == null` | service | 400 |
| Restore mà email đã bị user sống khác chiếm | — | **không xảy ra được**: hàng đã xóa mềm vẫn giữ email unique, nên user sống không thể chiếm email đó (R7 đính chính — bỏ 409) |
| Luôn còn ≥1 `ROLE_ADMIN` chưa xóa (áp cho **đổi role** và **xóa**) | service (count, R2) | 400 |
| `enabled=true` cho user chưa từng verify email (còn `verificationToken`) | service | 400 "Tài khoản chưa xác thực email…" |
| page/size ngoài khoảng, status/role ngoài danh mục, search > 100 ký tự | controller (`@Validated`, enum) | 400 |
| Principal không resolve được thành admin thật | service (`requireAdmin`) | 403 — không ghi audit với `adminId` giả |

## 2. `AdminAuditLog` (bảng `admin_audit_log`)

| Field | Kiểu | Nullable | Thay đổi | Ghi chú |
|---|---|---|---|---|
| `id` | BIGINT auto | — | giữ | |
| `adminId` | BIGINT | **NOT NULL** | giữ, nhưng **không còn giá trị giả `0`** (R10): không resolve được admin ⇒ thao tác bị từ chối trước khi ghi | |
| `adminEmail` | VARCHAR(255) | NOT NULL | giữ, không còn `"system"` | |
| `targetUserId` | BIGINT | nullable | giữ | |
| `action` | VARCHAR(100) | NOT NULL | giữ cột, **giá trị đóng** theo enum `AuditAction` (R10) | validate ở service, ghi chuỗi enum |
| `detail` | TEXT | nullable | giữ | chuỗi mô tả tiếng Việt, luôn kèm email của target; `CHANGE_ROLE` ghi "Đổi role: A → B \| user: <email>" (R7 đính chính: **không** dùng JSON snapshot) |
| **`succeeded`** | TINYINT(1) NOT NULL default 1 | NOT NULL | **MỚI** | cột `succeeded` (JSON cũng là `succeeded`), nỗ lực bị từ chối ⇒ `false` (US3.3) |
| **`reason`** | VARCHAR(500) | nullable | **MỚI** | lý do từ chối (vd. "Tài khoản đã bị khóa") |
| `createdAt` | DATETIME | NOT NULL | giữ | |

`@Table(indexes=...)` thêm cho entity (để `ddl-auto` tạo, vì V3 chưa từng chạy):
`idx_audit_created_at (created_at)`, `idx_audit_target_user_id (target_user_id)`,
`idx_audit_admin_id (admin_id)` — đúng 3 index migration V3 khai báo mà schema thật không có.
`@Index` của JPA không khai báo được `DESC`; query vẫn sắp `createdAt DESC` (MySQL dùng index ngược được).

Enum mới `model/enums/AuditAction.java`:
`VIEW_USER, LOCK_USER, UNLOCK_USER, DELETE_USER, RESTORE_USER, RESET_PASSWORD, CHANGE_ROLE, ENABLE_USER, DISABLE_USER`.

## 3. Quan hệ & chuyển trạng thái

Chuyển trạng thái hợp lệ (phía admin). "giữ" = không đổi field đó.

| Từ → Sang | Thao tác | Điều kiện tiền quyết | Tác dụng phụ bắt buộc |
|---|---|---|---|
| ACTIVE → LOCKED | `PATCH /lock` | chưa khóa, chưa xóa, không phải mình | bump `tokenVersion`, mail thông báo (fail ⇒ 502+rollback) |
| LOCKED → ACTIVE | `PATCH /unlock` | `locked == true`, chưa xóa | xóa `locked`; **không** đụng `lockUntil` (chặn brute-force tự hết hạn riêng); mail |
| ACTIVE/INACTIVE/LOCKED → INACTIVE | `PATCH /status {enabled:false}` | chưa xóa, không phải mình | bump `tokenVersion`; không mail |
| INACTIVE → ACTIVE | `PATCH /status {enabled:true}` | đã verify email, chưa xóa | mail xác thực đã gửi trước đó là điều kiện; không bump |
| any sống → DELETED | `DELETE /{id}` | chưa xóa (đã xóa rồi ⇒ 404 như mọi thao tác), không phải mình, còn ≥1 admin khác nếu target là admin | `deletedAt=now`, bump `tokenVersion`, mail |
| DELETED → (trạng thái trước xóa) | `POST /{id}/restore` | `deletedAt != null` | chỉ `deletedAt=null`; `enabled`/`locked`/`role` vốn bị xóa mềm giữ nguyên nên tự trở lại như trước; **không** bump tokenVersion; **không** mail |
| ROLE_X → ROLE_Y | `PATCH /{id}/role` | chưa xóa, không phải mình, còn ≥1 admin nếu hạ | bump `tokenVersion`; không mail |
| any → reset link | `POST /{id}/reset-password` | chưa xóa | token mới TTL **30 phút** = `RESET_TOKEN_TTL_MINUTES` (`PasswordResetServiceImpl:40`), audit `RESET_PASSWORD`, mail (fail ⇒ 502 + hoàn tác token). **Sửa kèm**: template mail đang hard-code "1 giờ" (`EmailServiceImpl:158`) → đổi thành 30 phút cho khớp cả 2 luồng (FR-008) |

## 4. Response shape thay đổi (không phá field cũ)

- `UserSummaryResponse`: `id, email, fullName, role, status, createdAt` + **`deletedAt`** (nullable).
  `status` giờ có thể thật sự là `DELETED` (chỉ xuất hiện khi query deleted, R5).
- `UserDetailResponse` (`model/dto/response/UserDetailResponse.java`): `id, email, fullName, role,
  enabled, locked, status, createdAt, updatedAt, deletedAt` + **mới**: **`phone`**, **`lockedAt`**,
  **`blockedUntil`** (= `lockUntil` khi còn hiệu lực, ngược lại `null`), **`failedLoginAttempts`**
  (= `loginAttempts`). `locked` chính là "khóa bởi admin" (R7 đã đính chính: **không** đổi tên thành
  `lockedByAdmin`/`bruteForceBlockedUntil` để tránh bẻ FE đang đọc `locked`); **không** trả `tokenVersion`.
  **`enabled` là trạng thái đăng nhập hiệu dụng**, không phải cột raw: `User.isEnabled()` (override của
  `UserDetails`) trả `enabled && deletedAt == null` (`model/entity/User.java:100-103`), nên tài khoản đã xóa
  mềm luôn báo `enabled:false` dù cột trong DB vẫn `1` (đo 2026-10-19: `en=1 lk=1 del=yes` nhưng JSON
  `"enabled":false`). Column raw chỉ có ý nghĩa khi restore (nó được giữ nguyên lúc xóa).
  Vẫn **không** lộ `password`, `resetToken`, `verificationToken` (đã kiểm, giữ).
- `AuditLogResponse`: thêm **`succeeded`**, **`reason`**, **`targetEmail`**, **`targetFullName`**.
- Danh sách vẫn trả `Page` của Spring (`data.content`, `totalElements`, `totalPages`, ...). **Không** đổi
  envelope ở lần này (FE đang đọc `data.content`; đổi structure = phạm vi FE, Constitution VII).
