# Hướng dẫn test backend (AI Interview Platform)

Tài liệu này ghi lại **toàn bộ** những gì đã làm cho phần unit test: cách chạy, harness tự chế, các lỗi
đã gặp và khoảng trống còn lại. Cập nhật lần cuối: đợt bổ sung test cho **toàn bộ module còn lại**
(489 test / 44 class / 6 bộ, chạy offline, 0 fail).

## 1. Trạng thái hiện tại

| Bộ | Script | Số class | Số test | Kết quả | Cần Docker? |
|---|---|---|---|---|---|
| Dùng chung: `GlobalExceptionHandler`, `SkillKeys`, `SkillMatcher`, validate DTO | `run-shared-tests.ps1` | 4 | 15 | pass | Không |
| Admin: user directory, dashboard, settings, permission, category, position, TopCV import | `run-admin-tests.ps1` | 7 | 123 | pass | Không |
| Folder 00 — Auth | `run-auth-tests.ps1` | 11 | 110 | pass | Không |
| Luồng nghiệp vụ chính (core flow: job / application / slot / CV / cover letter / profile / review / bug report / order / package) | `run-core-tests.ps1` | 14 | 145 | pass | Không |
| Interview: AI sinh câu hỏi, chương trình ôn, mock/avatar, nhắc lịch, kho câu hỏi | `run-interview-tests.ps1` | 6 | 81 | pass | Không |
| Infra: Email (SMTP + Gmail API + dev-log) | `run-infra-tests.ps1` | 2 | 15 | pass | Không |
| **Tổng** | 6 script | **44** | **489** | **0 fail** | Không |

Thời gian chạy: ~3–14 giây cho mỗi bộ sau khi đã có `target/classes`.

**Độ phủ theo lớp:** cả **35/35** class `*ServiceImpl` trong `src/main` đều đã có test trực tiếp, cộng thêm
3 util/handler dùng chung, 2 DTO validation, 1 evaluator AI và 1 controller (`AuthControllerSessionTest`).

> Ghi chú về tên: bộ 124 test cũ từng được gọi là **"P0 core flow"** (P0 = ưu tiên số 0, theo kế hoạch),
> nên script cũ tên là `run-p0.ps1`. Tên đó không nói lên nội dung nên đã đổi thành `run-core-tests.ps1`.
> Jargon "P0" còn sót ở một vài chỗ trong tài liệu chỉ để chỉ bộ test này.

## 2. Cách chạy

### 2.1. Đường chuẩn (Maven) — hiện chưa dùng được

```powershell
.\mvnw.cmd test -Dtest="LoginInterfaceImplTest"
mvn test -Dtest="*ServiceImplTest"
```

Không chạy được trên máy này vì:

- `mvnw.cmd` thiếu `.mvn/wrapper/maven-wrapper.properties` và `.mvn/lib/*`;
- `mvn` không có trong `PATH`.

Khi sửa được wrapper thì đây vẫn là đường chính, vì Surefire tự build `target/classes` và tự lọc
`**/*Test.java`. Mọi test bên dưới đều là unit test thuần nên chạy được cả hai đường.

### 2.2. Harness offline (đang dùng) — `scripts/testcheck/`

```powershell
cd c:\Users\LENOVO\Downloads\AI-Interview-Platform-BE

# Dung chung: exception handler, skill utils, validate DTO (15 test)
powershell -NoProfile -File scripts\testcheck\run-shared-tests.ps1

# Admin: user directory / dashboard / settings / permission / TopCV import (123 test)
powershell -NoProfile -File scripts\testcheck\run-admin-tests.ps1

# Folder 00 - Auth (110 test)
powershell -NoProfile -File scripts\testcheck\run-auth-tests.ps1

# Luong nghiep vu chinh - core flow (145 test)
powershell -NoProfile -File scripts\testcheck\run-core-tests.ps1

# Interview: AI question, study program, mock/avatar, reminder, pool (81 test)
powershell -NoProfile -File scripts\testcheck\run-interview-tests.ps1

# Infra: Email SMTP + Gmail API + dev-log (15 test)
powershell -NoProfile -File scripts\testcheck\run-infra-tests.ps1
```

Muốn chạy hết trong một lần:

```powershell
foreach ($s in 'shared','admin','auth','core','interview','infra') {
  & powershell -NoProfile -File "scripts\testcheck\run-$s-tests.ps1"
  if ($LASTEXITCODE -ne 0) { "BO $s FAIL"; break }
}
```

Điều kiện:

- `JAVA_HOME` trỏ tới JDK 17 (harness gọi `$JAVA_HOME\bin\javac.exe` và `java.exe`);
- **`target/classes` phải tồn tại** (harness không biên dịch lại `src/main`). Nếu đã `clean` thì phải
  build lại phần main trước (bằng IDE, hoặc sửa lại Maven wrapper rồi `mvn -DskipTests compile`);
- Maven repo `%USERPROFILE%\.m2\repository` đã có đủ jar trong allow-list. Thiếu jar nào script in
  `MISSING JAR: ...` và test sẽ fail với `NoClassDefFoundError`.

Kết quả: in summary của JUnit Platform; `exit code = 1` nếu có test fail (dùng được trong CI).

### 2.3. Chạy đúng một class

```powershell
powershell -NoProfile -Command "& scripts\testcheck\compile-and-run.ps1 `
  -SourceList 'src/test/java/com/aiinterview/platform/service/auth/impl/LoginInterfaceImplTest.java' `
  -TestList  'com.aiinterview.platform.service.auth.impl.LoginInterfaceImplTest'"
```

`-SourceList` và `-TestList` đều là **đường dẫn / FQN cách nhau bằng dấu phẩy** (truyền dạng chuỗi,
script tự `-split ','`).

### 2.4. Dọn dẹp

Harness ghi class đã biên dịch và argfile vào `%TEMP%\testcheck`, `%TEMP%\javac-args.txt`,
`%TEMP%\test-cp.txt` — **không** ghi gì vào repo. Muốn dọn:

```powershell
Remove-Item "$env:TEMP\testcheck" -Recurse -Force
Remove-Item "$env:TEMP\javac-args.txt","$env:TEMP\test-cp.txt","$env:TEMP\javac-runner.txt" -Force
```

## 3. Harness gồm những gì

| File | Vai trò |
|---|---|
| `scripts/testcheck/build-classpath.ps1` | Dựng `@argfile` cho `javac` (`%TEMP%\javac-args.txt`) và classpath runtime (`%TEMP%\test-cp.txt`) từ allow-list jar trong `.m2`. |
| `scripts/testcheck/TestRunner.java` | Thay thế `mvn test`: dùng JUnit Platform `Launcher` + `SummaryGeneratingListener`, in summary và `exit 1` khi fail. |
| `scripts/testcheck/compile-and-run.ps1` | Engine: `build-classpath` → `javac` source test → biên dịch `TestRunner` → `java ... TestRunner <FQN...>`. |
| `scripts/testcheck/run-shared-tests.ps1` | 4 class dùng chung: exception handler, skill utils, validate DTO. |
| `scripts/testcheck/run-admin-tests.ps1` | 7 class module admin (user directory, dashboard, settings, permission, category, position, TopCV import). |
| `scripts/testcheck/run-auth-tests.ps1` | 11 class của folder 00 (Auth). |
| `scripts/testcheck/run-core-tests.ps1` | 14 class của luồng nghiệp vụ chính (bộ "P0" cũ). |
| `scripts/testcheck/run-interview-tests.ps1` | 6 class module interview (AI question, study program, mock/avatar, reminder, pool). |
| `scripts/testcheck/run-infra-tests.ps1` | 2 class Email (Gmail API/SMTP wire-level + phân biệt mail bắt buộc vs thông báo). |

Vì sao phải tự chế: mỗi test chỉ cần `javac` + JUnit launcher, không cần Spring context, không cần
Docker. Cách này cho vòng phản hồi ~3 giây, đúng thứ cần khi viết test theo kiểu "liệt kê case".

## 4. Các lỗi đã gặp khi dựng harness (đừng sửa lại thành sai)

1. **`unmappable character ... for encoding windows-1252`** — source và message tiếng Việt là UTF-8,
   `javac` mặc định dùng charset hệ thống trên Windows. Đã thêm `-encoding UTF-8`.
2. **`error: invalid flag: ∩╗┐-proc:none`** — PowerShell `Set-Content -Encoding UTF8` ghi **BOM**, và
   `javac` không đọc được argfile có BOM. Đã ghi lại bằng
   `[System.IO.File]::WriteAllLines($path, $lines, (New-Object System.Text.UTF8Encoding($false)))`.
3. **`error: Invalid filename: C:UsersLENOVO...`** — trong argfile, `\` là ký tự escape. Mọi đường dẫn
   phải đổi sang `/`.
4. **`-File` truyền cả chuỗi phẩy thành 1 phần tử** — tham số phải là `[string]` rồi tự `-split ','`.
5. **`cannot find symbol: JpaRepository` / `findById`** — thiếu `spring-data-jpa` trên classpath;
   `spring-data-commons` một mình không đủ. Đã thêm `spring-data-jpa-3.2.5` + `jakarta.transaction-api`.
6. **`package org.springframework.mock.web does not exist`** — test cũ dùng `MockHttpServletRequest`
   và `ReflectionTestUtils`; hai class này nằm trong `spring-test-6.1.6`.
7. **`Mockito cannot mock this class: TotpVerifier` → `Could not create type` → `class redefinition
   failed: invalid class`** — lỗi này **không** phải lỗi Mockito/ByteBuddy. Nguyên nhân thật là thiếu
   jar `dev.samstevens.totp`; Mockito inline cần nạp (initialize) kiểu được mock, thiếu class trong
   hierarchy thì JVM trả về lỗi khó hiểu đó. Đã thêm `totp-1.7.1`. **Bài học: khi Mockito báo "cannot
   mock this class", đọc `Caused by` tận cuối, thường là `NoClassDefFoundError` của một dependency.**
8. **`hibernate-validator` không khởi tạo được offline** — cần thêm `classmate`, `jboss-logging`,
   `tomcat-embed-el` (và `tomcat-embed-core` để có `jakarta.servlet`).
9. **Version phải khớp `pom.xml`**: Spring Boot 3.2.5 → Spring 6.1.6, Mockito 5.7.0, ByteBuddy 1.14.13.
   Giữ đúng version để harness không che mất lỗi chỉ xảy ra trên build thật.
10. **`package jakarta.mail does not exist`** — `spring-boot-starter-mail` của Boot 3.2.5 kéo
   `org.eclipse.angus:jakarta.mail` (**không** phải `jakarta.mail:jakarta.mail-api`), cộng thêm
   `spring-context-support` (chứa `org.springframework.mail`). Đã thêm 3 jar này vào allow-list.
11. **`NoClassDefFoundError: io/micrometer/observation/ObservationRegistry`** — lỗi **runtime** khi
   `EmailServiceImpl` gọi `RestClient.builder()`: `spring-web` cần `micrometer-observation` +
   `micrometer-commons`. Lỗi này không xuất hiện lúc `javac`, chỉ nổ khi chạy test → phải đọc stack
   trace đến dòng `Caused by` cuối cùng.
12. **Không mock được `HttpClient` một cách đơn giản** — nó là abstract class trong JDK, không phải
   interface. Muốn test code cào web offline (TopCV) thì viết `StubHttpClient extends HttpClient`,
   override `send` + các method vô nghĩa; từ JDK 16 phải override thêm `authenticator()`, và
   `SSLContext.getDefault()` ném checked exception `NoSuchAlgorithmException`.
13. **Entity có constructor riêng thì không dùng builder** — `LoginAttempt` chỉ có
   `(email, ipAddress, success)` và tự gán `timestamp = now()`; `LoginAttempt.builder()`
   sẽ báo `cannot find symbol builder()`.
14. **Tên getter do Lombok sinh ra không phải lúc nào cũng `getX()`**: `boolean locked` → `isLocked()`,
   `boolean succeeded` → `isSucceeded()`, còn `record` → `emailChanged()` (không có tiền tố `get/is`).
   Viết sai sẽ chỉ hiện lúc compile với message `cannot find symbol`.
15. **`InvalidUseOfMatchersException: 6 matchers expected, 1 recorded`** — khi một tham số dùng
   `any()/eq()` thì **tất cả** tham số phải là matcher: `search(null, null, ..., any())` sai,
   phải là `search(isNull(), isNull(), ..., any())`.
16. **Cuối file test tự dư một block `package ...; public class XTest {}`** — IDE/Write tool đôi khi
   ghi thêm; `javac` báo `class, interface, enum, or record expected` trỏ vào dòng đó. Cách sửa: đọc
   phần cuối file và xóa block thừa bằng `SearchReplace` (không xóa nhầm `}` đóng class thật).

## 5. Folder 00 — Auth: endpoint nào được test ở đâu

Controller: `controller/auth/AuthController.java`, `TwoFactorController.java`.

| Endpoint | Xử lý | Test | Trạng thái |
|---|---|---|---|
| `POST /api/v1/auth/register` | `RegisterInterfaceImpl` | `RegisterInterfaceImplTest` (7) + `AuthControllerSessionTest` (201 contract) | Đã test |
| `POST /api/v1/auth/login` | `LoginInterfaceImpl` | `LoginInterfaceImplTest` (14) | Đã test |
| `POST /api/v1/auth/refresh` | `AuthController` | `AuthControllerSessionTest` (6 case refresh) | Đã test |
| `GET /api/v1/auth/me` | `AuthController` | `AuthControllerSessionTest` (3) | Đã test |
| `POST /api/v1/auth/logout` | `AuthController` | `AuthControllerSessionTest` (1) | Đã test |
| `GET /api/v1/auth/{google,github}/client-id` | `*AuthInterfaceImpl.getClientId` | `GoogleAuthInterfaceImplTest`, `GithubAuthInterfaceImplTest` | Đã test |
| `POST /api/v1/auth/google` | `GoogleAuthInterfaceImpl` | `GoogleAuthInterfaceImplTest` (5) | Chỉ nhánh tiền kiểm tra |
| `POST /api/v1/auth/github` | `GithubAuthInterfaceImpl` | `GithubAuthInterfaceImplTest` (5) | Chỉ nhánh tiền kiểm tra |
| `POST /api/v1/auth/verify-otp` | `TwoFactorServiceImpl` | `TwoFactorServiceImplTest` (14) | Đã test (service) |
| `POST /api/v1/auth/forgot-password`, `reset-password` | `PasswordResetServiceImpl` | `PasswordResetServiceImplTest` (8) | Đã test (service) |
| `GET/POST /api/v1/auth/sso*` | `SsoEmailDomainPolicy` | `SsoEmailDomainPolicyTest` (3) | Đã test (policy) |
| 2FA `/api/v1/2fa/*` | `TwoFactorServiceImpl` | `TwoFactorServiceImplTest` (14) | Đã test (service) |
| `GET /auth/verify`, `POST /auth/resend-verification`, controller `/login` nhánh MFA, controller `/verify-otp`, `/sso` | `AuthController` (chỉ delegate) | — | Chưa test (xem §8) |

## 6. Những gì đã viết cho folder 00

### 6.1. `LoginInterfaceImplTest` — 14 case

Trọng tâm: hai bộ đếm rate-limit **khác nhau** (20 lần sai / IP / 15 phút chặn cả thiết bị; 5 lần sai
/ email thì khóa tài khoản) và **thứ tự kiểm tra** (tài khoản bị khóa/chưa kích hoạt không bao giờ được
chạm tới bước so mật khẩu — tránh lộ thông tin).

Các case: IP vượt ngưỡng · lấy IP đầu tiên của `X-Forwarded-For` · email không tồn tại (vẫn ghi
`LoginAttempt` để còn đếm) · tài khoản xóa mềm · chưa xác thực email · admin khóa · khóa tạm còn hiệu
lực (kiểm tra `getLockUntil()`) · khóa đã hết hạn thì reset bộ đếm trước khi so mật khẩu
(`verify(save, times(2))`) · sai mật khẩu lần 3 (chưa khóa) · sai lần 5 (khóa + trả mốc khóa) · đã đủ 5
lần sai trong cửa sổ 15 phút thì khóa **dù mật khẩu đúng** · bật TOTP thì không cấp JWT mà chỉ trả
challenge 5 phút và **không** ghi nhận đăng nhập thành công · đăng nhập thành công (token mang `tv`,
`sid`; `sid` = session vừa tạo) · sau logout token mới mang `tokenVersion` hiện tại.

### 6.2. `AuthControllerSessionTest` — 14 case

Đây là phần logic nằm ngay trong controller, không có service nào che:

- `refresh`: thiếu token · token rác · dùng access token vào ô refresh · `tv` cũ (đổi mật khẩu) ·
  session đã bị thu hồi → **tất cả đều 401 `InvalidAccountException`**, không phải 500, để FE xóa
  storage. Token đời cũ không có `sid` → cấp session mới. Session còn sống → giữ nguyên `sid` và
  **giữ nguyên refresh token**.
- `me`: không có authentication · anonymous token → 401; có authentication → trả đúng id/email/tên/
  phone/role/2FA.
- `logout`: `revokeSessions(email)` — thu hồi **toàn bộ** phiên của tài khoản.
- `register`: HTTP **201**, chỉ trả message, `data == null` (không trả token).
- `client-id`: chuỗi rỗng = chưa bật OAuth (FE ẩn nút); đã bật thì chuyển tiếp đúng giá trị.

**Phát hiện cần biết:** hệ thống **không xoay vòng refresh token** (no rotation). `sid` là neo, `tv` và
`isLive(sid)` là hai cổng thu hồi. Test đang ghi lại đúng hành vi hiện tại — nếu sau này muốn rotation
thì đó là thay đổi hành vi có chủ đích và phải sửa case `refresh_liveSession_keepsSessionAndRefreshToken`.

### 6.3. `GoogleAuthInterfaceImplTest` / `GithubAuthInterfaceImplTest` — 5 case mỗi bên

Chỉ phủ được phần **trước khi gọi mạng**: chưa cấu hình client id/secret → message "chưa được cấu
hình" và **không chạm database**; thiếu token/code → message riêng; `getClientId()` trả `""` khi chưa
cấu hình. Lý do giới hạn ở §8.

## 7. Các module còn lại (đợt 2)

Nguyên tắc của đợt này: **không sửa `src/main`**. Chỗ nào code hiện tại không có điểm tiêm (inject)
để test offline thì ghi vào §8 (khoảng trống) thay vì sửa production. Trọng tâm là lớp `service` — nơi
ở quy tắc nghiệp vụ; controller chỉ được phủ ở phần logic nằm ngay trong controller.

Mỗi module có một script riêng để chạy lẻ khi đang sửa đúng module đó, và tên script nói rõ module
(`run-admin-`, `run-interview-`, ...). Cách chia đợt:

| Đợt | Nội dung | Class | Test |
|---|---|---|---|
| 1 | Folder 00 (Auth) + luồng nghiệp vụ chính | 20 | 70 + 136 = 206 |
| 2 | Admin 123, interview 81, shared 15, infra 15, bổ sung auth 40 + core 9 | 24 | **283** |
| **Tổng** | | **44** | **489** |

### 7.1. Bộ dùng chung — 4 class / 15 test (`run-shared-tests.ps1`)

| Class | Test | Hợp đồng được khoá |
|---|---|---|
| `common/exception/GlobalExceptionHandlerTest` | 3 | Tài khoản bị admin khoá (không có `lockUntil`) → **423**, không phải 500; khoá tạm thì trả kèm mốc dùng lại được; lỗi 500 **không** trả message nội bộ ra ngoài. |
| `common/util/SkillKeysTest` | 6 | `normalize`: trim + gộp khoảng trắng + lowercase, áp alias (`SpringBoot`/`spring-boot` → `spring boot`, `K8s` → `kubernetes`, `Postgres` → `postgresql`), giữ dấu chấm (`Node.js`), `null` → `""`. `fingerprint`: 64 ký tự, bỏ qua hoa/thường + khoảng trắng, đổi khi đổi `difficulty`. |
| `common/util/SkillMatcherTest` | 4 | Alias **chỉ dùng khi so khớp** (`REST`/`RESTful API` → `rest api`, `GCP` → `google cloud`, `CI CD` → `ci/cd`, `Unit Tests` → `unit testing`) và giữ nguyên dạng `a/b` (`CI/CD`, `AI/ML`) để không phá khoá kỹ năng. |
| `model/dto/request/RegisterRequestValidationTest` | 2 | **Phân lớp có chủ đích**: DTO chỉ chặn lỗi cấu trúc (thiếu mật khẩu, email sai định dạng); mật khẩu yếu phải xuống được service — nơi duy nhất ghi `register_attempts` và bật được ngưỡng rate-limit. |

### 7.2. Admin — 7 class / 123 test (`run-admin-tests.ps1`)

| Class | Test | Hợp đồng được khoá |
|---|---|---|
| `AdminCategoryServiceImplTest` | 5 | Đúng 4 nhóm theo thứ tự cố định, khoá trùng query param của job wizard; sắp giảm dần theo số tin, hoà thì theo alphabet (kết quả ổn định); bỏ giá trị rỗng/chỉ khoảng trắng (tin nhập dở); enum → string, số đếm nhận cả `Integer` lẫn `Long`; chưa có tin thì vẫn đủ 4 nhóm rỗng. |
| `AdminPermissionServiceImplTest` | 9 | Ma trận đúng 9 module theo thứ tự spec 006; chỉ `admin-console` là adminOnly; mỗi module đủ 5 hành động đúng 1 lần; duyệt tin (`PATCH /jobs/{id}/review`) không cấp cho nhà tuyển dụng; `APPROVE` rỗng vẫn hiện với `granted=false`; mỗi hành động kèm nhãn tiếng Việt; cột role đủ 4 role + số user từ `countLiveByRole`; driver trả `Integer` vẫn đọc được. |
| `AdminDashboardServiceImplTest` | 14 | User: 4 số lấy từ **một** aggregate row, `deleted` là count riêng, DB rỗng → 0 không NPE; `byRole`/`byStatus` luôn đủ cột (vắng = 0) để chart không thiếu cột; `jobs.total = Σ byStatus` nên hai số cạnh nhau không lệch; `pendingModeration` dùng chung nguồn với `byStatus.PENDING_APPROVAL`; `avgDurationSeconds` làm tròn về giây, chưa có buổi → `null` (FE hiện gạch); logins tách success/failed; trends đủ 12 tháng tăng dần và kết thúc ở tháng hiện tại, row ngoài cửa sổ bị bỏ, `rangeDays` chỉ đổi cửa sổ "trong khoảng". |
| `AdminSettingsServiceImplTest` | 19 | Bảng rỗng → toàn bộ default của catalog; giá trị DB thắng default và được ép đúng kiểu (bool/int/enum), int hỏng → quay về default thay vì sập màn hình; `meta` lấy dòng sửa mới nhất + đúng người sửa; cập nhật hợp lệ thì trim, ghi đúng key, `updatedBy=admin`, audit đúng 1 lần; key đã có thì update tại chỗ (không tạo dòng trùng); body rỗng → không ghi, không audit; chưa đăng nhập → 404 trước khi validate; key lạ báo dạng `group.field`; nhóm không phải object bị chặn riêng; rule kiểu: STRING 1..100, TEXT ≤ 2000, EMAIL rỗng được/sai định dạng thì không, BOOL chỉ nhận boolean thật, INT không nhận chuỗi số. |
| `AdminPositionServiceImplTest` | 11 | Có `skills` → dùng luôn + khử trùng, **không gọi AI**; thiếu skills → `OpenAiClient.extractRequiredSkills(jdText)` rồi khử trùng / bỏ chuỗi rỗng; thiếu cả hai → 400 và không `save`; `jdText` được trim và lưu để sửa sau; slug sinh từ tên, trùng thì thêm `-2`; update không gửi skills/jd → giữ kỹ năng cũ; update có skills → xoá kỹ năng không còn (khoá chuẩn hoá bằng `SkillMatcher`); `changeStatus` lật `active`; id lạ → 404. |
| `AdminUserServiceImplTest` | 48 | Danh sách live từ chối filter `DELETED` (chỉ endpoint `/users/deleted`); từ khoá escape `%`/`_`, quá 100 ký tự bị chặn trước khi chạm DB, ghép `lastLoginAt` khớp không phân biệt hoa thường; tab Đã xoá dùng query riêng; stats 5 số từ một row aggregate + 2 count riêng, row bị cắt ngắn thì cột thiếu = 0; chi tiết user gộp login gần nhất, điểm AI làm tròn 1 số lẻ, đếm hoạt động, tách skills, ghi audit, thiếu hồ sơ/điểm → `null`; dòng thời gian gộp 3 nguồn, mới nhất trước, chặn xem quá 1000 dòng; audit log điền email/họ tên người bị tác động bằng **một** lần đọc `users`. **Luật quản trị:** không tự khoá/tự xoá/tự hạ quyền, không khoá `ROLE_ADMIN`, luôn còn ≥ 1 admin sống; khoá/xoá/đổi role/đổi email đều tăng `tokenVersion` + `revokeAll`; hành động bị từ chối vẫn ghi audit `succeeded=false`; reset mật khẩu TTL 30 phút, lời mời TTL 24 giờ; export CSV cap 10 000 dòng, chặn formula injection (`=`/`+`/`-`/`@`/tab/CR → thêm `'`), quote RFC-4180, ô rỗng thay vì chữ `null`; bulk lock/role trả kết quả từng id. |
| `TopCvImportServiceImplTest` | 17 | Chặn trước khi gọi mạng: URL rỗng, scheme không phải http(s), tên miền lạ, chuỗi không phải URI, batch rỗng. Bóc tách ưu tiên JSON-LD `JobPosting` → đăng ngay `OPEN`, ghi `JobModerationRecord(APPROVED, ADMIN)`, `aiSafetyScore` **tính lại từ chính nội dung** (không phải số cứng); thiếu JSON-LD thì lùi OpenGraph → H1 → `<title>` và tự dò kỹ năng; trang không có tiêu đề → lỗi nêu tên miền. 403 Cloudflare → message kèm 3 cách khắc phục (`TOPCV_COOKIE`/`TOPCV_PROXY_URL`/`TOPCV_SCRAPER_API_URL`); 503 thử lại; 429 ba lần thì dừng kèm hướng dẫn giảm số tin; body rỗng là lỗi dữ liệu. Trang danh sách: khử trùng link, bỏ query, tôn trọng `limit`; một URL lỗi không làm hỏng cả lô; TopCV không có tin → tự chuyển ITviec; `limit <= 0` dùng `app.topcv.default-limit`. |

### 7.3. Interview — 6 class / 81 test (`run-interview-tests.ps1`)

| Class | Test | Hợp đồng được khoá |
|---|---|---|
| `MockInterviewServiceImplTest` | 35 | Ba mode `MOCK`/`KNOWLEDGE`/`AVATAR` có tiền điều kiện riêng và thông điệp 400 khác nhau; `AVATAR` **không** sinh câu hỏi trước, topic lấy từ JD; `MOCK` sinh câu hỏi theo JD + số câu; `KNOWLEDGE` bắt buộc topic và trim trước khi đưa cho AI; JD không tồn tại → 404; **phiên của người khác → 403 chứ không phải 404** (không lộ sự tồn tại); phiên đã kết thúc → 400 và không ghi thêm câu trả lời; `MOCK`/`KNOWLEDGE` không nhận câu hỏi do client tự gửi; đã trả lời hết câu → 400 thay vì `IndexOutOfBounds`; chấm điểm: đúng 100, sai 0 nhưng feedback vẫn nêu đáp án đúng, option ngoài phạm vi → 400, không gửi gì → 400 thay vì chấm 0 im lặng, client gửi `B`/`b)`/`2` vẫn hiểu đúng. |
| `AIQuestionServiceImplTest` | 18 | Skill rỗng → dừng **trước** khi gọi AI (không tốn token); tỉ lệ toàn 0/null bị từ chối; một mức 100% → gọi AI 1 lần và map đủ field nghiệp vụ; độ khó lạ/thiếu → rơi về bucket đang xử lý, không nổ exception; 50/50 với `count=4` → 2 lần gọi, mỗi lần 2 câu; làm tròn **thiếu** câu (30/30, count=4) thì phần dư chia bù cho từng bucket, làm tròn **dư** (33/33/33, count=5) vẫn không vượt `count`; AI trả nhiều hơn → cắt; thiếu field `questions`/trả về object → "dữ liệu AI không hợp lệ"; câu hỏi trống bị bỏ, tất cả trống → báo không generate được; lẫn câu tốt và hỏng → giữ câu tốt; MCQ `options` serialize thành JSON string để lưu 1 cột, tự luận không lưu options; chấm điểm map `score/is_correct/feedback/strengths` và giữ nguyên model answer; `is_correct` dạng `1`/`0` vẫn hiểu đúng. |
| `StudyProgramServiceImplTest` | 17 | Chưa có CV → 400 kèm hướng dẫn upload và **không gọi AI**; CV không có skill và không có knowledge gap → 400 thay vì chương trình rỗng; không xác định được vị trí ở request/CV/gợi ý → 400 nêu endpoint cần gọi; vị trí lấy theo request → CV → `suggestedPositions[0]`; skill trước, knowledge gap sau, mỗi topic đánh số 1..n và gắn câu hỏi; skill trùng (không phân biệt hoa/thường) chỉ 1 topic; `maxTopicsPerProgram` cắt số topic; `questionsPerTopic` kẹp 1..50, bỏ trống thì dùng cấu hình; `programType` do client chọn được giữ nguyên; chương trình của user khác → 404. |
| `QuestionPoolServiceImplTest` | 5 | Chỉ bù đúng phần thiếu khi phần thiếu lớn hơn batch; thiếu nhỏ vẫn nạp ít nhất 1 batch; không bao giờ sinh vượt trần; cell đã đầy → không sinh gì; `acquire` không cần gì thì **không chạm** pool. |
| `InterviewReminderServiceImplTest` | 3 | Cửa sổ quét rộng đúng bằng nhịp cron và nằm trước giờ hẹn đúng `lead`; mỗi buổi đã chốt gửi đúng 1 mail cho ứng viên + 1 cho người phỏng vấn; slot còn mang dấu của đơn đã rời vòng hẹn lịch thì im lặng (không nhắc "ma"). |
| `service/ai/AvatarReportEvaluatorTest` | 3 | Chỉ chấm lượt đã chốt (bỏ bản nháp và câu trả lời rỗng); phiên không có lượt chốt vẫn trả báo cáo hợp lệ, không `NaN`/điểm âm; câu trả lời rất dài bị chặn ở 100 điểm. |

### 7.4. Infra — 2 class / 15 test (`run-infra-tests.ps1`)

Bộ này chạy **offline thật**: Gmail API được thay bằng một `HttpServer` trong tiến trình, SMTP được thay
bằng mock `JavaMailSender`, nên không cần credential hay mạng.

| Class | Test | Hợp đồng được khoá |
|---|---|---|
| `EmailServiceImplGmailTest` | 7 | Mail xác thực gửi đúng dạng **base64url raw RFC-2822**; access token được cache giữa các lần gửi; Google từ chối → `MailDeliveryException`; thiếu credential thì fail-fast, **không** gọi Google; `dev-log` không gửi và không fail khi thiếu credential; có app password thì **SMTP được ưu tiên hơn Gmail API**; SMTP từ chối → `MailDeliveryException`. |
| `EmailServiceImplNotificationTest` | 8 | Có `MAIL_USERNAME` nhưng thiếu `MAIL_PASSWORD` → báo lỗi gửi mail, không gọi SMTP; không cấu hình gì + `dev-log=false` → email xác thực ném lỗi (caller trả 502); **email thông báo** (ứng tuyển/lịch/nhắc lịch) không được ném lỗi khi chưa cấu hình; số tiền hiển thị kiểu Việt Nam (`1.250.000 VND`, không phải `1,250,000`); mail khoá/mở khoá/xoá tài khoản có subject riêng; `From` lấy `MAIL_FROM`, trống thì dùng `MAIL_USERNAME` (Gmail chặn From khác); link trong mail trỏ đúng route FE và nêu thời hạn token; `dev-log=true` thì email bắt buộc cũng không gửi thật và không ném lỗi. |

### 7.5. Bổ sung vào các bộ đã có

| Bộ | Class thêm ở đợt 2 | Test |
|---|---|---|
| `auth` | `UserServiceImplTest` | 20 |
| `auth` | `UserSessionServiceImplTest` | 14 |
| `auth` | `SsoAuthInterfaceImplTest` | 6 |
| `core` | `PositionRecommendationServiceImplTest` | 7 |
| `core` | `CvServiceImplTest` | 2 |

Nhờ đó **cả 35/35 class `*ServiceImpl`** trong `src/main` đều có test trực tiếp (trước đợt 2 là 33/35:
`AdminUserServiceImpl` và `TopCvImportServiceImpl` chưa có).

## 8. Khoảng trống đã biết

1. **OAuth sau lời gọi HTTP chưa test được.** `GoogleAuthInterfaceImpl` và `GithubAuthInterfaceImpl`
   tạo `HttpClient` **cứng trong class** (`private final HttpClient httpClient = HttpClient.newBuilder()...`),
   không có điểm inject, nên các nhánh `aud` / `iss` / `exp` / `email_verified`, GitHub từ chối code,
   profile không có email đã xác minh, tự tạo user, tài khoản bị khóa/xóa **chưa phủ**.
   *Đề xuất (cần bạn quyết vì phải sửa code production):* đổi thành tham số constructor
   (`ObjectMapper`, `HttpClient`) với bean mặc định, khi đó test có thể tiêm fake và phủ 100% nhánh.
2. **TopCV/ITviec: hai nhánh qua proxy/scraper chưa phủ.** `TopCvImportServiceImpl` có 3 đường lấy HTML:
   trực tiếp, `TOPCV_PROXY_URL`, `TOPCV_SCRAPER_API_URL`. Test hiện chỉ phủ đường trực tiếp vì hai đường
   còn lại dựng `HttpClient`/URI khác nhau ngay trong `@PostConstruct`. Muốn phủ: chạy test với env thật
   (không offline được) hoặc thêm điểm inject — tức sửa `src/main`, nằm ngoài phạm vi đợt này.
3. **15 class integration (`@SpringBootTest` + Testcontainers, 211 test) cần Docker/MySQL** — không nằm
   trong 489 test ở trên: `AdminUserControllerIntegrationTest` (72), `JobControllerIntegrationTest` (17),
   `AvatarInterviewApiIntegrationTest` (16), `AdminDataCompletionIntegrationTest` (15), `PersistenceTest`
   (15), `ApplicationWorkflowIntegrationTest` (10), `JobModerationConsoleIntegrationTest` (10),
   `TwoFactorIntegrationTest` (9), `UserSessionAndAvatarIntegrationTest` (9), `AdminSettingsIntegrationTest`
   (8), `ApplicationScheduleIntegrationTest` (8), `LogoutWithdrawDeleteIntegrationTest` (7),
   `AdminPermissionMatrixIntegrationTest` (6), `AdminDashboardIntegrationTest` (5),
   `ChangePasswordIntegrationTest` (4).
4. **Controller chỉ delegate, chưa test:** `GET /auth/verify`, `POST /auth/resend-verification`,
   `POST /auth/sso` + `GET /auth/sso/config`, nhánh MFA của `POST /auth/login`, `POST /auth/verify-otp`,
   và toàn bộ `TwoFactorController` (`/setup`, `/enable`, `/disable`). Logic service đã có test; phần
   còn lại là ánh xạ HTTP ↔ DTO. Muốn phủ thì viết thêm case trong `AuthControllerSessionTest`.
5. **Phase 2 (contract/E2E theo Postman)** vẫn cần Docker (MySQL) và một bộ HTTP client.

## 9. Quy ước khi viết test mới

- Đặt test **cùng package** với class cần test: `src/test/java/com/aiinterview/platform/<same path>`.
- Tên method: `method_tìnhHuống_kỳVọng`; `@DisplayName` viết tiếng Việt **không dấu** (theo các test
  có sẵn của repo) để không phụ thuộc charset khi đọc log.
- Hai style đang cùng tồn tại, chọn theo package:
  - `@ExtendWith(MockitoExtension.class)` + `@Mock` + `@InjectMocks` — dùng cho `service/auth/**` và
    `controller/**` (theo test Auth có sẵn);
  - `mock()` + khởi tạo thủ công `new XxxServiceImpl(...)` — dùng cho các test P0 đã viết.
- **Không** `mockStatic` cho `SecurityUtils`/`JwtUtil`: dựng `SecurityContextHolder` và stub
  `userRepository.findByEmail` (với `SecurityUtils`) hoặc để `JwtUtil` mint token thật (nó có secret
  fallback cứng nên chạy offline được).
- Mỗi test phải trả lời được: **gọi cái gì → mong đợi gì**, và khi cần thì khẳng định thêm "không được
  chạm vào đâu" bằng `verifyNoInteractions(...)`.
- Thêm class mới vào script của **đúng bộ** nó thuộc về (`run-shared|admin|auth|core|interview|infra-tests.ps1`);
  chạy lẻ một class thì dùng `compile-and-run.ps1`. Dependency mới thì thêm vào allow-list trong
  `build-classpath.ps1` (kèm version khớp `pom.xml`: Boot 3.2.5 → Spring 6.1.6) và **chạy lại cả 6 bộ**,
  vì thiếu jar thường chỉ nổ ở bộ đầu tiên dùng tới nó.
- Class test không nằm trong script nào = **test chết**: vẫn xanh nếu ai đó chạy tay, nhưng không ai chạy.
  Trước khi kết thúc một đợt, đối chiếu `Get-ChildItem src/test -Recurse -Filter *Test.java` với danh sách
  FQN trong 6 script (đợt 2 đã tìm ra 7 class rơi vào tình trạng này và đăng ký lại).

## 10. Nhật ký thay đổi

### 10.1. Đợt 1 — Folder 00 (Auth) + luồng nghiệp vụ chính

| File | Nội dung |
|---|---|
| `src/test/java/com/aiinterview/platform/service/auth/impl/LoginInterfaceImplTest.java` | 14 test cho `POST /auth/login` |
| `src/test/java/com/aiinterview/platform/controller/auth/AuthControllerSessionTest.java` | 14 test cho `refresh` / `me` / `logout` / `register` / `client-id` |
| `src/test/java/com/aiinterview/platform/service/auth/impl/GoogleAuthInterfaceImplTest.java` | 5 test tiền kiểm tra Google OAuth |
| `src/test/java/com/aiinterview/platform/service/auth/impl/GithubAuthInterfaceImplTest.java` | 5 test tiền kiểm tra GitHub OAuth |
| `scripts/testcheck/build-classpath.ps1` | Dựng argfile + classpath offline |
| `scripts/testcheck/TestRunner.java` | JUnit Platform launcher thay `mvn test` |
| `scripts/testcheck/compile-and-run.ps1` | Engine compile + chạy |
| `scripts/testcheck/run-auth-tests.ps1` | Bộ test folder 00 |
| `scripts/testcheck/run-core-tests.ps1` | Bộ test luồng nghiệp vụ chính |
| `docs/testing.md` | Tài liệu này |

### 10.2. Đợt 2 — toàn bộ module còn lại (24 class / 283 test)

| File | Nội dung |
|---|---|
| `service/admin/impl/AdminCategoryServiceImplTest.java` | 5 test — 4 nhóm danh mục JD |
| `service/admin/impl/AdminPermissionServiceImplTest.java` | 9 test — ma trận quyền 9 module |
| `service/admin/impl/AdminDashboardServiceImplTest.java` | 14 test — số liệu dashboard + trends |
| `service/admin/impl/AdminSettingsServiceImplTest.java` | 19 test — catalog cài đặt + validate kiểu |
| `service/admin/impl/AdminPositionServiceImplTest.java` | 11 test — CRUD vị trí + bóc kỹ năng bằng AI |
| `service/admin/impl/AdminUserServiceImplTest.java` | 48 test — user directory, khóa/xóa/role, CSV, bulk |
| `service/admin/impl/TopCvImportServiceImplTest.java` | 17 test — cào TopCV/ITviec (fake `HttpClient`) |
| `service/interview/impl/MockInterviewServiceImplTest.java` | 35 test — 3 mode phỏng vấn + chấm điểm |
| `service/interview/impl/AIQuestionServiceImplTest.java` | 18 test — chia câu theo tỉ lệ độ khó |
| `service/interview/impl/StudyProgramServiceImplTest.java` | 17 test — chương trình ôn từ CV |
| `service/interview/impl/QuestionPoolServiceImplTest.java` | 5 test — nạp pool theo batch/trần |
| `service/interview/impl/InterviewReminderServiceImplTest.java` | 3 test — cửa sổ nhắc lịch |
| `service/ai/AvatarReportEvaluatorTest.java` | 3 test — chấm báo cáo avatar |
| `service/infra/impl/EmailServiceImplGmailTest.java` | 7 test — Gmail API wire-level (fake `HttpServer`) |
| `service/infra/impl/EmailServiceImplNotificationTest.java` | 8 test — mail bắt buộc vs thông báo |
| `service/auth/impl/UserServiceImplTest.java` | 20 test — profile, verify, resend, xóa |
| `service/auth/impl/UserSessionServiceImplTest.java` | 14 test — start/isLive/revokeSessions |
| `service/auth/impl/SsoAuthInterfaceImplTest.java` | 6 test — SSO theo domain |
| `service/job/impl/PositionRecommendationServiceImplTest.java` | 7 test — gợi ý vị trí từ CV |
| `service/document/impl/CvServiceImplTest.java` | 2 test — đọc/lưu CV |
| `common/exception/GlobalExceptionHandlerTest.java` | 3 test — ánh xạ exception → HTTP |
| `common/util/SkillKeysTest.java` | 6 test — normalize + fingerprint |
| `common/util/SkillMatcherTest.java` | 4 test — alias khi so khớp |
| `model/dto/request/RegisterRequestValidationTest.java` | 2 test — phân lớp validate DTO |
| `scripts/testcheck/run-shared-tests.ps1` | **Mới** — 4 class dùng chung (15 test) |
| `scripts/testcheck/run-admin-tests.ps1` | **Mới** — 7 class admin (123 test) |
| `scripts/testcheck/run-interview-tests.ps1` | **Mới** — 6 class interview (81 test) |
| `scripts/testcheck/run-infra-tests.ps1` | **Mới** — 2 class Email (15 test) |
| `scripts/testcheck/build-classpath.ps1` | Sửa — thêm `org.eclipse.angus:jakarta.mail`, `spring-context-support`, `spring-webmvc`, `jakarta.activation-api`, `micrometer-observation`, `micrometer-commons` |
| `scripts/testcheck/run-auth-tests.ps1` | Sửa — thêm `UserServiceImplTest`, `UserSessionServiceImplTest`, `SsoAuthInterfaceImplTest` |
| `scripts/testcheck/run-core-tests.ps1` | Sửa — thêm `PositionRecommendationServiceImplTest`, `CvServiceImplTest` |
| `docs/testing.md` | Cập nhật §1, §2.2, §3, §4 (lỗi 10–16), §7 mới, §8, §9, §10 |

Không sửa một dòng nào trong `src/main` và không sửa `pom.xml`.

Mọi test đều **unit test thuần** (mock repo/client, không Spring context, không Docker) nên chạy được
theo cả hai đường khi Maven wrapper được sửa: harness offline ở trên và `mvn test` (Surefire tự build
`target/classes`).

Kế hoạch gốc của đợt test nằm ở
`C:\Users\LENOVO\AppData\Roaming\Qoder\SharedClientCache\cache\plans\Unit_First_Test_Strategy_6edac833.md`
(Phase 1 xong; Phase 2 contract test chờ Docker).
