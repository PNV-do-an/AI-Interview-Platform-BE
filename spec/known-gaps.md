# Lỗ hổng đã biết (backend + API + UI)

Mỗi mục ghi cách kiểm chứng. "Đã đo" = tự chạy; "agent báo" = ghi nhận từ agent, chưa tự xác minh lại.

## Chặn luồng chính

1. ~~**Đăng nhập email/password trả 500**~~ — **đã sửa và đã đo 2026-10-17**: `model/entity/LoginAttempt.java` giờ map field `timestamp` sang `@Column(name = "created_at", nullable = false)` (kèm `@Index` columnList đổi theo). Verify: `docker compose up -d --build backend` -> `curl POST /api/v1/auth/login` với `ui.probe.two.1017b@example.com` trả `success:true` + accessToken, và `select ... from login_attempts` có 2 dòng `created_at` đầy đủ.
   **Lưu ý 2026-10-17 (branch `feature/create-api-sign-up`)**: bản sửa này nằm ở commit `48f3899` (branch `feature/create-product-mvp`) nhưng **chưa được merge vào `dev`** — trên dev tip `1f716ed` login email vẫn 500 cho tới khi áp lại nội dung fix (đã áp thủ công trong phiên sign-up, đo lại: login 200). Khi `feature/create-api-sign-up` merge thì 2 bên trùng file, resolve lấy bản có `@Column(name = "created_at")`.
   Gốc rễ vẫn còn: schema tiến hoá bằng `ddl-auto: update` (Flyway `enabled: false`) nên các bản đổi tên field để lại cột xác nhận cũ; khi nào cần đổi tên column thì phải thêm migration, không dựa ddl-auto.
   Test không chặn được bug này: ~~`PersistenceTest` và `AdminUserControllerIntegrationTest` lỗi **môi trường** (Testcontainers khởi động `mysql:8.0` xong không kết nối được `jdbc:mysql://172.17.0.1:<port>/test` trên máy Windows này)~~ -> **đã gỡ được hạn chế 2026-10-19**: chạy maven trong container có mount docker socket + `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` + `--add-host=host.docker.internal:host-gateway` + `TESTCONTAINERS_RYUK_DISABLED=true` thì cả hai class xanh ngay trên máy này (lệnh đầy đủ trong `specs/001-admin-user-management/quickstart.md`). `SsoEmailDomainPolicyTest` (3 test) pass.
   Đăng nhập UI hiện đi được cả đường email lẫn OAuth.

1b. **CI `Backend tests` đỏ trên dev `e4b2c53` (PR #21 sign-up)** — **đã sửa + CI đã xác nhận xanh** (PR #22 `7030c8a`, run 39024415101 `success`): hai test Testcontainers **chỉ chạy thật trên CI** (ubuntu có Docker), còn máy Windows này chúng chết ở bước "find a valid Docker environment" trước khi chạy bất kỳ assertion nào -> local "xanh" là ảo giác. `PersistenceTest` vẫn assert hành vi register cũ (thành công ghi 1 dòng `register_attempts` với `counterFail=0`; mỗi lần fail cộng đúp counter lên cùng 1 dòng; `locked` sau 5 fail) trong khi contract mới (đã chốt ở `spec/features/sign-up.md`) là window counting: mỗi fail 1 dòng mới timestamp=now, thành công **0 dòng**, chặn theo đếm-trong-5-phút. Đã cập nhật 3 case: `registrationAndVerificationKeepEnabledProfileAfterRestart`, `invalidRegistrationsRecordOneAttemptPerFailureAndWindowLimitBlocksEmailThenIp` (thay case counter cũ), `verificationEmailFailureStillPersistsRegistration`.
    **Luật làm việc rút ra:** mọi đổi hành vi register/login phải đối chiếu `PersistenceTest` (test chỉ đỏ trên CI); cách verify đáng tin nhất là push branch + để Actions chạy full suite trước khi merge dev.

1c. **Register trên staging treo hàng phút -> user bấm "Create account" không bao giờ thấy 201** — **đã sửa + đã đo 2026-10-17**: `application.yml` để `mail.host: ${MAIL_HOST:smtp.gmail.com}` nhưng JavaMail mặc định **không có timeout connect** (`timeout -1`). Đo trên `ai-interview-backend-staging.onrender.com`: `POST /auth/register` **>90s không có response**, còn `POST /auth/login` trả 401 sau **0.28s** và `forgot-password` trả 200 sau **0.26s** -> DB/Render nhanh, chỉ nghẽn ở bước gửi email; tài khoản probe vẫn được tạo thật (login bằng email probe trả `success:true` ở call sau), nên lần bấm thứ hai của user dính **409 "Email đã tồn tại"** trong khi lần một hiển thị treo.
   Fix: `spring.mail.properties.mail.smtp.connectiontimeout=3000`, `timeout=5000`, `writetimeout=5000` -> SMTP chết thì `try/catch` trong service bắt được, request trả 201 sau ~3s thay vì treo vô hạn.
   Đo chứng minh timeout có hiệu lực (`MAIL_HOST` trỏ vào IP im lặng 192.0.2.1): ảnh cũ log `Couldn't connect to host, port: 192.0.2.1, 587; timeout -1`; ảnh mới log `... timeout 3000`. Đo không hồi quy (env bình thường): register **201 / 5.6s**, email trùng **409 / 0.04s**, login **200 / 0.17s**.
   **Đo lại sau deploy staging (PR #23 merge `97f252c`, run `39026902828` xanh cả 2 job)**: `POST /auth/register` **201 / 3.84s** (call đầu sau cold start 20.8s), email trùng **409 / 0.33s**, login **200 / 1.22s** -> hết treo >90s. "Backend tests" success trên run này cũng là bằng chứng `PersistenceTest` đã chạy thật và pass với contract window counting.
   Việc còn mở: SMTP staging chưa có credential thật (`MAIL_PASSWORD` rỗng) nên email xác thực vẫn không gửi đi được — đã chốt auto-activation nên luồng đăng ký không bị chặn; khi nào có SMTP thì set `MAIL_*` trên Render.
   Liên quan khi chạy local: `docker-compose.yml` không forward `MAIL_TEST_CONNECTION`, backend boot với default `true`, nên nếu `MAIL_HOST` chỉ tới host từ chối kết nối thì app **crash ngay lúc boot** ("Mail server is not available") — đó là cấu hình, không phải bug code.
   **Cập nhật 2026-10-19:** hai đoạn trên chỉ còn giá trị lịch sử. Kênh mail đã chuyển sang **Gmail API** (Render free chặn outbound 25/465/587), `spring.mail.*` / `MAIL_HOST` / `MAIL_PASSWORD` / `MAIL_TEST_CONNECTION` không còn trong `application.yml` và không còn trong `render.yaml`; hợp đồng lỗi mới là mail fail → **502 + hoàn tác tạo tài khoản** (chi tiết: `features/registration-email.md`, `features/password-reset.md`).

1d. **Hai rule rate-limit đăng ký không bao giờ bật qua HTTP + `/forgot-password` không giới hạn lượt** — **đã sửa + đã đo 2026-10-17**:
   - (1) `RegisterRequest` từng nhân bản rule độ mạnh mật khẩu bằng `@Pattern`/`@Size`, nên `@Valid` trả 400 ngay ở controller và request không tới được nhánh ghi `register_attempts` trong service. Hệ quả đo được: 5 request mật khẩu yếu + 1 request trùng email -> `register_attempts` vẫn **0 dòng**, tức ngưỡng "≥5/email" và "≥10/IP trong 5 phút" chết trên đường HTTP (unit test gọi thẳng service nên vẫn xanh — đó là lý do bug sống sót). Cách sửa: DTO chỉ giữ `@NotBlank`/`@Email`, rule độ mạnh chỉ còn trong `RegisterInterfaceImpl` (vẫn chặn trước khi ghi user, 401), nhánh email trùng 409 cũng ghi attempt. Đo lại: request yếu #1..#5 -> 401 + 5 dòng; #6 (mật khẩu hợp lệ) -> 401 "Vui lòng thử lại sau 5 phút", không tạo user; 10 attempt từ một IP -> request #11 (email mới, mật khẩu hợp lệ) bị chặn "hoạt động bất thường"; email trùng -> vẫn 409 và có thêm 1 dòng attempt. Chi tiết số liệu: `features/sign-up.md` mục 8 "Đo lại lô 2".
   - (2) `POST /forgot-password` không đếm gì cả: mỗi lần gọi là ghi đè `reset_token` và gọi SMTP lại, nên một script dò email vừa spam mailbox vừa làm link trong email trước đó chết giữa chừng. Cách sửa: bảng `password_reset_attempts(account, address, created_at)` + ngưỡng 15 phút (≥5/email, ≥10/IP); vượt ngưỡng thì **không** đổi token, **không** gọi SMTP, nhưng response vẫn là 200 với đúng message chung để không lộ email tồn tại hay không. Chi tiết + số liệu: `features/password-reset.md` mục 6 và 8.
   - Giới hạn còn lại của kiểu đếm này: cửa sổ tính bằng số dòng trong bảng, không có job dọn -> bảng lớn dần theo lưu lượng (chưa cần xử lý ở quy mô đồ án); request bị `@Valid` chặn từ sớm (thiếu fullName, email sai format) không ghi attempt, nhưng những request đó cũng không chạm tới bước tạo user hay gửi email.

2. **DB local gần như trống** — đã đo 2026-10-17: `job_descriptions` 0, `applications` 0, `interview_sessions` 0, `interview_slots` 0, `candidate_profiles` 0, `orders` 0; chỉ `users` 13, `service_packages` 2, `service_package_features` 15, `admin_audit_log` 12, `register_attempts` 18, `password_reset_attempts` 20, `login_attempts` 19 (các dòng attempt là của lô test rate-limit 2026-10-17, tự hết đếm sau 5/15 phút). Tên bảng job là `job_descriptions` (không phải `jobs`).
   Hệ quả: mọi màn `DATA` chỉ soi được empty state; muốn thấy bảng có dòng thì phải tạo dữ liệu qua chính app (đăng tin bằng role interviewer + ứng tuyển bằng candidate), không insert tay.

## Thiếu endpoint (là lý do các khối mockup bị bỏ)

- Upload/parse file CV (PDF/DOCX): `cv.service` chỉ có `analyzeCv` nhận **text**; `CandidateProfile.cvFileName` là chuỗi tự điền.
- 2FA / magic link / device check (camera, mic, speaker, latency): không có API, không gọi MediaDevices.
- Rubric Studio (system prompt, weights, cutoffs), Hiring Analytics (funnel, bias audit, calibration log), Job approval workflow (duyệt tin), Candidate scorecard của employer: không có endpoint.
- Lưu job / bookmark, đánh giá tương thích profile, percentile/benchmark, audio replay của buổi mock, join Meet, test thiết bị: không có.
- Trường dữ liệu mockup cần nhưng model không có: avatar URL, job title, location, phone, LinkedIn/GitHub/portfolio, chứng chỉ, GPA, skill kèm trình độ, ngày hết hạn offer, mức lương offer, timeline trạng thái ứng tuyển, `location` trên `Application`, điểm theo từng tiêu chí (chỉ có `score` từng câu).

## Vấn đề nghiệp vụ (audit MVP, agent báo — chưa tự đo lại)

- **Slot lưu sai giờ:** frontend gửi `toISOString()` trong khi backend nhận `LocalDateTime` (mốc tường, không zone) và container chạy UTC -> nhập 10:00 giờ máy (UTC+7) thành DB `03:00`, lịch calendar vẽ ô 03:00. -> **Đã sửa 2026-10-17**: `MySlotsPage` và `InterviewCalendarPage` gửi thẳng chuỗi local `YYYY-MM-DDTHH:mm:00` (không `toISOString`). Đã đo lại trên production build: nhập 10:00 -> `select interview_slots` = `2026-10-19 10:00:00` (trước fix: `03:00`), ô lịch và `/my-slots` hiển thị đúng. Đường này đúng vì DTO chỉ có `SlotRequest` nhận datetime từ client (đã grep `model/dto/request`, không field date nào khác).
  - Rủi ro còn lại (chưa sửa, cần anh chốt): backend vẫn so `start.isBefore(LocalDateTime.now())` bằng đồng hồ UTC của container, nên slot "tương lai theo giờ VN nhưng đã qua theo UTC" vẫn bị từ chối. Hướng xử lý: đặt `TZ` cho container backend, hoặc bỏ check quá khứ phía server và chặn bằng validation khác.
  - `SlotServiceImpl` trả message tiếng Việt (`validateTimeRange:88`, `:91`) -> controller ưu tiên `data.message`, nên lỗi hiển thị tiếng Việt giữa chrome tiếng Anh. Thêm nữa chuỗi `Khoảng thởi gian` (dòng 88) là typo, không phải tiếng Việt đúng.
  - Đo 2026-10-17: **77** message exception tiếng Việt trong `backend/src/main/java` (`grep -rP 'Exception\("[^"]*[\x{1E00}-\x{1EFF}...]'`). Đang giữ nguyên văn theo luật "dữ liệu động nguyên văn" đã chốt; nếu muốn đồng bộ tiếng Anh thì phải quyết định riêng (ảnh hưởng cả staging + log, không phải việc lặt vặt UI).
- Thanh toán chỉ giả lập: `backend/.../OrderServiceImpl.java:59-76`; `PackagesPage` gọi `payOrder` ngay sau `createOrder`.
- Hạn mức gói (`jobPostLimit`, `durationDays`) không bị chặn khi tạo tin.
- AI chấm là rule-based, không phải model thật.
- **Thu hồi phiên sau reset mật khẩu chỉ chặn được refresh token.** Đã đo 2026-10-17: `POST /auth/refresh` bằng token phát trước khi reset trả 401 "Phiên đăng nhập không còn hiệu lực" (claim `tv` so với `users.token_version`), nhưng access token cũ hạn **24h** vẫn qua `JwtFilter` tới khi hết hạn. Muốn thu hồi tức thời: rút TTL access token xuống ngắn + dựa vào `/refresh`, hoặc blacklist token — cần anh chốt (`features/password-reset.md` mục 9).
- **Không có API danh sách phiên/thiết bị đang đăng nhập** (không có bảng session, chỉ có `users.token_version`) -> mockup "REVOCATION_STATUS: COMPLETED (2 SESSIONS)" render thành trạng thái thật `COMPLETED (ALL SESSIONS)` / `SKIPPED (THIS DEVICE ONLY)`, không đếm số phiên.
- `ViewController`, `AuthorizationController` là endpoint chết.
- Hầu hết bảng không có migration (chỉ V1-V5, mà Flyway đang tắt) -> schema tiến hoá bằng `ddl-auto`, lệch nhau giữa các môi trường (chính là nguyên nhân mục 1).

## Lặt vặt UI

- `UserDetailModal` (admin) lồng `ConfirmDialog` trong overlay có `onClick={onClose}` -> mọi click trong confirm đều bubble lên overlay và đóng luôn modal cha. **Đã sửa** (2026-10-17): overlay chỉ đóng khi `e.target === e.currentTarget`. Chưa đo trạng thái "trước khi sửa" trên trình duyệt, nhưng đọc code thì chắc chắn bug. Đã verify sau sửa: bấm Cancel trong "Confirm account lock" thì "User Details" vẫn mở.
- Audit log admin hiển thị `targetUserId` thô; bộ lọc trạng thái thiếu `DELETED`. -> **BE đã có đồ án thay** (2026-10-19, `feature/mange-user`): `AuditLogResponse` trả thêm `targetEmail`/`targetFullName`/`succeeded`/`reason`, `GET /users/deleted`:list tài khoản đã xóa mềm. **FE chưa đổi** — vẫn hiển thị id và vẫn thiếu DELETED cho tới khi màn admin đọc field mới.

## Quản lý tài khoản (admin) — điểm còn mở (measured 2026-10-19)

- **Guard "admin cuối cùng" không tới được qua HTTP**: `rejectIfLastAdmin` đếm `ROLE_ADMIN` chưa xóa, mà mọi request
  thành công đều có actor là admin còn sống nên count luôn ≥ 2; đường tự thao tác thì self-guard chặn 400 trước
  (đo M-7). Guard này chỉ có giá trị chống race / data-repair — **giữ nguyên theo luật "giữ guard phòng vệ"**,
  không phải code chết bị quên.
- **Không đặt guard admin-cuối cho `lock` và `status=false`** (chỉ `delete` và `role`): chủ đích, vì actor không thể
  tự khóa/tự tắt nên luôn còn ≥1 admin đăng nhập được.
- **`UserDetailResponse.enabled` là trạng thái hiệu dụng, không phải cột raw**: `User.isEnabled()`
  (override `UserDetails`) trả `enabled && deletedAt == null`, nên tài khoản đã xóa mềm luôn báo
  `enabled:false` dù cột `enabled` trong DB vẫn `1` (đo 2026-10-19: `en=1 lk=1 del=yes` → JSON `"enabled":false`).
  FE muốn hiện "vô hiệu hóa do bị xóa" thì dựa `status`/`deletedAt`, đừng suy từ `enabled`.
- **`tokenVersion` không lộ ra API** (kế hoạch từng ghi "detail trả tokenVersion" — đã bỏ, vì FE không cần và nó là
  chi tiết thu hồi phiên nội bộ).
- **Admin không được bật (`enabled=true`) tài khoản còn `verificationToken`**: 400 "Tài khoản chưa xác thực email…".
  Đây là quyết định kỹ thuật của em (theo hợp đồng đã deploy), chưa được anh chốt bằng lời — nếu muốn admin bỏ qua
  bước verify email thì nói, đổi 1 điều kiện trong `AdminUserServiceImpl`.
- **ROLE_STAFF bị chặn 403 ở cả 2 lớp** (Q-12 đã chốt 2026-10-19): đo M-13, 11/11 endpoint → 403 với token Staff,
  401 với anonymous.
- **Bản 002 (directory — stats/invite/resend/revoke/patch/CSV), còn mở sau khi đã đo**
  (số đo: `specs/002-admin-user-directory/quickstart-measure.md`):
  - **`lastLoginAt` không phải "Last Active" như mockup.** C-1 chốt suy từ `login_attempts` nên không sửa schema;
    giá trị trả về là *lần đăng nhập thành công gần nhất*, `null` khi chưa từng đăng nhập. Tài khoản đang online
    nhưng chưa đăng nhập lần nào vẫn hiện `null`, và tài khoản đăng nhập 45 ngày trước vẫn hiện mốc đó (đã đo u3).
    FE đừng đặt nhãn "Last active" rồi suy ra "đang dùng".
  - **Không phân biệt được "lời mời do admin gửi" với "tài khoản tự đăng ký chưa verify"**: C-2 chốt dùng
    `verification_token IS NOT NULL`, không thêm cột, nên hai nguồn này giống hệt nhau trong DB. Cũng vì không có
    cột `invited_by` nên mockup mục "Người mời" **không có dữ liệu thật để hiện** — ẩn đi, đừng bịa.
  - **Người được mời không có mật khẩu, phải tự đặt qua luồng "quên mật khẩu"** (C-4 + `users.password` NOT NULL +
    `/auth/verify` chỉ bật `enabled`, không nhận mật khẩu). Nghiệm vụ: mail mời chỉ có link xác thực; nếu sản phẩm
    muốn link mời cho phép đặt mật khẩu ngay thì phải thêm endpoint nhận `token + password` — chưa làm, ngoài phạm vi.
  - **Không có actor "hệ thống" trong audit** (C-3, giữ R10 của 001: `admin_id NOT NULL`, không bao giờ `0`/"system").
    Dòng "Automated policy check" trong mockup vì thế **không có nguồn dữ liệu**; anh Huy nhận phần xóa dòng đó khỏi mockup.
  - **409 trùng email khi "Add User" không ghi audit `succeeded=0`** (đã đo: 2 request 409 → audit vẫn 4 dòng),
    vì `attempt()` của 001 chỉ ghi khi target đã tồn tại. Mọi bị chặn trên user có thật (revoke/resend/patch) vẫn ghi
    `succeeded=0` + `reason`.
  - **Badge `stats` KHÔNG áp dụng bộ lọc `status`** (chủ đích, để mỗi tab hiện số của chính tab đó với cùng
    `search`/`role`). Hệ quả FE phải biết: `active + pending + locked + disabled` **có thể > total** vì `PENDING`
    chồng `INACTIVE` (đã đo: 5+2+1+3 > 9). Muốn "đếm theo tab đang xem" thì gọi lại `/users/stats` kèm `search`/`role`
    của tab đó, không suy từ con số đang hiển thị.
  - **Đường vượt trần CSV tải 10.001 entity để lấy `totalElements`** rồi mới 400 (đo 0.60s cho request 400 ở DB
    10.010 dòng). Đổi lại là code đơn giản, một nguồn predicate; nếu sau này trần tăng thì thêm `count` riêng,
    chưa làm vì chưa cần.
  - `PATCH /users/{id}` với email **bằng đúng email hiện tại** của chính admin đang đăng nhập trả **200**
    (coi như không đổi email), chỉ địa chỉ *khác* mới bị 400 "Không thể đổi email của chính tài khoản quản trị…".
    Hành vi nhất quán với "không có gì thay đổi", nhưng đừng trông đợi 400 cho case nhập lại email cũ.
- ~~`StarDisplay` trong `models/Review.model.tsx` vẫn dùng inline style~~ -> đã đổi sang class Tailwind (`text-amber-500`), 2026-10-17.
- Nhãn chrome tiếng Việt: **đã dọn xong** (2026-10-17). Gồm 12 trang agent A/B/C dịch + `MyApplicationsPage`, `MockInterviewsPage`, `MockInterviewSessionPage`, `MockInterviewResultPage`, `CandidateProfilePage`, `JobDetailPage`, `UnauthorizedPage`, `UserDetailModal`, `ConfirmDialog` (default `Confirm`/`Cancel`), `ui.tsx` (`LoadingState` mặc định "Loading..."), `INTERVIEW_MODE_LABELS`, và ~90 message `setError`/`setSuccessMsg` trong `controllers/`. Ngoại lệ có chủ đích: `CvAnalyzerPage`, `CoverLettersPage` + message trong `useCvController` (hai màn này chưa có mockup nào khác).
  - Còn giữ: `toLocaleString/toLocaleDateString('vi-VN')` (định dạng dữ liệu, không phải nhãn), comment code tiếng Việt, `formatVnd`.
- Scan lại bằng: `grep -rnP '[\x{00C0}-\x{00FF}\x{0100}-\x{017F}\x{1E00}-\x{1EFF}]' src/views src/controllers src/models --include='*.tsx' --include='*.ts'` rồi loại dòng comment (chạy từ `frontend/`).
- Các trang app không có trong 29 mockup (`CvAnalyzer`, `CoverLetters`, `BugReports`, `EmployerReviews`, `PackageManagement`, `AdminFeedback`) đang dùng primitive Stitch nhưng chưa từng được đối chiếu mockup nào -> mặc định là "không có thiết kế", muốn chuẩn hoá phải yêu cầu Stitch màn mới.

## Quyết định đã chốt (để khỏi hỏi lại)

- Phạm vi: UI bám từng mockup Stitch, không tự thiết kế theo spec; luồng ứng viên làm trước.
- 7+ màn chưa có backend -> làm **UI tĩnh đúng mockup**, có route nhưng **không** gắn sidebar.
- Số liệu: chỉ hiện dữ liệu thật, phần mockup bịa thì ẩn.
- Ngôn ngữ: chrome tiếng Anh theo mockup, dữ liệu động nguyên văn.
- Sidebar lọc theo role; `ROLE_USER` chỉ thấy Candidate Workspace.
- `interviewai_public_job_board` là màn tĩnh ở `/opportunities`; `/jobs` trong app vẫn là board thật sau đăng nhập.
