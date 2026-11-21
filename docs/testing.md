# Hướng dẫn test backend (AI Interview Platform)

Tài liệu này ghi lại **toàn bộ** những gì đã làm cho phần unit test: cách chạy, harness tự chế, các lỗi
đã gặp và khoảng trống còn lại. Cập nhật lần cuối: đợt bổ sung test cho **Postman folder 00 — Auth** và
**Batch 1 — Security core**.

## 1. Trạng thái hiện tại

| Bộ | Số class | Số test | Kết quả | Cần Docker? |
|---|---|---|---|---|
| Luồng nghiệp vụ chính (core flow: job / application / slot / cover letter / profile / review / bug report / order / package) | 10 | 124 | pass | Không |
| Folder 00 — Auth | 8 | 70 | pass | Không |
| Batch 1 — Security core (JWT / filter / session / mật khẩu / avatar) | 7 | 89 | pass | Không |
| **Tổng** | **25** | **283** | **0 fail** | Không |

Thời gian chạy: ~3–8 giây cho mỗi bộ sau khi đã có `target/classes`.

> Ghi chú về tên: bộ 124 test từng được gọi là **"P0 core flow"** (P0 = ưu tiên số 0, theo kế hoạch),
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

# Folder 00 - Auth (70 test)
powershell -NoProfile -File scripts\testcheck\run-auth-tests.ps1

# Batch 1 - Security core (89 test)
powershell -NoProfile -File scripts\testcheck\run-security-tests.ps1

# Luong nghiep vu chinh - core flow (124 test)
powershell -NoProfile -File scripts\testcheck\run-core-tests.ps1
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
| `scripts/testcheck/run-auth-tests.ps1` | Danh sách 8 class của folder 00 (Auth). |
| `scripts/testcheck/run-core-tests.ps1` | Danh sách 10 class của luồng nghiệp vụ chính (bộ "P0" cũ). |

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
| `GET /auth/verify`, `POST /auth/resend-verification`, controller `/login` nhánh MFA, controller `/verify-otp`, `/sso` | `AuthController` (chỉ delegate) | — | Chưa test (xem §7) |

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
cấu hình. Lý do giới hạn ở §7.

## 7. Khoảng trống đã biết

1. **OAuth sau lời gọi HTTP chưa test được.** `GoogleAuthInterfaceImpl` và `GithubAuthInterfaceImpl`
   tạo `HttpClient` **cứng trong class** (`private final HttpClient httpClient = HttpClient.newBuilder()...`),
   không có điểm inject, nên các nhánh `aud` / `iss` / `exp` / `email_verified`, GitHub từ chối code,
   profile không có email đã xác minh, tự tạo user, tài khoản bị khóa/xóa **chưa phủ**.
   *Đề xuất (cần bạn quyết vì phải sửa code production):* đổi thành tham số constructor
   (`ObjectMapper`, `HttpClient`) với bean mặc định, khi đó test có thể tiêm fake và phủ 100% nhánh.
2. **`TwoFactorIntegrationTest`** (`@SpringBootTest` + Testcontainers) cần MySQL trong Docker — không
   nằm trong 70 test ở trên. Đây là test duy nhất của folder 00 cần Docker.
3. **Controller chỉ delegate, chưa test:** `GET /auth/verify`, `POST /auth/resend-verification`,
   `POST /auth/sso` + `GET /auth/sso/config`, nhánh MFA của `POST /auth/login`, `POST /auth/verify-otp`,
   và toàn bộ `TwoFactorController` (`/setup`, `/enable`, `/disable`). Logic service đã có test; phần
   còn lại là ánh xạ HTTP ↔ DTO. Muốn phủ thì viết thêm case trong `AuthControllerSessionTest`.
4. ~~**`UserServiceImpl` / `UserSessionServiceImpl`**: chưa có test trực tiếp cho `revokeSessions`,
   `start`, `isLive`.~~ Đã phủ ở Batch 1 (`UserServiceImplTest` 23 test, `UserSessionServiceImplTest`
   12 test). Phần còn thiếu của nhóm này: `SsoAuthInterfaceImpl` (chỉ test được tiền kiểm tra vì
   `HttpClient` dựng cứng trong class, xem mục 1) và các nhánh OAuth sau lời gọi HTTP.
5. **Phase 2 (contract/E2E theo Postman)** vẫn cần Docker (MySQL) và một bộ HTTP client.

## 8. Quy ước khi viết test mới

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
- Thêm class mới vào script bộ tương ứng (`run-auth-tests.ps1`, `run-security-tests.ps1`,
  `run-core-tests.ps1`); nếu cần dependency mới thì thêm vào allow-list trong `build-classpath.ps1`
  (kèm version khớp `pom.xml`).

## 9. Batch 1 — Security core (89 test)

Mục tiêu: phủ lớp bảo vệ mà **mọi** endpoint dựa vào — mint/đọc JWT, filter, phiên đăng nhập, đổi mật
khẩu và ghi avatar. Chạy bằng `run-security-tests.ps1`, không cần Docker.

| Class | Test | Nội dung |
|---|---|---|
| `security/jwt/JwtUtilTest` | 16 | access / refresh / 2FA: mint + validate, sai secret, hết hạn, token rỗng/rác |
| `security/jwt/JwtFilterTest` | 14 | nhánh fail-open: 12 trường hợp từ chối + nhánh hợp lệ (authentication, `setAttribute(sid)`, `touch`) |
| `security/utils/JwtUtilsTest` | 5 | refresh mint access mới, `tv` lấy từ users chứ không từ claim, refresh token giữ nguyên |
| `security/utils/SecurityUtilsTest` | 7 | `X-Forwarded-For` lấy hop đầu, fallback `remoteAddr`, `getCurrentUser` 3 nhánh |
| `security/utils/UserAgentParserTest` | 11 | Edge/Opera thắng Chrome, iPhone/iPad, Android điện thoại vs máy tính bảng, UA null |
| `service/auth/impl/UserSessionServiceImplTest` | 12 | `start` (UUID, nhãn thiết bị, IP, UA cắt 512), `isLive(null)=true`, revoked/expired, `touch` throttle 5 phút, `revoke` chỉ phiên của mình |
| `service/auth/impl/UserServiceImplTest` | 23 | 4 quy tắc mật khẩu mới + mật khẩu hiện tại, tăng `token_version` + `revokeAll`, 5 nhánh avatar (base64, rỗng, >2MB, sai chữ ký tệp, PNG/JPEG/WEBP) |

Ba điều đáng nhớ khi đọc các test này:

1. `JwtFilter` **không ném lỗi**: mọi trường hợp xấu đều `filterChain.doFilter(...)` rồi `return`. Vì vậy
   mỗi test khẳng định cả hai vế — "vẫn đi tiếp" **và** "SecurityContext rỗng".
2. `isLive(null) = true` là chủ ý (token phát trước wave 010 không có `sid`), không phải bug.
3. Message `" User not found with id : 9"` có **khoảng trắng ở đầu**; test giữ nguyên để phát hiện ai đó
   sửa message mà quên đổi phía FE.

Kỹ thuật riêng của batch này: TTL trong `JwtUtil` là hằng số private, nên test token hết hạn **mint token
hết hạn tại chỗ** với `Key` đọc qua `ReflectionTestUtils.getField(JwtUtil.class, "accessKey")` — không
phải chờ 24h, cũng không phải mock static.

## 10. Nhật ký thay đổi

Thêm mới (chưa commit):

| File | Nội dung |
|---|---|
| `src/test/java/com/aiinterview/platform/service/auth/impl/LoginInterfaceImplTest.java` | 14 test cho `POST /auth/login` |
| `src/test/java/com/aiinterview/platform/controller/auth/AuthControllerSessionTest.java` | 14 test cho `refresh` / `me` / `logout` / `register` / `client-id` |
| `src/test/java/com/aiinterview/platform/service/auth/impl/GoogleAuthInterfaceImplTest.java` | 5 test tiền kiểm tra Google OAuth |
| `src/test/java/com/aiinterview/platform/service/auth/impl/GithubAuthInterfaceImplTest.java` | 5 test tiền kiểm tra GitHub OAuth |
| `src/test/java/com/aiinterview/platform/security/jwt/JwtUtilTest.java` | 16 test token factory (access / refresh / 2FA) |
| `src/test/java/com/aiinterview/platform/security/jwt/JwtFilterTest.java` | 14 test filter, toàn bộ nhánh fail-open |
| `src/test/java/com/aiinterview/platform/security/utils/JwtUtilsTest.java` | 5 test bước refresh |
| `src/test/java/com/aiinterview/platform/security/utils/SecurityUtilsTest.java` | 7 test IP + current user |
| `src/test/java/com/aiinterview/platform/security/utils/UserAgentParserTest.java` | 11 test nhãn thiết bị |
| `src/test/java/com/aiinterview/platform/service/auth/impl/UserSessionServiceImplTest.java` | 12 test vòng đời phiên đăng nhập |
| `src/test/java/com/aiinterview/platform/service/auth/impl/UserServiceImplTest.java` | 23 test đổi mật khẩu + avatar |
| `scripts/testcheck/build-classpath.ps1` | Dựng argfile + classpath offline |
| `scripts/testcheck/TestRunner.java` | JUnit Platform launcher thay `mvn test` |
| `scripts/testcheck/compile-and-run.ps1` | Engine compile + chạy |
| `scripts/testcheck/run-auth-tests.ps1` | Bộ 70 test folder 00 |
| `scripts/testcheck/run-security-tests.ps1` | Bộ 89 test Batch 1 |
| `scripts/testcheck/run-core-tests.ps1` | Bộ 124 test luồng nghiệp vụ chính |
| `docs/testing.md` | Tài liệu này |

Không sửa một dòng nào trong `src/main` và không sửa `pom.xml` (Batch 1 cũng vậy; `spring-web` đã có sẵn
trong allow-list nên không cần thêm jar).

Kế hoạch gốc của đợt test nằm ở
`C:\Users\LENOVO\AppData\Roaming\Qoder\SharedClientCache\cache\plans\Unit_First_Test_Strategy_6edac833.md`
(Phase 1 xong; Phase 2 contract test chờ Docker).
