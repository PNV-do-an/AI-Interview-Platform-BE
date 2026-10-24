# Quickstart đo — spec 010: API 2FA

## Cách đo

Hai tầng, chạy độc lập:

```bash
# 1) Unit thuần (Mockito, không cần Docker) — 14 test
mvn test -Dtest='TwoFactorServiceImplTest'

# 2) Integration full-loop trên MySQL thật (Testcontainers) — 8 test
mvn test -Dtest='TwoFactorIntegrationTest'
```

`mvn test` một lượt ăn cả hai (CI chạy `mvn -B clean test`).

## Kết quả đo 2026-10-24

- `TwoFactorServiceImplTest`: **14/14** xanh (chạy bằng maven trên host, không cần Docker).
- `TwoFactorIntegrationTest`: **8/8** xanh (Testcontainers `mysql:8.0`).
- Hồi quy toàn repo: **216/216** xanh, 0 fail — bao gồm `AdminUserControllerIntegrationTest` 71
  và `PersistenceTest` 13, tức chữ ký `JwtUtil` đổi (thêm `sessionId`) không làm vỡ luồng nào có
  trước đó.

## Những gì integration test chứng minh bằng API thật

1. Anonymous gọi `GET /auth/2fa` → 401.
2. User **chưa** bật 2FA: login vẫn trả `accessToken` thẳng — hành vi cũ không đổi.
3. Full loop: `setup` → `enable` (OTP tính từ secret thật) → login trả `mfaRequired:true`
   và **không** có `accessToken` → `verify-otp` trả JWT → JWT đó gọi được `GET /auth/me`,
   `twoFactorEnabled=true`.
4. **Challenge token không phải access token:** mang nó làm `Authorization: Bearer` gọi
   `/auth/me` → 401.
5. OTP sai → 401, và dòng `login_attempts` `success=false` được ghi lại.
6. Recovery code: dùng được ở `verify-otp`, **dùng lần hai bị 401** (một lần, có `used_at`).
7. STAFF/USER không được force-disable 2FA người khác → 403.
8. ADMIN force-disable: secret + `totp_enabled` + toàn bộ recovery codes bị xóa, có audit
   `DISABLE_2FA` kèm lý do; sau đó user login trả JWT thẳng (thoát được vì mất thiết bị).
9. Force-disable thiếu `reason` → 400.

## Chưa đo (nằm ngoài kỳ này)

- Chưa đo trên Render staging; deploy theo superrepo sau khi merge `dev`.
- **FE chưa đổi theo hợp đồng mới.** `POST /auth/login` giờ có thể trả `mfaRequired:true`
  thay vì token — FE phải thêm bước nhập OTP, nếu không user bật 2FA sẽ không đăng nhập được.
  Block "2FA" trong admin console cần đọc `twoFactorEnabled` từ `/auth/me` và
  `UserDetailResponse`.
- QR: BE chỉ trả `otpauth://` URI, **không** sinh ảnh QR (không thêm dependency zxing).
  FE tự render.
- Đường SSO/Google/Github không qua 2FA — đã chốt ngoài scope, chưa đo.
- Chưa đo giới hạn brute-force OTP trên môi trường có độ trễ mạng thật.
