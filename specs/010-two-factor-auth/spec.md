# Spec 010 — API 2FA (TOTP) cho đăng nhập bằng mật khẩu

## Bối cảnh

FE wave 2026-10-21 dán nhãn "Khối demo — BE chưa có API 2FA" ở màn bảo mật.
008 và 009 đều cắt 2FA có chủ đích (`specs/008-admin-user-actions/spec.md` mục Out of scope:
"mockup nói dối, không làm gì để theo nó"). Kỳ này là yêu cầu làm API thật, không phải tô dữ liệu.

## Phạm vi đã chốt với anh (hỏi-đáp 2026-10-24)

1. **Hình thức lúc đăng nhập = challenge token.** `POST /auth/login` đúng mật khẩu mà user đã bật
   2FA thì **không** trả JWT: trả `{mfaRequired:true, challengeToken}` (JWT sống 5 phút, cùng chữ
   ký access nhưng có claim `purpose=2fa-challenge` nên `JwtFilter` từ chối — không dùng được làm
   access token). FE nộp mã lên `POST /auth/verify-otp` → mới nhận JWT thật.
2. **Ai bị chặn:** mọi tài khoản đăng nhập bằng mật khẩu, gồm ROLE_USER/STAFF/ADMIN.
   SSO/Google/Github **ngoài scope** — IdP đã là yếu tố thứ hai, và 3 luồng đó cấp token ở
   `AuthController` riêng, không đi qua `LoginInterfaceImpl`.
3. **Có recovery codes.** Bật 2FA sinh 8 mã `XXXXX-XXXXX`, hiển thị đúng một lần, DB chỉ giữ
   BCrypt hash, dùng một lần (`recovery_codes.used_at`). Mất thiết bị: hoặc user tự dùng mã dự
   phòng, hoặc admin `PATCH /admin/users/{id}/2fa` kèm **lý do bắt buộc** → xóa secret + thu hồi
   toàn bộ recovery codes + audit `DISABLE_2FA`.

## Hợp đồng endpoint

| Method | Path | Quyền | Kết quả |
|---|---|---|---|
| GET | `/api/v1/auth/2fa` | isAuthenticated | `{enabled}` |
| POST | `/api/v1/auth/2fa/setup` | isAuthenticated | secret Base32 + `otpauth://` URI (FE tự render QR) |
| POST | `/api/v1/auth/2fa/enable` | isAuthenticated | OTP đúng → bật + 8 recovery codes (duy nhất một lần) |
| POST | `/api/v1/auth/2fa/disable` | isAuthenticated | cần OTP hiện tại **hoặc** một recovery code |
| POST | `/api/v1/auth/login` | public | `mfaRequired:true + challengeToken` khi user bật 2FA |
| POST | `/api/v1/auth/verify-otp` | public | challenge + OTP/recovery → AuthResponse như login thường |
| PATCH | `/api/v1/admin/users/{id}/2fa` | ADMIN | force-disable, `reason` blank → 400 |

`AuthResponse.user` có thêm `twoFactorEnabled`; `UserDetailResponse` có `twoFactorEnabled` cho
chip "2FA Đã kích hoạt" trong console.

## Ràng buộc

- **TOTP thật, không mock:** `dev.samstevens.totp:totp:1.7.1` (SHA1, 6 số, 30s, cửa sổ ±1 step
  chịu lệch đồng hồ điện thoại-server). 1.7.3 không tồn tại trên Central — đã đo.
- **Rate-limit OTP sai = rate-limit mật khẩu sai:** mỗi lần nộp mã sai ghi một dòng `login_attempts`
  `success=false`, đếm theo 5 lần/15 phút trên email và 20 lần/15 phút trên IP, `noRollbackFor`
  để dòng failure không biến mất theo exception.
- **Mật khẩu đúng + OTP sai không được tính là đăng nhập thành công:** `LoginInterfaceImpl` chỉ
  ghi attempt success và reset counter sau khi **qua bước hai**, nên không thể dò OTP song song rồi
  hưởng reset `loginAttempts`.
- **Tắt 2FA phải chứng minh đang giữ thiết bị** (OTP hoặc recovery code) — token bị đánh cắp
  không tự hạ được lớp bảo vệ.
- **Schema:** `ddl-auto: update`, Flyway tắt → thêm `users.totp_enabled`, `users.totp_secret` và
  bảng `recovery_codes` bằng entity, không viết migration.

## Out of scope (kỳ sau)

- Bắt buộc 2FA theo chính sách (admin ép user bật) — chưa có bảng policy.
- WebAuthn/passkey.
- Sửa 3 luồng SSO/Google/Github.
- Danh sách phiên đăng nhập + thu hồi từng phiên: đang làm ở wave `UserSession` riêng
  (`user_session`, claim `sid`) — 2FA phát hành token với `sid=null` nên tương thích legacy.
