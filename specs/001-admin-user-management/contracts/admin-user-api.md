# Contract: Admin User Management API

Base path `/api/v1/admin`. **Gate**: `ROLE_ADMIN` — chặn bằng **2 lớp**: `hasRole('ADMIN')` ở
`JwtSecurityConfig` (mới, R13) **và** `@PreAuthorize` ở controller. `ROLE_STAFF` ⇒ 403 (chốt 2026-10-19).

**Envelope thành công** (`ApiResponse`, `@JsonInclude(NON_NULL)`):
```json
{ "success": true, "message": "<string|null>", "data": <T|null> }
```
**Envelope lỗi** (`ErrorModel`):
```json
{ "status": 400, "message": "...", "path": "/api/v1/admin/users", "timestamp": "...", "detail": {} }
```

Quy tắc chung áp cho **mọi** endpoint dưới đây:

| Tình huống | Status | Message (tiếng Việt) | Ghi chú |
|---|---|---|---|
| Không có token / token hết hiệu lực | 401 | "Yêu cầu đăng nhập" | lớp URL rule trả envelope lỗi, không body rỗng; body luôn `charset=UTF-8` |
| Token hợp lệ nhưng role ≠ ADMIN | 403 | "Bạn không có quyền truy cập chức năng quản trị" | cả 2 lớp (URL rule + `@PreAuthorize`) đều dùng message này |
| User không tồn tại (`id` lạ) | 404 | "Không tìm thấy người dùng với id: X" | |
| User đã bị xóa mềm — **mọi** thao tác (kể cả xóa lần 2) | 404 | "Không tìm thấy tài khoản (đã bị xóa)" | FR-001: thống nhất, không còn chỗ 400/chỗ 404 |
| Tham số lọc/trang không hợp lệ | 400 | nêu rõ tham số nào + giá trị được phép | `@Validated` ở controller, trước DB |
| Vi phạm rule nghiệp vụ (đã khóa, tự tác động, admin cuối…) | 400 | message nghiệp vụ hiện có | + ghi audit `succeeded=false` kèm `reason` |
| Gửi mail thất bại | 502 | "Không gửi được email, thao tác đã được hoàn tác" | + **rollback** toàn bộ thay đổi (R1); **không** có dòng audit mới (transaction đã rollback) |
| Tài khoản bị admin khóa, người dùng thử đăng nhập | 423 | "Tài khoản của bạn đã bị khóa bởi quản trị viên…" | `detail` rỗng (không có `lockedUntil`); trước đây 500 (R15) |
| Lỗi hệ thống không chủ động ném | 500 | "Lỗi hệ thống. Vui lòng thử lại sau." | **không** kèm `ex.getMessage()` (R11) |

---

## 1. `GET /api/v1/admin/users` — danh sách tài khoản **chưa xóa**

Query: `page` (default 0, `@Min(0)`) · `size` (default 20, `@Min(1) @Max(100)`) ·
`search` (optional, ≤100 ký tự, escape `%`/`_`/`\`) · `status` (optional enum
`ACTIVE|INACTIVE|LOCKED|PENDING`, thêm `PENDING` từ 002) · `role` (optional enum `Role`).

`data` = `Page<UserSummaryResponse>`:
`content[].{id, email, fullName, role, status, createdAt, deletedAt, pendingVerification, lastLoginAt}`
(`deletedAt` luôn `null` ở endpoint này), `totalElements, totalPages, number, size, sort, pageable`.

- `PENDING` (002, Clarify C-2) = `verification_token IS NOT NULL` — tài khoản chưa dùng link xác thực.
  Một tài khoản vừa `PENDING` vừa `INACTIVE` (đó là hai cách nhìn khác nhau, không phải hai giá trị loại nhau).
- `pendingVerification` (002) = boolean cùng định nghĩa với bộ lọc `PENDING` ở trên.
- `lastLoginAt` (002, Clarify C-1) = `MAX(created_at)` của `login_attempts` **thành công** theo email,
  `null` khi chưa có lần đăng nhập thành công nào. Đây là "lần đăng nhập gần nhất", **không phải** "đang hoạt động".
  Lấy theo một query `IN` gộp cho cả trang (không N+1).
- `status=DELETED` ⇒ **400** "Bộ lọc DELETED không dùng cho danh sách này, hãy dùng
  `GET /api/v1/admin/users/deleted`" (R5). Lưu ý message liệt kê `[ACTIVE, INACTIVE, LOCKED, DELETED]`
  làm tập enum (nên DELETED vượt qua bước parse enum) rồi bị service chặn ở bước kiểm tra danh mục.
- `status=FOO` / `role=ROLE_FOO` ⇒ **400** qua `MethodArgumentTypeMismatchException` (R3), không còn 500.
- Sort cố định `createdAt DESC`.

## 2. `GET /api/v1/admin/users/deleted` — **MỚI**: danh sách tài khoản đã xóa mềm

Query: `page`, `size` (như trên), `search` (optional), `role` (optional — thêm từ 002 để badge
`stats.deleted` đếm đúng theo cùng bộ lọc với bảng). Không có `status` (mọi dòng đều `DELETED`).
`data` = `Page<UserSummaryResponse>` với `status="DELETED"`, `deletedAt` là thời điểm xóa.

## 3. `GET /api/v1/admin/users/{id}` — chi tiết

`data` = `UserDetailResponse`: `id, email, fullName, role, enabled, locked, status, createdAt, updatedAt,
deletedAt` **+ mới**: `phone`, `lockedAt`, `blockedUntil` (= `lockUntil` khi còn hiệu lực, ngược lại `null`),
`failedLoginAttempts`, `pendingVerification` (002). `locked` = "khóa bởi admin" (giữ tên cũ để không bẻ FE đang đọc `locked`; R7 đính chính).
`enabled` là **trạng thái đăng nhập hiệu dụng** (`enabled && deletedAt == null` theo override
`User.isEnabled()`), nên tài khoản đã xóa mềm luôn trả `enabled:false` dù cột trong DB còn `1`.
Không bao giờ trả `password`, `resetToken`, `verificationToken`, `tokenVersion`.

Ghi audit `VIEW_USER` (`succeeded=true`). User đã xóa mềm: chi tiết **đọc được** (200, `status="DELETED"`)
vì admin cần nó để quyết định restore — đây là ngoại lệ có chủ đích duy nhất của quy tắc 404
(vốn áp cho các thao tác ghi).

## 4. `PATCH /api/v1/admin/users/{id}/lock`

Thành công: 200, message `"Tài khoản đã bị khóa"`, `data` = `UserDetailResponse`.
Hiệu lực: `locked=true`, `lockedAt=now`, **bump `tokenVersion`** (R9), mail `sendAccountLockedEmail`,
audit `LOCK_USER`.

| Case | Status |
|---|---|
| đã khóa rồi | 400 "Tài khoản đã bị khóa" |
| chính mình | 400 "Không thể khóa tài khoản của chính mình" |
| đã xóa mềm | 404 (hợp đồng chung) |
| mail fail | 502 + **rollback** (`locked` vẫn `false`, `tokenVersion` không đổi) |
| target là ADMIN | 400 "Không thể khóa tài khoản quản trị viên" — từ 008, xem §18 |

## 5. `PATCH /api/v1/admin/users/{id}/unlock`

Thành công: 200, message `"Tài khoản đã được mở khóa"`, `locked=false`, `lockedAt=null`, mail,
audit `UNLOCK_USER`. **Không** đụng `lockUntil`/`loginAttempts` (chặn brute-force là cơ chế riêng).

| Case | Status |
|---|---|
| không hề bị khóa | 400 "Tài khoản không trong trạng thái bị khóa" |
| đã xóa mềm | 404 (thay vì 200 như hiện tại — FR-001) |
| mail fail | 502 + rollback (`locked` vẫn `true`) |

## 6. `PATCH /api/v1/admin/users/{id}/status` — **MỚI**: bật/tắt đăng nhập

Body: `{"enabled": true|false}` (`@NotNull`). Thành công: 200, message
`"Tài khoản đã được tắt"` / `"Tài khoản đã được bật"`, `data` = `UserDetailResponse`.
Tắt ⇒ `enabled=false` + bump `tokenVersion`, audit `DISABLE_USER`, **không mail**.
Bật ⇒ audit `ENABLE_USER`, **không mail**.

| Case | Status |
|---|---|
| target là chính admin | 400 "Không thể thay đổi trạng thái tài khoản của chính mình" |
| bật cho user chưa từng xác thực email | 400 "Tài khoản chưa xác thực email" |
| trạng thái đã đúng yêu cầu | 400 "Tài khoản đã ở trạng thái …" |
| đã xóa mềm | 404 |
| body thiếu `enabled` | 400 (validation, message tiếng Việt qua handler mới) |

## 7. `DELETE /api/v1/admin/users/{id}` — xóa mềm

Thành công: 200, message `"Tài khoản đã được xóa"`, `deletedAt=now` + bump `tokenVersion`,
mail `sendAccountDeletedEmail`, audit `DELETE_USER` (`detail` = "Xóa tài khoản: <email>").
Xóa mềm **không** đụng `enabled`/`locked`/`role`, nên restore không cần snapshot (R7 đính chính).

| Case | Status |
|---|---|
| chính mình | 400 "Không thể xóa tài khoản của chính mình" |
| đã xóa rồi | 404 "Không tìm thấy tài khoản (đã bị xóa)" (hợp đồng chung, FR-001) |
| admin cuối cùng chưa xóa | 400 "Không thể xóa: phải luôn có ít nhất một tài khoản Quản trị viên" (guard mới — R2; **không tới được qua HTTP** khi actor ≠ target — xem R15) |
| mail fail | 502 + rollback (`deletedAt` vẫn `null`) |

## 8. `POST /api/v1/admin/users/{id}/restore` — **MỚI**: khôi phục đã xóa mềm

Thành công: 200, message `"Tài khoản đã được khôi phục"`, `data` = `UserDetailResponse`.
Hiệu lực: **chỉ** `deletedAt=null`. `enabled`/`locked`/`role` trở lại đúng giá trị trước khi xóa vì
xóa mềm không đổi chúng (R7 đính chính — không có snapshot, không có fallback "phải verify lại email").
**Không** bump `tokenVersion` (token lúc xóa đã bị giết), **không** gửi mail; audit `RESTORE_USER`
(`detail` = "Khôi phục tài khoản: <email>").

| Case | Status |
|---|---|
| chưa từng bị xóa | 400 "Tài khoản không ở trạng thái đã xóa" |
| email xung đột với tài khoản sống | không xảy ra — hàng đã xóa mềm vẫn giữ unique email của chính nó |
| mail fail | không áp dụng (restore không gửi mail) |

## 9. `POST /api/v1/admin/users/{id}/reset-password`

Thành công: 200, message `"Email đặt lại mật khẩu đã được gửi"`, token `UUID` mới,
**TTL 30 phút** (khớp `RESET_TOKEN_TTL_MINUTES` và template mail — FR-008), audit `RESET_PASSWORD`.
Không bump `tokenVersion` (mật khẩu chưa đổi).

| Case | Status |
|---|---|
| đã xóa mềm | 404 (hợp đồng chung, thay 404 message khác hiện tại) |
| mail fail | 502 + rollback: `resetToken`/`expiry` trở về giá trị cũ (không còn "ghi đè âm thầm" + mail không đi) |

Ghi chú đã đo: đường **user-side** (`/auth/forgot-password`) giữ nguyên rate-limit `password_reset_attempts`
riêng của nó; đường admin **không** dùng bảng đó (quyết định: admin là dòng tin cậy, đã chốt trong plan;
nếu muốn chặn rate-limit cho cả admin thì ghi `known-gaps` chứ không tự thêm).

## 10. `PATCH /api/v1/admin/users/{id}/role`

Body `{"role":"ROLE_USER|ROLE_STAFF|ROLE_ADMIN|ROLE_INTERVIEWER"}` (`@NotNull`).
Thành công: 200, message `"Vai trò đã được cập nhật"`, bump `tokenVersion`, audit `CHANGE_ROLE`
(`detail` = "Đổi role: A → B | user: <email>").

| Case | Status |
|---|---|
| chính mình | 400 "Không thể thay đổi vai trò của chính mình" |
| hạ admin cuối cùng | 400 (guard **đếm**, R2) |
| đã xóa mềm | 404 |
| role không thuộc danh mục | 400 (message tiếng Việt qua handler validation mới) |

## 11. `GET /api/v1/admin/audit-logs`

Query: `page`, `size` (như mục 1) **+ mới**: `adminId` (Long), `targetUserId` (Long), `action`
(enum `AuditAction`), `from` / `to` (`yyyy-MM-dd'T'HH:mm:ss` hoặc `yyyy-MM-dd`).
Thành công: 200, `data` = `Page<AuditLogResponse>`:
`{id, adminId, adminEmail, targetUserId, targetEmail, targetFullName, action, detail, success, reason, createdAt}`
sắp xếp `createdAt DESC`. `action` sai danh mục ⇒ 400 (R3). `from > to` ⇒ 400.

---

# Bổ sung từ feature 002 (directory) — 6 endpoint, cùng gate `ROLE_ADMIN` 2 lớp

Số đo thật của từng mục: `specs/002-admin-user-directory/quickstart-measure.md`.

## 12. `GET /api/v1/admin/users/stats?search=&role=`

`data` = `AdminUserStatsResponse`: `{total, active, pendingVerification, locked, disabled, deleted,
activeLast30Days}` (to `long`).

- **Sáu con số đầu** đếm bằng **đúng** predicate của danh sách (§1/§2), chỉ khác nhau ở điều kiện
  trạng thái; `search`/`role` áp chung, **`status` không áp** (mỗi huy hiệu là một tab, nên tab
  "All" phải hiện tổng của mọi tab với cùng từ khóa). Lưu ý đã đo: `active + pending + locked +
  disabled` **có thể** > `total` vì `PENDING` chồng `INACTIVE`.
- `activeLast30Days` = số tài khoản sống có **ít nhất một lần đăng nhập thành công trong 30 ngày**
  (`login_attempts`, Clarify C-1) — "đăng nhập gần đây", không phải "đang online".
- `search` >100 ký tự ⇒ 400 "Từ khóa tìm kiếm tối đa 100 ký tự"; `role` sai ⇒ 400 danh mục enum.

## 13. `GET /api/v1/admin/users/export?search=&role=&status=` — CSV

Trả file thô `text/csv;charset=UTF-8` + `Content-Disposition: attachment; filename="users-export.csv"`
(không envelope `ApiResponse`), cột:`id,email,fullName,role,status,pendingVerification,lastLoginAt,createdAt,deletedAt`.
**Không** có `password` / token / `tokenVersion`.

- Dòng dữ liệu trong file **bằng đúng** `totalElements` của §1 cùng bộ lọc (đã đo 4 bộ lọc + moc 10.000).
- **Trần 10.000 dòng**: vượt ⇒ **400** "Kết quả vượt 10000 dòng, hãy thu hẹp bộ lọc".
- `status=DELETED` ⇒ 400 "Bộ lọc DELETED không dùng cho bảng xuất này, hãy dùng
  `GET /api/v1/admin/users/deleted`".
- Ô độc hại sinh từ `fullName` do người dùng nhập: nháy kép đôi thành `""`, ô chứa `,` hoặc xuống dòng
  được bọc nháy kép (RFC 4180), ô bắt đầu bằng `= + - @` hoặc tab/CR được chặn bằng `'` để Excel không
  diễn giải thành công thức.
- Ghi audit `EXPORT_USERS` (`targetUserId=null`, `detail` nêu số dòng + bộ lọc).

## 14. `POST /api/v1/admin/users` — "Add User" = gửi lời mời (Clarify C-4)

Body `AdminInviteUserRequest`: `{fullName (bắt buộc, ≤200), email (bắt buộc, ≤200), role (bắt buộc),
phone (optional, ≤20)}` — **không có `password`**.

- 200 + "Đã gửi email mời tài khoản", `data` = `UserDetailResponse` với `enabled:false`,
  `pendingVerification:true`, `status:"INACTIVE"`.
- Tài khoản tạo ra có `verification_token` = UUID, hạn 24 giờ, và `password` là chuỗi bcrypt **ngẫu
  nhiên không ai biết** (cột NOT NULL); người được mời phải tự đặt mật khẩu qua luồng "quên mật khẩu"
  hiện có — xem known-gaps.
- Email trùng (kể cả của tài khoản đã xóa mềm) ⇒ **409**; của tài khoản xóa mềm thì message chỉ đường
  `POST /api/v1/admin/users/{id}/restore`. Race đồng thời cũng ra 409 (bắt `DataIntegrityViolationException`
  tại chỗ, không thêm handler toàn cục).
- Mail hỏng ⇒ **502** và **rollback**: không còn user, không còn dòng audit `INVITE_USER` (đã đo).
- Audit `INVITE_USER` với `admin_id` thật.

## 15. `POST /api/v1/admin/users/{id}/invitation/resend`

200 + "Đã gửi lại email mời": token **đổi sang giá trị mới**, expiry +24h, audit `RESEND_INVITATION`.
Tài khoản đã xác thực email ⇒ 400 "Tài khoản đã xác thực email". Mail hỏng ⇒ 502 và **token giữ nguyên
giá trị cũ** (rollback, đã đo).

## 16. `DELETE /api/v1/admin/users/{id}/invitation` — thu hồi link mời

200 + "Đã thu hồi link mời", `data` = `UserDetailResponse` (`pendingVerification:false`).
Chỉ xóa `verification_token` + `verification_token_expiry`; **không** đổi `enabled`, `locked`,
`tokenVersion`; **không** gửi mail (nên không có 502 ở đường này); tài khoản vẫn nằm trong danh sách
và vẫn bị từ chối đăng nhập. Không có lời mời đang chờ ⇒ 400 "Tài khoản không có lời mời đang chờ xác thực"
(+ audit `succeeded=false` mang `reason`).

## 17. `PATCH /api/v1/admin/users/{id}` — sửa hồ sơ

Body `AdminUpdateProfileRequest`: `{fullName?, email?, phone?}` — tất cả optional, sửa trực tiếp,
**không** đụng vai trò/trạng thái (vẫn là §6/§10).

- Không đổi email: 200 "Hồ sơ đã được cập nhật", `data` = `{user, emailChanged:false}`.
- **Đổi email** (`emailChanged:true`) kéo theo 4 việc, tất cả trong một transaction: email mới,
  `enabled=false` (chờ xác thực lại), `verification_token` mới + mail mời, `tokenVersion` cộng 1 nên
  phiên/refresh token cũ của chính tài khoản đó chết ngay (đã đo: refresh 200 → 401). Message dài nêu rõ
  hệ quả. Audit `UPDATE_PROFILE`.
- Email mới trùng người khác ⇒ **409**, hồ sơ giữ nguyên. Admin đổi email **của chính tài khoản đang
  đăng nhập** sang địa chỉ khác ⇒ 400 "Không thể đổi email của chính tài khoản quản trị đang đăng nhập".
- Body rỗng ⇒ 400 "Không có thông tin nào để cập nhật"; `fullName` chỉ có space ⇒ 400
  "Họ tên không được để trống" (chặn lúc ghi, kiểm tra trước cả rule "body rỗng").

# Bổ sung từ feature 008 (user actions) — 4 endpoint, cùng gate `ROLE_ADMIN` 2 lớp

## 18. `PATCH /api/v1/admin/users/{id}/lock` — body giờ là tùy chọn (008)

Body: `{"reason": "…(≤500)", "notifyUser": true|false}` — **được phép bỏ trống entirely** (giữ hành vi 001).
- `reason` có giá trị ⇒ audit `LOCK_USER` nối thêm " — Lý do: …".
- `notifyUser=false` ⇒ **không** gửi `sendAccountLockedEmail`; mặc định (null) ⇒ vẫn gửi.
- `reason` > 500 ký tự ⇒ 400 "Lý do tối đa 500 ký tự".

| Case mới | Status |
|---|---|
| target có `role=ROLE_ADMIN` | 400 "Không thể khóa tài khoản quản trị viên" + audit `succeeded=false` |

## 19. `POST /api/v1/admin/users/bulk-lock`

Body: `{"ids":[1,2,3], "reason":"…", "notifyUser":false}` (`ids` 1..100). Response 200 với
`data = {requested, succeeded, failed:[{id, message}]}` — **mỗi id một transaction riêng**, id fail
không làm hỏng id đã thành công. Message lỗi lấy nguyên văn từ rule đơn lẻ (vd mục 18, 404).
Audit: đúng số id thành công (`LOCK_USER`, `succeeded=true`); id bị chặn vẫn có dòng `succeeded=false`;
id không tồn tại ⇒ không có dòng audit.

## 20. `POST /api/v1/admin/users/bulk-unlock`

Body: `{"ids":[…]}`.Same envelope; per-id rule "Tài khoản không ở trạng thái bị khóa" ⇒ vào `failed`.

## 21. `PATCH /api/v1/admin/users/bulk-role`

Body: `{"ids":[…], "role":"ROLE_STAFF"}`. Per-id rule của §10 áp dụng nguyên vẹn: tự đổi ⇒ fail
"Không thể thay đổi vai trò tài khoản của chính mình"; hạ role admin cuối cùng ⇒ fail.

## 22. `POST /api/user/change-password` — **không phải endpoint admin**

`@PreAuthorize("isAuthenticated()")`, body `{"currentPassword":"…","newPassword":"…"}`.
Thành công: 200, message "Đổi mật khẩu thành công…", **bump `tokenVersion`** ⇒ refresh token cũ 401,
mật khẩu cũ login 401. Bốn rule mật khẩu mới có message riêng:
"…tối thiểu 8 ký tự" / "…chữ hoa và một chữ thường" / "…ít nhất một chữ số" / "…ký tự đặc biệt (@#$%^&+=!)" —
tất cả là 400. Sai mật khẩu hiện tại ⇒ 400 "Mật khẩu hiện tại không đúng". Anonymous ⇒ 401.
Không gửi mail, không ghi `admin_audit_log` (chủ thể là chính user, không phải admin).

## Việc phải làm ngoài 11 endpoint trên (thuộc hợp đồng)

- `JwtFilter`: catch thêm `InvalidAccountException` (R12) — token của email không còn trong DB ⇒ 401, hết 500.
- `GlobalExceptionHandler`: 500 không rò message; handler mới cho type mismatch / param violation /
  data integrity (R11).
- Mail template: bỏ hard-code "1 giờ", dùng đúng TTL 30 phút (FR-008) — sửa `EmailServiceImpl:158`.
