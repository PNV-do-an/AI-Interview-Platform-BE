# Quên mật khẩu (forgot / reset password)

Trạng thái: `done`
Người/phiên tạo: 2026-10-17, branch `feature/create-api-sign-up`
Mockup nguồn: `interviewai_reset_password` (màn nhập email) + `interviewai_set_new_password_confirmation` (màn đặt mật khẩu mới)

## 1. Mục tiêu

User quên mật khẩu nhận link qua email, mở link thấy được tài khoản của mình + thời gian còn hiệu lực, đặt mật khẩu mới (có xác nhận lại), tự chọn vô hiệu hoá các phiên khác; frontend gọi được hết các API đó và hiển thị đúng số liệu thật.

## 2. Phạm vi / Không làm

- Làm: `POST /forgot-password` (đã có, sửa TTL 15 -> 30 phút theo mockup; thêm cửa sổ đếm lượt 2026-10-17), `GET /reset-password?token=` (mới: validate link + trả danh tính), `POST /reset-password` (hợp đồng mới: token + password + confirmPassword + signOutEverywhere), thu hồi phiên qua claim `tv` ở `POST /refresh`, 2 màn frontend dựng lại theo mockup.
- Không làm: "Verify via SMS code" (không có API SMS — nút hiển thị notice "not available yet"); đếm số phiên bị thu hồi ("COMPLETED (2 SESSIONS)") vì hệ thống không có bảng session, chỉ có `token_version`; revocation tức thời access token (access token vẫn hạn 24h — xem mục 9); email thật (SMTP chưa bật, link nằm trong log).

## 3. API thật dùng được

| Endpoint | Method | Response liên quan | Ghi chú |
|---|---|---|---|
| `/api/v1/auth/forgot-password` | POST | Luôn 200 `ApiResponse{message}` với cùng một message chung (kể cả lúc bị chặn — không lộ email tồn tại hay không) | token UUID, TTL 30 phút; mỗi lượt ghi 1 dòng `password_reset_attempts`; ≥5 lượt/email hoặc ≥10 lượt/IP trong 15 phút thì bỏ qua (không ghi đè token, không gửi email) |
| `/api/v1/auth/reset-password?token=` | GET | 200 `{fullName, email, role, remainingSeconds}`; 401 "không hợp lệ" / "đã hết hạn" | route công khai, dùng cho màn đặt mật khẩu mới |
| `/api/v1/auth/reset-password` | POST | 200 `{eventId, signedOutEverywhere}`; 400 thiếu field; 401 sai confirm / mật khẩu yếu / token sai-hết hạn | token chỉ dùng 1 lần, thành công là xoá `reset_token` |
| `/api/v1/auth/refresh` | POST | 200 token mới; 401 "Phiên đăng nhập không còn hiệu lực" | so `tv` trong refresh token với `users.token_version` |
| `/api/v1/auth/login` | POST | `AuthResponse` | refresh token phát ra mang `tv` hiện tại |

## 4. Ánh xạ số liệu -> UI

| Thành phần mockup | Nguồn | Xử lý khi trống/lỗi |
|---|---|---|
| Pill "Verified Session • 12m remaining" | `remainingSeconds` từ GET, đồng hồ đếm ở client (tick 15s) | hết hạn giữa chừng -> đổi sang card "link đã hết hạn" + nút xin link lại |
| Identity chip (avatar tên, email, "Verified Account", role) | `fullName`/`email`/`role` từ GET | role hiển thị "User" vì hệ thống chưa có role CANDIDATE |
| 4 rule mật khẩu + thanh strength | rule client, tập ký tự đặc biệt `@#$%^&+=!` giống hệt `PASSWORD_PATTERN` backend | nút Save disable cho tới khi đủ 4 rule + confirm khớp |
| "Passwords match" badge | so sánh client `password === confirmPassword` | — |
| Modal `EVENT_ID` | `eventId` từ API (`evt_rot_<userId>_<8 ký tự>`) | — |
| Modal `REVOCATION_STATUS` | `signedOutEverywhere` -> "COMPLETED (ALL SESSIONS)" / "SKIPPED (THIS DEVICE ONLY)" | không bịa số phiên |
| "Check your inbox" + "Resend email" | 200 từ forgot-password | cooldown 30s ("Resend available in Ns") rồi mới cho gửi lại; quá ngưỡng server (5 lượt/email trong 15 phút) thì API vẫn 200 nhưng không có email mới — client không phân biệt được, đó là đánh đổi để giữ anti-enumeration |

## 5. Khối mockup BỎ + lý do

- `COMPLETED (2 SESSIONS)`: backend không lưu danh sách session -> chỉ báo trạng thái, không báo số.
- CTA "Continue to Evaluation Console": user vừa mất phiên, vào console được ngay là sai -> "Continue to sign in".
- Chữ "CANDIDATE" trong chip danh tính: tài khoản thật có `ROLE_USER` -> hiển thị role thật.
- Mật khẩu ký tự đặc biệt `(!@#$%^&*)`: backend từ chối `*`, UI liệt kê đúng tập `@#$%^&+=!` để không hứa sai.
- Không tự thêm endpoint "kiểm tra email tồn tại" hay "danh sách thiết bị đang đăng nhập" (luật: không gọi thêm API để làm cho đầy).

## 6. Việc backend cần làm (đã xong)

- `RESET_TOKEN_TTL_MINUTES = 30`; user không tồn tại hoặc `deletedAt != null` -> trả message chung, không ném lỗi (chống dò email).
- Cửa sổ đếm lượt `POST /forgot-password` (thêm 2026-10-17): bảng mới `password_reset_attempts(account, address, created_at)` do Hibernate `ddl-auto: update` tạo; mỗi lượt ghi 1 dòng **trước khi** quyết định (lượt bị chặn cũng phải đếm được ở cửa sổ sau), ngưỡng `MAX_RESET_REQUESTS_PER_EMAIL = 5` và `MAX_RESET_REQUESTS_PER_IP = 10` trong 15 phút. Khi vượt: vẫn trả đúng message chung 200, **không** ghi đè `reset_token` (link đang gửi giữ nguyên), **không** gọi SMTP, chỉ `log.warn`. IP lấy qua `SecurityUtils.getClientIP(request)` nên phải truyền `HttpServletRequest` vào service (controller đổi chữ ký `forgotPassword(email, httpRequest)`).
- `getResetTokenInfo(token)`: cùng luồng kiểm tra với reset (token hợp lệ -> còn hạn -> tài khoản còn sống), trả `remainingSeconds = Duration.between(now, expiry)`.
- `resetPassword(token, newPassword, confirmPassword, signOutEverywhere)`: thứ tự kiểm tra token -> confirm khớp -> pattern -> đổi mật khẩu; `signOutEverywhere` khác `false` (kể cả null) là bump `token_version`; kết quả trả `eventId` + cờ thực thi.
- Claim `tv` trong refresh token (`JwtUtil.generateRefreshToken(account, role, tokenVersion)`), `POST /refresh` từ chối khi `tv` lệch `users.token_version`; cả 4 chỗ phát refresh token (login, Google, GitHub, SSO) đều truyền version hiện tại.
- Thành công: `reset_token`/`expiry` = null, `login_attempts` = 0, `lock_until` = null.

## 7. Test cases

| # | Tình huống | Kết quả mong đợi |
|---|---|---|
| 1 | forgot với email hợp lệ | 200 message chung, token có TTL <= 30 phút |
| 2 | forgot với email không tồn tại | vẫn 200, message y hệt |
| 3 | GET reset-password token thật | 200 + `{fullName, email, role, remainingSeconds<=1800}` |
| 4 | GET/POST với token sai hoặc token hết hạn | 401 "không hợp lệ" / "đã hết hạn", mật khẩu giữ nguyên |
| 5 | POST thiếu confirmPassword | 400 `Validation failed` + `detail.confirmPassword` |
| 6 | POST password != confirmPassword | 401 "Mật khẩu xác nhận không khớp...", không gửi email xác nhận |
| 7 | POST mật khẩu yếu / chỉ có `*` | 401 kèm rule message |
| 8 | POST hợp lệ, signOutEverywhere true/thiếu | 200 `signedOutEverywhere: true`, `token_version` +1, refresh token cũ -> 401 |
| 9 | POST hợp lệ, signOutEverywhere false | 200 `signedOutEverywhere: false`, `token_version` giữ nguyên, refresh token hiện tại -> 200 |
| 10 | Dùng lại token sau khi reset | 401 "không hợp lệ" |
| 11 | Login mật khẩu cũ / mới sau reset | cũ 401 "Mật khẩu sai", mới 200 |
| 12 | forgot lần 1-5 cùng email trong 15 phút | 200 message chung, token mới mỗi lần, 5 dòng `password_reset_attempts` |
| 13 | forgot lần 6 cùng email (vượt 5/15 phút) | vẫn 200 message **y hệt**, token + expiry giữ nguyên, không gửi email, vẫn +1 dòng |
| 14 | forgot email thật khi IP đã đủ 10 lượt | vẫn 200 message chung, `reset_token` vẫn NULL; cũng email đó từ IP khác -> được cấp token |
| 15 | forgot email không tồn tại / email rỗng | 200 message chung; email không tồn tại vẫn +1 dòng (đếm cho IP), email rỗng không ghi dòng nào |

## 8. Cách đã verify

Đo 2026-10-17 trên code production (docker compose, backend image đang chạy, DB `mysqljava`/3307 — backend trỏ 3307 qua `.env`, không phải container `db` trong compose):

- `mvn -B clean test-compile` trong container `maven:3.9.6`: BUILD SUCCESS (main + 4 file test). `mvn clean test` có `PersistenceTest`/`AdminUserControllerIntegrationTest` cần Testcontainers -> chỉ xanh trên CI.
- curl: #1 200 + `timestampdiff` còn 1320s; #2 200 message giống hệt; #3 200 `{"fullName":"Probe Reset User","email":"probe-reset-...","role":"ROLE_USER","remainingSeconds":1206}`; #4 token lạ 401, token back-date 401 (mật khẩu + `token_version` không đổi); #5 400 `detail.confirmPassword`; #6 401 "không khớp"; #7 "Ab1@" và "Abcdef1*" đều 401; #8 200 `evt_rot_18_3002dcc8` + `signedOutEverywhere:true`, `tv` 0 -> 1, refresh token cũ 401; #9 200 `signedOutEverywhere:false`, refresh 200; #10 401; #11 401/200.
- `npx tsc --noEmit` exit 0; eslint 2 trang mới: 0 error, 9 warning `jsx-a11y/anchor-is-valid` (baseline cả thư mục pages 70 warning, không phải lỗi mới).
- `docker compose build frontend && up -d`: `/forgot-password` DOM đủ mockup (badge "Password Recovery", H1, "Work or Personal Email", "Send Reset Link", note 30 phút, "Check your inbox" + "Resend available in 13s" -> "Resend email", SSO + SMS, Help & Security 3 mục, footer). `/reset-password?token=<thật>` render pill "Verified Session • 25m remaining" + chip danh tính từ API; điền "Hello1234" -> Good + 3/4 rule, thiếu special; mismatch -> "Passwords do not match" + nút disable; đủ điều kiện -> Strong + "Passwords match" + nút enable; submit -> modal `COMPLETED (ALL SESSIONS)` / `evt_rot_18_86e30774`; bỏ tick -> `SKIPPED (THIS DEVICE ONLY)`.
- Token giả -> card lỗi "Link đặt lại mật khẩu không hợp lệ"; vào `/reset-password` không query -> "This link is missing or no longer valid."; "Request a new reset link" -> `/forgot-password`; "Verify via SMS code" -> notice; SSO -> `/login`.

Chưa verify ở viewport >=1024px (browser-use hidden viewport — xem `verification.md` mục 4).

Đo lại 2026-10-17 (lô thêm cửa sổ đếm lượt), code production `docker compose` (backend image rebuild, DB `mysqljava`):

- `mvn -B clean test -Dtest=RegisterInterfaceImplTest,PasswordResetServiceImplTest,RegisterRequestValidationTest` trong container `maven:3.9.6`: **16/16 pass**, trong đó `PasswordResetServiceImplTest` 8 test (hạn token 30 phút, đủ điều kiện thì cấp token + ghi dòng, ngưỡng 5/email, ngưỡng 10/IP, email không tồn tại vẫn đếm, email rỗng không chạm repository, SMTP chết vẫn lưu token, reset xong thì lượt sau cấp token khác).
- Boot tạo bảng: `show tables like '%attempt%'` -> `password_reset_attempts` (`id, account, address, created_at datetime(6)`), 2 `@Index` (account+created_at, address+created_at) hiện đúng.
- #12/#13: 5 request liên tiếp cho `probe-reset-...@example.test` từ IP `203.0.113.60` -> 5x 200 message chung, `password_reset_attempts` đếm 1..5, `users.reset_token` đổi sau mỗi lần; request thứ 6 -> vẫn 200 **cùng message**, row thứ 6 vẫn ghi, nhưng `reset_token_expiry` giữ nguyên mốc của request 5 (đo: expiry `15:31:11` = 15:01:11 + 30 phút, trong khi request 6 chạy lúc 15:01:17).
- #14: 10 request với email không tồn tại từ IP `203.0.113.81` -> đủ 10 dòng; request thứ 11 bằng email THẬT (`probe-gap4@example.test`, `reset_token` đang NULL) -> 200 message chung và `reset_token` **vẫn NULL**; cũng request đó đổi sang IP `203.0.113.82` -> 200 và token được cấp (`9543cdbf-...`).
- #15: email không tồn tại -> 200 message chung + 1 dòng; `{"email":"  "}` -> 200 message chung, 0 dòng (`count(*) where address='203.0.113.61'` = 1, chỉ dòng của email không tồn tại).
- Không hồi quy luồng reset: `POST /register` email mới -> 201, `POST /reset-password` vẫn chạy (unit test #8 trong `PasswordResetServiceImplTest` đi đủ đường forgot -> reset -> forgot).

## 9. Câu hỏi mở cho anh Huy

- **Access token vẫn sống 24h sau khi "Sign out of all other sessions".** Refresh token bị vô hiệu ngay (đã đo 401), nhưng request đang cầm access token cũ vẫn qua được `JwtFilter` tới hết hạn. Muốn thu hồi tức thời thì phải rút `EXPIRATION_TIME_ACCESS_TOKEN` xuống ngắn (15 phút) + dựa vào `/refresh`, hoặc thêm blacklist token. Đổi lại là mỗi 15 phút app phải refresh — cần anh chốt.
- **JWT secret ở local**: `docker-compose.yml` chỉ truyền `JWT_SECRET`, còn `JwtUtil` đọc `JWT_ACCESS_SECRET`/`JWT_REFRESH_SECRET` -> local đang dùng fallback commit trong repo (staging/Render sinh secret thật qua `render.yaml`). Có nên sửa compose truyền đủ 2 biến không?
- **Tài khoản probe** `probe-reset-1792242022@example.test` (id 18, DB local) đang để lại, cần xác nhận xoá hay giữ để test tiếp.
