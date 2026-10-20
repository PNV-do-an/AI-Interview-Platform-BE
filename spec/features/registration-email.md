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
auto-activation cũ ở `sign-up.md` mục 9). Nếu email không gửi được thì đăng ký **thất bại**
(502, user bị hoàn tác, decision 2026-10-19: THAY cho quyết định "vẫn 201 + log.warn" cũ),
vì 201-không-kèm-mail tạo ra tài khoản mồ côi mà người dùng không bao giờ kích hoạt được.

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
| 2 | Kênh mail chết (thiếu credential, Google/Resend trả 4xx-5xx) | **502 "Không gửi được email..."**, `@Transactional` hoàn tác user vừa lưu — không tồn tại tài khoản mồ côi mà client được báo thành công |
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

Cấu hình mail — **đã chuyển sang Gmail API (2026-10-19)**: `EmailServiceImpl` đổi lấy access
token từ `https://oauth2.googleapis.com/token` (client id/secret + refresh token, cache tới
trước khi hết hạn 1 giờ) rồi `POST https://gmail.googleapis.com/gmail/v1/users/me/messages/send`
với `raw` = MIME RFC 2822 base64url (subject tiếng Việt mã hóa RFC 2047). Env:
`GMAIL_CLIENT_ID`, `GMAIL_CLIENT_SECRET`, `GMAIL_REFRESH_TOKEN`, `MAIL_FROM` (bắt buộc đúng là
hộp thư đã cấp token), `GMAIL_TOKEN_URI` + `GMAIL_API_BASE_URL` override được để test.

Lịch sử kênh gửi, để không quay lại vết cũ:
- **SMTP (JavaMailSender)** — bỏ 2026-10-18: Render free tier chặn outbound port 25/465/587
  (changelog chính thức, hiệu lực 26/09/2025), nên mọi attempt gửi từ staging chết ở TCP.
- **Resend HTTP API** — bỏ 2026-10-19: tài khoản chưa verify domain nên chỉ dùng được
  `onboarding@resend.dev`, và địa chỉ đó **chỉ giao thư cho chính email chủ tài khoản** (đo:
  403 `validation_error` với mọi recipient lạ, kể cả plus-addressing). Hệ quả đo được: user thật
  đăng ký vẫn nhận 201 nhưng không bao giờ nhận link → kẹt 401 vĩnh viễn. Muốn dùng lại Resend
  thì phải verify domain + đổi `MAIL_FROM`; đó là việc hạ tầng, không phải việc code.
- `Dockerfile` hiện tại cho Render giữ nguyên; hướng Vercel Container Runtime (`Dockerfile.vercel`)
  đã thử và **bỏ** — beta không phát hiện file, build emit static rồi 404 mọi path.

### 5. Các bước thực hiện (theo đúng thứ tự — bước 1 chưa xong thì chưa làm bước 3)

1. **Cấp email provider thật** — ĐỔI 2026-10-19: code đã chuyển sang Gmail API, việc còn lại là
   bật Gmail API trên chính Google Cloud project đang dùng cho Login with Google, rồi set
   `GMAIL_CLIENT_ID` / `GMAIL_CLIENT_SECRET` / `GMAIL_REFRESH_TOKEN` / `MAIL_FROM` (hướng dẫn
   từng bước trong `.env.example`; lưu ý chuyển consent screen sang "In production" vì ở chế độ
   Testing thì refresh token hết hạn sau 7 ngày):
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
   - `RegisterInterfaceImplTest`: case đăng ký hợp lệ + `emailSendFailureBreaksRegistration`
     (đảo 2026-10-19: mail fail → 502, kỳ vọng cũ "vẫn 201" đã bỏ).
   - `PersistenceTest.verificationEmailFailureRollsBackRegistration` (đảo 2026-10-19:
     mail fail → `userRepository.count()==0`, không còn user `enabled=false` sót lại).
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

Đã đo (kênh Gmail API, staging `aiinterview-vn.vercel.app` build `f773246`, 2026-10-19):
- [x] `POST /register` → 201 và **inbox nhận mail thật** (hộp thư người lạ dựng qua mail.tm), user `enabled=0` còn token.
- [x] `POST /login` ngay sau register → **401** "Tài khoản chưa được kích hoạt...".
- [x] Mở link `FRONTEND_URL/verify?token=` → 200; `POST /login` lại → **200** vào được app.
- [x] `POST /resend-verification` cho user chưa verify → mail thứ hai tới inbox (token cũ hỏng, token mới chạy).
- [x] `mvn -B test` xanh (102/102, gồm Testcontainers chạy được trên máy local).

**Đo 2026-10-20 (build `639997f`, e2e local: app + MySQL container + fake Gmail API, không đụng hộp thư thật):**
- `POST /register` → **201**, app log `Mail da giao cho Gmail API: to=..., messageId=fake-message-id` —
  mỗi lần gửi có một dòng bằng chứng phía server (trước đây chỉ suy ra từ HTTP 201).
- `POST /register` trùng email **chưa verify** → **409** "Email này đã đăng ký nhưng chưa xác thực.
  Vui lòng kiểm tra hộp thư (kể cả thư mục Spam), hoặc bấm 'Gửi lại link xác thực'." (email đã verify
  vẫn trả "Email đã tồn tại trong hệ thống"). FE render luôn nút gửi lại ở màn Register — đã đo trên
  browser: 409 → bấm nút → `POST /resend-verification` **200** → đổi nhãn "Verification email sent" + đếm ngược.
- Login trước verify → **401**; verify bằng token lấy từ chính body mail → **200**; login lại → **200**.
- Regression hợp đồng mail fail (`/FAIL` bắt Gmail API trả 503) → `POST /register` **502**
  "Không gửi được email...", `SELECT COUNT(*)` user mới = **0** (rollback), log "Gmail API tu choi gui mail".

**Đo 2026-10-20 (build `5a4ac3b`, e2e local: app + MySQL container + fake Gmail API):**
- `POST /auth/refresh`: token hợp lệ → **200** "Token refreshed"; token **thiếu** (`{}`), token rác
  (`abc.def.ghi`), token **sai chữ ký**, token **hết hạn** → **401** "Phiên đăng nhập không còn hiệu lực.
  Vui lòng đăng nhập lại." (trước bản vá, cả bốn case này là **500** "Lỗi hệ thống" — FE interceptor
  không phân biệt được hết phiên với lỗi server).
- Luồng ĐẠT chạy lại trên image mới: `POST /register` **201** → mail trong fake Gmail chứa token →
  `GET /verify` **200** → `POST /login` **200**.
- Lỗi **đổi access token** (khác lỗi gửi mail): cho `GMAIL_TOKEN_URI` trả 404 (mô phỏng Google
  từ chối client đã bị thu hồi) và trỏ vào host không resolve được (mô phỏng mất mạng) →
  `POST /register` **502** + rollback (0 user) kèm log "Khong doi duoc access token tu ..." ghi rõ
  biến nào cần kiểm tra. Trước bản vá hai case này trả 502 nhưng **log trắng**, không có cách nào
  phân biệt refresh token hỏng với kênh gửi hỏng.

### 8. Câu hỏi mở cho anh Huy

- ~~Bật chặn login khi chưa verify email?~~ **ĐÃ CHỐT 2026-10-18: bật** — đưa vào phạm vi
  (mục 2) và plan (mục 5). Điều kiện: hoàn thành bước 1–2 (SMTP thật + đo e2e) trước bước 3.
- **OAuth Google/GitHub/SSO có phải verify không?** Đề xuất: KHÔNG — email đã được provider
  xác minh, các luồng này (`GoogleAuthInterfaceImpl:79`, `GithubAuthInterfaceImpl:79`) giữ
  `enabled=true` như hiện tại.
- **Mailbox gửi** dùng Gmail App Password (nhanh, đủ cho dev/staging) hay đăng ký provider
  có quota rõ (SES/SendGrid) ngay từ đầu cho production? ~~Đề xuất: Gmail cho bước này~~
  **ĐÃ CHỐT 2026-10-19: Gmail API** (không cần domain/SES, free). SMTP bị Render chặn port
  25/465/587, Resend bị loại vì từ chối recipient lạ.
