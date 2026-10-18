# Registration email (API đăng ký thì gửi mail xác thực)

Trạng thái: `in-progress` — 2026-10-18 anh Huy chốt: **phải xác nhận trong mail mới đăng nhập được**
(đảo quyết định auto-activation cũ ở `sign-up.md` mục 9). Spec/plan dưới đây đã cập nhật theo chốt này;
SMTP thật từ "việc còn mở" nâng thành **điều kiện tiên quyết** trước khi bật chặn.
Người/phiên tạo: 2026-10-18, branch `feature/create-api-sendmail`
Mockup nguồn: không có mockup riêng — màn hình dùng chung với `features/sign-up.md`

> Tài liệu này làm theo quy trình Spec-Driven Development: phần SPEC (mục 1–3) mô tả
> **what/why** không nhắc giải pháp; phần PLAN (mục 4–7) mô tả **how** trên code thật.
> Bản đầy đủ của mọi tài liệu nằm ở superrepo `AI-Interview-Platform-deploy-staging/spec/`.

---

## PHẦN SPEC (what / why)

### 1. Mục tiêu

Người dùng đăng ký tài khoản xong thì nhận được một email xác thực chứa link hợp lệ;
**chỉ sau khi bấm link đó mới đăng nhập được** (chốt 2026-10-18, thay quyết định
auto-activation cũ ở `sign-up.md` mục 9). Nếu email không gửi được thì việc đăng ký vẫn
201 và user thoát bằng `POST /resend-verification` khi SMTP hoạt động lại.

### 2. Phạm vi / Không làm

- Làm: bật chặn đăng nhập khi chưa verify (tạo user với `enabled=false` lúc register);
  ghi nhận hợp đồng API của luồng "đăng ký → gửi mail xác thực" (register, verify,
  resend-verification); hành vi khi gửi mail lỗi; cấp SMTP thật ở các môi trường —
  **bắt buộc hoàn thành trước khi bật chặn**, nếu không thì không ai đăng ký-rồi-đăng-nhập
  được ở env chưa có mail.
- Không làm: HTML template / provider SES-SendGrid (chưa thuộc phạm vi lần này, mail
  plain-text là đủ); 2FA / magic link; đổi hành vi OAuth Google/GitHub/SSO (đề xuất giữ
  enabled=true vì provider đã xác minh email — chờ chốt ở mục 8).

### 3. Hợp đồng hành vi (acceptance, không phụ thuộc công nghệ)

| # | Tình huống | Kết quả mong đợi |
|---|---|---|
| 1 | Đăng ký hợp lệ | 201 + user tạo mới với `enabled=false` (chưa login được) + **một email xác thực được phát đi** với link mang token của chính user đó |
| 2 | SMTP chết / thiếu credential | vẫn 201, user vẫn tạo, lỗi mail chỉ ghi log — đăng ký không bao giờ chết vì mail; user chưa verify thì login bị 401, đường thoát là resend |
| 3 | Bấm link `.../verify?token=<token thật>` | 200, tài khoản được kích hoạt, token bị xoá không dùng lại được |
| 4 | Token sai hoặc quá 24 giờ | 401 kèm thông điệp tương ứng |
| 5 | User chưa verify bấm gửi lại | token mới 24 giờ được tạo và mail thứ hai gửi đi; user đã enabled thì 401 |
| 6 | Đăng ký fail (trùng email / mật khẩu yếu / chặn rate-limit) | không có mail nào được gửi (mail chỉ chạy sau khi user đã lưu) |
| 7 | **POST /login khi chưa bấm link verify** | 401 "Tài khoản chưa được kích hoạt. Vui lòng kiểm tra email." (hành vi đã có sẵn trong `LoginInterfaceImpl`, chỉ thành thật khi register đổi `enabled`) |
| 8 | POST /login sau khi verify | 200, vào được app |

---

## PHẦN PLAN (how — trên code hiện tại)

### 4. Kiến trúc hiện tại (đã verify bằng code ngày 2026-10-18)

```
POST /api/v1/auth/register
  └─ AuthController.register()                    controller/auth/AuthController.java:51
       └─ RegisterInterfaceImpl.register()         service/impl/RegisterInterfaceImpl.java:39
            ├─ rate-limit cửa sổ 5 phút (IP ≥10, email ≥5) — bảng register_attempts
            ├─ existsByEmail → 409 | PASSWORD_PATTERN → 401 (mỗi nhánh ghi 1 dòng attempt)
            ├─ lưu User: ROLE_USER, enabled=true, verificationToken=UUID, expiry=+24h
            │    (enabled=true là auto-activation cũ — bước 2 bên dưới đổi thành false)
            └─ try { emailService.sendVerificationEmail(...) } catch → chỉ log.warn  (L89-93)
                 └─ EmailServiceImpl               service/impl/EmailServiceImpl.java:19
                      JavaMailSender + SimpleMailMessage (plain-text),
                      link = ${app.frontend-url}/verify?token=<uuid>

GET  /api/v1/auth/verify?token=    → RegisterInterfaceImpl.verifyEmail()   (L104-119)
POST /api/v1/auth/resend-verification → RegisterInterfaceImpl.resendVerification() (L121-140)

CHẶN LOGIN (đã có sẵn, không phải viết mới):
  LoginInterfaceImpl.java:57-59  !user.isEnabled() → 401 "Tài khoản chưa được kích hoạt.
                                 Vui lòng kiểm tra email."
  JwtFilter.java:49              token của user !enabled/locked/deleted → 401
  User.java:45                   mặc định enabled=false — RegisterInterfaceImpl:82 đang set tay true
```

Cấu hình mail — **đã chuyển sang Resend HTTP API (2026-10-18)**: `EmailServiceImpl` dùng
`RestClient` gọi `POST https://api.resend.com/emails` (key `RESEND_API_KEY`, địa chỉ gửi
`MAIL_FROM`, `RESEND_BASE_URL` override được để test); không còn `spring-boot-starter-mail`,
`spring.mail.*` và 3 timeout SMTP. Lý do: HTTP API không phụ thuộc port SMTP (25 bị nhiều
nền tảng serverless chặn) và Resend chạy y hệt trên Render. Deploy giữ `Dockerfile` hiện tại cho
Render; hướng Vercel Container Runtime (`Dockerfile.vercel`) đã thử và **bỏ** — beta không
phát hiện file, build emit static rồi 404 mọi path.

### 5. Các bước thực hiện (theo đúng thứ tự — bước 1 chưa xong thì chưa làm bước 3)

1. **Cấp email provider thật** — ĐỔI 2026-10-18: code đã chuyển sang Resend HTTP API, nên việc
   còn lại là tạo account resend.com → lấy API key → set env `RESEND_API_KEY` (+ `MAIL_FROM`
   khi đã verify domain DNS) cho từng môi trường:
   - Local/dev: `.env` (theo `.env.example`).
   - Staging (Render): env của service — chỉ đổi cấu hình, không sửa code.
   Sau bước này mail verify **đã gửi thật** nhưng user vẫn login được ngay (chưa đổi `enabled`).
2. **Đo thật một lượt gửi mail + verify end-to-end** (checklist mục 7) trên dev và staging.
3. **Bật chặn login** — code change rất nhỏ, không thêm cờ config:
   - `RegisterInterfaceImpl.java:82`: `.enabled(true)` → `.enabled(false)`.
   - Message register `RegisterInterfaceImpl.java:95`: bỏ "Bạn có thể đăng nhập ngay." →
     "Đăng ký thành công! Vui lòng kiểm tra email để kích hoạt tài khoản."
   - Không sửa gì thêm ở login/verify: `LoginInterfaceImpl:57` + `JwtFilter:49` +
     `verifyEmail()` đã xử lý đúng.
4. **Sửa test đang pin hành vi cũ** (test đang assert user mới đăng ký là enabled — phải
   đảo kỳ vọng theo hợp đồng mới ở mục 3):
   - `RegisterInterfaceImplTest`: case đăng ký hợp lệ + `emailSendFailureDoesNotBreakRegistration`
     (vẫn phải 201 khi mail fail, nhưng user `enabled=false`).
   - `PersistenceTest.verificationEmailFailureStillPersistsRegistration` (L584-604):
     `assertTrue(storedUser(email).isEnabled())` → `assertFalse` + token còn trong DB.
   - Thêm case mới: login trước verify → 401; login sau verify → 200.
5. **Việc ngoài repo BE** (ghi để bàn giao FE): `RegisterPage` đang chuyển `/login?registered=true`
   kèm banner "Registration successful! Please sign in to continue." — phải đổi thành hướng dẫn
   kiểm tra email; thêm cảnh báo khi login bị 401 "chưa được kích hoạt" + lối bấm resend.

Rủi ro đã biết khi bật chặn: user đăng ký ở env **chưa** cấu hình SMTP sẽ bị kẹt ngoài app
(mail không bao giờ tới). Đó là lý do bước 1–2 bắt buộc trước bước 3; các env kế thừa
`MAIL_PASSWORD` rỗng (mọi env hiện tại theo `known-gaps.md` 1c) **không được deploy bước 3**.

**Chốt 2026-10-18 (buổi làm việc):** anh Huy quyết định làm bước 3 (sửa code bật chặn) TRƯỚC
bước 1–2. Hệ quả chấp nhận: ở dev/staging chưa có `MAIL_*`, tài khoản đăng ký mới sẽ login
bị 401 "chưa được kích hoạt" cho tới khi SMTP thật hoạt động và resend/verify chạy được.
Khi đo (mục 7) phải đo trên env đã có credential.

### 6. Files liên quan

| File | Vai trò |
|---|---|
| `controller/auth/AuthController.java` | 3 endpoint register / verify / resend-verification |
| `service/impl/RegisterInterfaceImpl.java` | điều phối luồng, gọi gửi mail sau khi save |
| `service/EmailService.java` + `impl/EmailServiceImpl.java` | hợp đồng 11 loại mail + hiện thực JavaMailSender |
| `model/dto/request/RegisterRequest.java` | DTO vào (`@NotBlank`/`@Email`; rule mật khẩu để ở service — lý do ghi tại `sign-up.md` mục 6) |
| `model/entity/RegisterAttempt.java` | dòng đếm thất bại phục vụ rate-limit |
| `src/main/resources/application.yml` | `spring.mail.*` + SMTP timeouts + `app.frontend-url` |
| `.env.example` / `render.yaml` | khai báo env MAIL_* |
| `service/impl/LoginInterfaceImpl.java` | **đã chặn sẵn** user `!enabled` (L57-59) — không sửa |
| `security/jwt/JwtFilter.java` | đã chặn token của user `!enabled` (L49) — không sửa |
| `src/test/.../RegisterInterfaceImplTest.java` | 6 unit test; có case pin `enabled=true` phải đảo |
| `src/test/.../PersistenceTest.java` | case `verificationEmailFailureStillPersistsRegistration` (chỉ đo thật trên CI) |

### 7. Cách verify (bắt buộc có số đo thật)

Đo 2026-10-18 (bước 3 đã làm — code bật chặn + đảo test; chạy trong container `maven:3.9.6-eclipse-temurin-17`):
- `RegisterInterfaceImplTest,PasswordResetServiceImplTest,RegisterRequestValidationTest,SsoEmailDomainPolicyTest`
  = **19/19 pass, exit 0**. `registersDisabledUserAwaitingEmailVerification...` giờ assert
  `assertFalse(user.isEnabled())` + message chứa "kiểm tra email" → pass, xác nhận user tạo ra **chưa enabled**.
- `PersistenceTest` (Testcontainers) **chỉ xanh thật trên CI** — trên Windows container lồng
  không bắt được nhau; 2 assertion đã đảo (`verificationEmailFailureStillPersistsRegistration`
  + `registrationAndVerification...`) **đã compile sạch** (test-compile chạy trong lô trên).
- Rủi ro còn treo: chưa đo e2e nhận mail thật + login 401-trước-verify trên env có `MAIL_*`
  (bước 1–2 chưa làm — xem checklist bên dưới).

Đã đo trước đó (trích `sign-up.md` mục 8 và `known-gaps.md` 1c, ngày 2026-10-17):
- register hợp lệ → 201 (5.6s local; 3.84s staging sau PR #23, cold-start call đầu 20.8s);
  email trùng → 409; mật khẩu yếu → 401 + ghi attempt; đủ ngưỡng → chặn đúng rule.
- SMTP chết (`MAIL_HOST=192.0.2.1`): request trả 201 sau ~3s nhờ timeout 3000ms, không treo.
- `RegisterInterfaceImplTest` pass (6/6, lô 2 chạy cùng 2 class khác = 16/16 pass,
  container `maven:3.9.6`).

**Đo 2026-10-18 (Resend refactor + Dockerfile.vercel, image chạy trên máy anh Huy, Laragon MySQL 3306):**
- `mvn clean test` (container `maven:3.9.6`) 4 class unit: **19/19 pass** sau khi bỏ `starter-mail`.
- `docker build -f Dockerfile.vercel` xanh; container đọc `PORT` (chạy thật với `PORT=9000`).
  (Sau đó deploy thử lên Vercel thất bại: build của Vercel không nhận `Dockerfile.vercel`,
  emit static 200ms → mọi route 404; hướng Vercel backend bị bỏ cùng ngày, file đã gỡ.)
- `POST /register` với `RESEND_API_KEY=re_fake...`: **201 / 1.27s**, log `401 Unauthorized:
  {"message":"API key is invalid"}` từ api.resend.com (wire HTTP đúng, chỉ thiếu key thật),
  DB `enabled=0 + token`. Probe đã xoá.
- Trước đó cùng ngày (bản SMTP): e2e đủ 7 bước register→401 login→verify→200 login→token dùng lại 401
  (`enabled=0`, token UUID, expiry 24h) — hành vi gate không đổi sau refactor.

Cần đo khi có key Resend thật (bước còn lại của plan):
- [ ] `POST /register` → 201 và **inbox nhận mail thật**, trong DB user `enabled=0` còn token.
- [ ] `POST /login` ngay sau register → **401** "Tài khoản chưa được kích hoạt...".
- [ ] Mở link `FRONTEND_URL/verify?token=` → 200; `POST /login` lại → **200** vào được app.
- [ ] `POST /resend-verification` cho user chưa verify → mail thứ hai tới inbox.
- [ ] `mvn -B clean test` xanh sau khi đảo assertion mục 5 bước 4.
- [ ] Ghi số đo vào mục này (thời điểm, môi trường, kết quả từng bước).

### 8. Câu hỏi mở cho anh Huy

- ~~Bật chặn login khi chưa verify email?~~ **ĐÃ CHỐT 2026-10-18: bật** — đưa vào phạm vi
  (mục 2) và plan (mục 5). Điều kiện: hoàn thành bước 1–2 (SMTP thật + đo e2e) trước bước 3.
- **OAuth Google/GitHub/SSO có phải verify không?** Đề xuất: KHÔNG — email đã được provider
  xác minh, các luồng này (`GoogleAuthInterfaceImpl:79`, `GithubAuthInterfaceImpl:79`) giữ
  `enabled=true` như hiện tại.
- **Mailbox gửi** dùng Gmail App Password (nhanh, đủ cho dev/staging) hay đăng ký provider
  có quota rõ (SES/SendGrid) ngay từ đầu cho production? Đề xuất: Gmail cho bước này,
  provider tính khi có user thật.
