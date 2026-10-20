# Bộ đo 008 — Hành động admin trên user + tự đổi mật khẩu (đo 2026-10-20)

## Môi trường đo

- Container maven + Testcontainers MySQL 8.0 thật; `EmailService` là `@MockBean` để đếm email
  (`verify(...).sendAccountLockedEmail` / `never()`) chứ không cần Gmail thật.
- 8 test mới nằm trong `AdminUserControllerIntegrationTest` (class hồi quy 001+002), 3 test
  đổi mật khẩu ở `ChangePasswordIntegrationTest` dùng **login/refresh thật** qua
  `POST /api/v1/auth/login` và `POST /api/v1/auth/refresh` — không mock token.

## Kết quả

- `AdminUserControllerIntegrationTest` — **71/71 pass**, 81.1s (63 test hồi quy 001+002 + 8 test 008).
- `ChangePasswordIntegrationTest` — **3/3 pass**, 31.3s.

| AC | Test | Số đo được |
|---|---|---|
| AC-1 | `test008_LockWithReasonSuppressesMail` | `PATCH /users/{id}/lock` body `{reason:"vi phạm quy định", notifyUser:false}` ⇒ user `locked=true`, status `LOCKED`, `tokenVersion` +1, `verify(emailService, never()).sendAccountLockedEmail(...)`, audit detail chứa đúng lý do (" — Lý do: ") |
| AC-2 | `test008_LockWithoutBodyStillNotifies` | Khóa không body ⇒ hành vi 001 nguyên vẹn: mail khóa đi, audit không có cụm "Lý do" |
| AC-3 | `test008_LockAdminTargetRejected` | Khóa một `ROLE_ADMIN` khác ⇒ 400 "Không thể khóa tài khoản quản trị viên"; audit vẫn ghi **1** dòng với `isSucceeded = false` (lời từ chối cũng phải được kiểm toán) |
| AC-3 | `test008_BulkLockReportsPerId` | `POST /bulk-lock` với `[user, admin, id-999999]` ⇒ `{requested:3, succeeded:1, failed:2}`, mỗi phần tử `failed` mang id + message riêng; đúng **1** audit `succeeded=true` |
| — | `test008_BulkUnlock` | `POST /bulk-unlock` ⇒ 2 id khóa mở lại + mail đi từng id; id là admin chưa khóa ⇒ fail riêng, `never()` mail cho id đó |
| AC-4 | `test008_BulkRole` | `PATCH /bulk-role` với `[self, user]` ⇒ self fail chứa "chính mình", user thường chuyển thành `ROLE_STAFF` |
| AC-6 | `test008_BulkLimitsAndBadInput` | 101 ids ⇒ 400 nêu trần "100"; `ids` rỗng ⇒ 400; `role=ROLE_FOO` ⇒ 400; `reason` 501 ký tự ⇒ 400; cả 4 request ⇒ 0 dòng audit |
| AC-6 | `test008_NewEndpointsGuards` | 4 endpoint mới × (anonymous ⇒ 401, `ROLE_STAFF` ⇒ 403) |
| AC-5 | `testChangePassword_RevokeEverySession` | Login thật lấy 2 token → `POST /api/user/change-password` ⇒ 200 "Đổi mật khẩu thành công", `tokenVersion = 1`; login bằng mật khẩu cũ ⇒ 401 "Mật khẩu sai"; mật khẩu mới ⇒ 200; **refresh token cũ ⇒ 401 "Phiên đăng nhập không còn hiệu lực"**; `admin_audit_log` 0 dòng |
| AC-5 | `testChangePassword_RulesNameTheViolatedOne` | Sai mật khẩu hiện tại ⇒ 400 "Mật khẩu hiện tại không đúng"; "abc" / "abcdefgh" / "Abcdefgh" / "Abcdefg1" ⇒ 4 message khác nhau nêu đúng rule (độ dài, chữ hoa, chữ số); body trống ⇒ 400; DB không đổi cho tới khi request hợp lệ (`tokenVersion` vẫn 1) |
| AC-6 | `testChangePassword_AnonymousRejected` | Anonymous ⇒ 401 |

Suite đầy đủ `mvn -B -o test` (chạy gộp 21h45 2026-10-20): **Tests run: 154, Failures: 0, Errors: 15**;
15 error đó ở `JobControllerIntegrationTest.setUp` (bảng 004 thêm FK, thứ tự xóa trong test cũ sai),
đã sửa và chạy lại riêng class đó (22h53) ⇒ **16/16 BUILD SUCCESS**. Tổng **154/154**.

Lần chạy đầu của 6 class này (trước khi sửa `PasswordEncoderConfig`) **cả 6 context sập** vì vòng
tròn khởi tạo — đó là lý do mục "Sai lệch" dưới đây ghi rõ bug production này.

## Sai lệch so với plan (có chủ đích)

- **`PasswordEncoder` phải rời khỏi `JwtSecurityConfig`.** `UserServiceImpl` nay cần encoder để
  đổi mật khẩu, mà `JwtSecurityConfig` lại phụ thuộc `UserService` ⇒ vòng tròn khởi tạo,
  mọi ApplicationContext sập (`Error creating bean 'dataInitializer' … passwordEncoder is
  currently in creation`). Đã chuyển bean sang `config/PasswordEncoderConfig`; test hồi quy
  001/002 (71 test) chạy lại xanh nên không đổi hành vi thật.
- Đổi mật khẩu **không gửi mail, không ghi `admin_audit_log`**: đây là thao tác của chính user,
  không phải thao tác admin. Phiên thu hồi bằng `tokenVersion++` và được chứng minh bằng
  refresh token cũ ⇒ 401.
- Card "Thiết bị đăng nhập" trong mockup không có endpoint nào: JWT stateless, chỉ
  `login_attempts` (lịch sử đăng nhập) — không có bảng session đang hoạt động để liệt kê.

## Chưa đo (nằm ngoài kỳ này)

- 2FA/mật khẩu một lần trong mockup là hư cấu, không có BE.
- Chưa đo trên Render staging.
