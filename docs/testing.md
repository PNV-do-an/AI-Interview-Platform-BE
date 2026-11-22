# Hướng dẫn test backend (AI Interview Platform)

Tài liệu này ghi lại **toàn bộ** những gì đã làm cho phần unit test: cách chạy, harness tự chế, các lỗi
đã gặp và khoảng trống còn lại. Cập nhật lần cuối: đợt bổ sung test cho **Postman folder 00 — Auth**,
**Batch 1 — Security core** và **Batch 2 — OAuth đầy đủ nhánh** (kèm refactor R1 tiêm `HttpClient`).

## 1. Trạng thái hiện tại

| Bộ | Số class | Số test | Kết quả | Cần Docker? |
|---|---|---|---|---|
| Luồng nghiệp vụ chính (core flow: job / application / slot / cover letter / profile / review / bug report / order / package) | 10 | 124 | pass | Không |
| Folder 00 — Auth (đã gồm Batch 2 — OAuth: Google / GitHub / SSO) | 9 | 104 | pass | Không |
| Batch 1 — Security core (JWT / filter / session / mật khẩu / avatar) | 7 | 89 | pass | Không |
| **Tổng** | **26** | **317** | **0 fail** | Không |

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

# Folder 00 - Auth (104 test, gom Batch 2 OAuth: Google / GitHub / SSO)
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
- **Nếu có sửa `src/main`**, phải rebuild đúng những file đó vào `target/classes` — xem §2.5;
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

### 2.5. Rebuild `src/main` khi đã sửa code production — `rebuild-main.ps1`

Harness chỉ biên dịch `src/test`, nên nếu bạn sửa một class trong `src/main` (ví dụ đổi constructor)
mà không build lại, test sẽ **compile được nhưng chết lúc chạy** với `NoSuchMethodError`.

```powershell
# danh sách mặc định = 4 file của Batch 2 (HttpClientConfig + 3 OAuth impl)
powershell -NoProfile -File scripts\testcheck\rebuild-main.ps1

# file khác
powershell -NoProfile -File scripts\testcheck\rebuild-main.ps1 `
  -SourceList 'src/main/java/.../A.java,src/main/java/.../B.java'
```

Ba điều script này làm mà `javac` trần không làm:

1. Dùng classpath của allow-list (`%TEMP%\test-cp.txt`) nên không cần Maven;
2. Bật Lombok đúng cách: lombok có mặt **cả trên classpath** (để resolve `import lombok.*`) **và**
   trên `-processorpath` (để chạy processor). Nếu chạy `-proc:none`, `@RequiredArgsConstructor` biến
   mất, class vẫn compile nhưng constructor thiếu tham số;
3. Tự kiểm tra lại bằng `javap` rằng constructor của 3 OAuth impl **có** tham số `java.net.http.HttpClient`;
   nếu Lombok không chạy thì script `exit 1` ngay chứ không để test chết khó hiểu.

Ghi chú: `target/classes` do IDE build lần đầu (kèm Maven classpath đầy đủ). Script này chỉ **ghi đè**
những `.class` tương ứng, không xoá phần còn lại.

## 3. Harness gồm những gì

| File | Vai trò |
|---|---|
| `scripts/testcheck/build-classpath.ps1` | Dựng `@argfile` cho `javac` (`%TEMP%\javac-args.txt`) và classpath runtime (`%TEMP%\test-cp.txt`) từ allow-list jar trong `.m2`. |
| `scripts/testcheck/TestRunner.java` | Thay thế `mvn test`: dùng JUnit Platform `Launcher` + `SummaryGeneratingListener`, in summary và `exit 1` khi fail. |
| `scripts/testcheck/compile-and-run.ps1` | Engine: `build-classpath` → `javac` source test → biên dịch `TestRunner` → `java ... TestRunner <FQN...>`. |
| `scripts/testcheck/rebuild-main.ps1` | Rebuild vài file `src/main` đã sửa vào `target/classes` (kèm Lombok) — xem §2.5. |
| `scripts/testcheck/run-auth-tests.ps1` | Danh sách 9 class của folder 00 (Auth + OAuth). |
| `scripts/testcheck/run-security-tests.ps1` | Danh sách 7 class của Batch 1 (Security core). |
| `scripts/testcheck/run-core-tests.ps1` | Danh sách 10 class của luồng nghiệp vụ chính (bộ "P0" cũ). |

Ngoài ra có một file test **không** phải test thật: `src/test/.../service/auth/impl/StubHttpClient.java`.
Đây là fake `java.net.http.HttpClient` dùng chung cho 3 test OAuth. Vì tên không khớp `**/*Test.java`
nên Surefire (và harness) sẽ không coi nó là test — nhưng harness **vẫn phải biên dịch** nó, nên nó
nằm trong `$sources` của `run-auth-tests.ps1`.

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
| `POST /api/v1/auth/google` | `GoogleAuthInterfaceImpl` | `GoogleAuthInterfaceImplTest` (17) | Đã test (Batch 2) |
| `POST /api/v1/auth/github` | `GithubAuthInterfaceImpl` | `GithubAuthInterfaceImplTest` (17) | Đã test (Batch 2) |
| `GET /api/v1/auth/sso/config` | `SsoAuthInterfaceImpl.getPublicConfig` | `SsoAuthInterfaceImplTest` (4) | Đã test (Batch 2) |
| `POST /api/v1/auth/sso` | `SsoAuthInterfaceImpl` | `SsoAuthInterfaceImplTest` (6) | Đã test tới bước exchange; bước verify id_token còn gap (§7) |
| `POST /api/v1/auth/verify-otp` | `TwoFactorServiceImpl` | `TwoFactorServiceImplTest` (14) | Đã test (service) |
| `POST /api/v1/auth/forgot-password`, `reset-password` | `PasswordResetServiceImpl` | `PasswordResetServiceImplTest` (8) | Đã test (service) |
| `GET/POST /api/v1/auth/sso*` | `SsoEmailDomainPolicy` | `SsoEmailDomainPolicyTest` (3) | Đã test (policy) |
| 2FA `/api/v1/2fa/*` | `TwoFactorServiceImpl` | `TwoFactorServiceImplTest` (14) | Đã test (service) |
| `GET /auth/verify`, `POST /auth/resend-verification`, controller `/login` nhánh MFA, controller `/verify-otp`, controller `/sso` | `AuthController` (chỉ delegate) | — | Chưa test (xem §7) |

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

### 6.3. `GoogleAuthInterfaceImplTest` — 17 case

Google **không** parse id_token ở local: `POST /auth/google` gửi token lên `tokeninfo` và tin claims trả
về. Vì vậy "token hết hạn" chỉ là một claim `exp` trong quá khứ, không cần chờ 24h.

Các case: `getClientId` rỗng/đủ · chưa cấu hình → không gọi mạng, không chạm DB · token rỗng ·
`tokeninfo` trả 400 · lỗi mạng (`IOException`) được bọc thành message · `aud` khác client id ·
`iss` không phải Google · `exp` đã qua · `email_verified=false` · email rỗng · user mới (tạo với mật
khẩu random, `enabled=true`, `fullName` lấy từ claim `name`) · thiếu `name` → lấy phần trước `@` ·
user đã tồn tại → **không** `save` lần nữa · user xóa mềm · user bị admin khóa · và một case khẳng
định chỉ gọi `tokeninfo` **đúng 1 lần** (không gọi lặp).

### 6.4. `GithubAuthInterfaceImplTest` — 17 case

GitHub là **3 lời gọi tuần tự** và thứ tự có ý nghĩa: đổi code → `/user` → chỉ khi `/user` không có
email mới gọi `/user/emails`. Test assert đúng chuỗi URI đó bằng `http.requestUris()`, nên nếu ai đổi
thứ tự hoặc gọi thừa thì có test đỏ ngay.

Các case: cấu hình thiếu id / thiếu secret · thiếu code · GitHub từ chối code (lấy `error_description`) ·
response đổi token không có `access_token` → message `unknown` · `/user` trả 500 (message kèm status để
debug) · lỗi mạng ở `/user` · email có sẵn trong profile → chỉ 2 lời gọi · thiếu email → lấy email
`primary` + `verified` từ `/user/emails` (3 lời gọi) · không có `primary` → email verified đầu tiên ·
không có email nào verified → từ chối · `/user/emails` lỗi 503 → coi như không có email (không ném lỗi
HTTP ra ngoài) · user mới (thiếu tên → lấy `login` GitHub) · user bị khóa · user xóa mềm.

Điểm dễ sai: nhánh `/user/emails` **nuốt mọi exception** (`catch (Exception) → return ""`), nên test
dùng đúng kỳ vọng đó thay vì kỳ vọng một lỗi mạng.

### 6.5. `SsoAuthInterfaceImplTest` — 10 case

OIDC enterprise: `GET /auth/sso/config` trả endpoint cho FE, `POST /auth/sso` đổi code lấy token.
Discovery được **cache** trong field `volatile` của service — đây là hành vi có thật và có test riêng
(gọi `getPublicConfig()` 2 lần → chỉ 1 request).

Các case: chưa cấu hình → `enabled=false`, không gọi mạng · đã cấu hình → trả `authorization_endpoint`
đọc từ discovery · cache discovery · discovery trả 503 → message kèm status · discovery thiếu endpoint
bắt buộc → từ chối · lỗi mạng khi đọc discovery → bọc message · login khi chưa cấu hình · login thiếu
code · IdP từ chối code · IdP không trả `id_token`.

### 6.6. `StubHttpClient` — fake dùng chung

`HttpClient.send` là generic nên `mock(HttpClient.class)` vừa khó đọc vừa dễ sai. Thay vào đó là một
fake ~180 dòng: hàng đợi response (`enqueue(status, body)`), hàng đợi lỗi (`failNextWith(IOException)`),
và `requestUris()` để assert thứ tự/số lần gọi.

Quy tắc quan trọng: **response và lỗi nằm chung một hàng đợi**, nên `failNextWith` nghĩa đúng là "lời
gọi HTTP kế tiếp ném lỗi này". Muốn lỗi ở lời gọi thứ 2 thì phải `enqueue` response của lời gọi thứ
nhất trước. (Bản đầu tiên để cờ lỗi ưu tiên hơn hàng đợi và đã làm sai đúng một test — xem §11.)

## 7. Khoảng trống đã biết

1. ~~**OAuth sau lời gọi HTTP chưa test được.**~~ Đã đóng ở Batch 2 bằng refactor R1: `HttpClient` trở
   thành tham số constructor (bean `oauthHttpClient` trong `config/HttpClientConfig`), nên
   `StubHttpClient` tiêm được và toàn bộ nhánh sau lời gọi HTTP của Google / GitHub / SSO đã có test.
   **Còn lại đúng một điểm:** trong `SsoAuthInterfaceImpl.verifyIdToken`,
   `NimbusJwtDecoder.withJwkSetUri(...)` **tự fetch JWKS** bên trong thư viện nên `HttpClient` được tiêm
   không đi qua đó. Các nhánh sau khi có `id_token` (verify chữ ký → đọc claims → map user) vì thế vẫn
   thuộc diện integration test với IdP thật hoặc một JWKS endpoint giả. `ObjectMapper` cũng cố ý **không**
   tiêm vì trong Spring context chưa có bean `ObjectMapper` — tiêm vào sẽ làm hỏng khởi động app.
2. **`TwoFactorIntegrationTest`** (`@SpringBootTest` + Testcontainers) cần MySQL trong Docker — không
   nằm trong 104 test ở trên. Đây là test duy nhất của folder 00 cần Docker.
3. **Controller chỉ delegate, chưa test:** `GET /auth/verify`, `POST /auth/resend-verification`,
   `POST /auth/sso` + `GET /auth/sso/config`, nhánh MFA của `POST /auth/login`, `POST /auth/verify-otp`,
   và toàn bộ `TwoFactorController` (`/setup`, `/enable`, `/disable`). Logic service đã có test; phần
   còn lại là ánh xạ HTTP ↔ DTO. Muốn phủ thì viết thêm case trong `AuthControllerSessionTest`.
4. ~~**`UserServiceImpl` / `UserSessionServiceImpl`**: chưa có test trực tiếp cho `revokeSessions`,
   `start`, `isLive`.~~ Đã phủ ở Batch 1 (`UserServiceImplTest` 23 test, `UserSessionServiceImplTest`
   12 test) và Batch 2 (`SsoAuthInterfaceImplTest`, `GoogleAuthInterfaceImplTest`,
   `GithubAuthInterfaceImplTest` cho các nhánh OAuth sau lời gọi HTTP).
5. **Phase 2 (contract/E2E theo Postman)** vẫn cần Docker (MySQL) và một bộ HTTP client.

## 8. Quy ước khi viết test mới

- Đặt test **cùng package** với class cần test: `src/test/java/com/aiinterview/platform/<same path>`.
- Tên method: `method_tìnhHuống_kỳVọng`; `@DisplayName` viết tiếng Việt **không dấu** (theo các test
  có sẵn của repo) để không phụ thuộc charset khi đọc log.
- **Mỗi test method có 3 khối comment `// Arrange:` → `// Act` → `// Assert:`** (tiếng Việt có dấu, nói
  *vì sao* dựng dữ liệu như vậy chứ không mô tả lại code). Khi lời gọi và khẳng định nằm chung một dòng
  (`assertThatThrownBy(() -> service.x(...))`) thì gộp thành `// Act & Assert:`. Class javadoc có thêm
  một dòng `<p>Mỗi test chia 3 khối comment: <b>Arrange</b> → <b>Act</b> → <b>Assert</b>.</p>`.
  Comment **không** thay `@DisplayName`: tên test vẫn tiếng Việt không dấu, comment giữ bối cảnh
  nghiệp vụ.
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
- Gọi HTTP trong code production → đừng `new HttpClient()` trong class, hãy tiêm từ `HttpClientConfig`
  rồi dùng `StubHttpClient` trong test. Gọi mạng thật trong unit test là thứ duy nhất không được phép
  (test phải chạy được khi offline).
- Helper test dùng chung (như `StubHttpClient`) đặt **cùng package** với test và **không** đặt tên
  `*Test`; nhớ thêm vào `$sources` (không thêm vào `$tests`) của script.
- Sửa bất kỳ file `src/main` nào → chạy `scripts/testcheck/rebuild-main.ps1` trước khi chạy test (§2.5).

## 9. Batch 1 — Security core (89 test)

Mục tiêu: phủ lớp bảo vệ mà **mọi** endpoint dựa vào — mint/đọc JWT, filter, phiên đăng nhập, đổi mật
khẩu và ghi avatar. Chạy bằng `run-security-tests.ps1`, không cần Docker.

| Class | Test | Nội dung |
|---|---|---|
| `security/jwt/JwtUtilTest` | 16 | access / refresh / 2FA: mint + validate, sai secret, hết hạn, token rỗng/rác |
| `security/jwt/JwtFilterTest` | 14 | nhánh fail-open: 12 trường hợp từ chối + nhánh hợp lệ (authentication, `setAttribute(sid)`, `touch`) |
| `security/utils/JwtUtilsTest` | 5 | refresh mint access mới, `tv` lấy từ users chứ không từ claim, refresh token giữ nguyên |
| `security/utils/SecurityUtilsTest` | 7 | `X-Forwarded-For` lấy hop đầu, fallback `remoteAddr`, `getCurrentUser` 3 nhánh |
| `security/utils/UserAgentParserTest` | 12 | Edge/Opera thắng Chrome, iPhone/iPad, Android điện thoại vs máy tính bảng, UA null, 3 tổ hợp còn lại qua `@CsvSource` |
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

## 10. Batch 2 — OAuth đầy đủ nhánh (bộ Auth 70 → 104 test, net +34)

Mục tiêu: đóng khoảng trống lớn nhất còn lại của folder 00 — mọi nhánh **sau** lời gọi HTTP của
Google / GitHub / SSO. Không cần Docker. Chạy chung bằng `run-auth-tests.ps1`.

Con số: 3 class OAuth giờ có **44 test** (17 + 17 + 10); 7 test tiền kiểm tra cũ đã được thay thế nên
tổng bộ Auth tăng thực tế 34.

### 10.1. Refactor R1 — tiêm `HttpClient` (thay đổi `src/main` duy nhất)

Trước đây cả 3 class đều có `private final HttpClient httpClient = HttpClient.newBuilder()...` — không
có điểm inject nên không thể giả lập Google/GitHub/IdP.

| File | Thay đổi |
|---|---|
| `config/HttpClientConfig.java` | **Mới**: `@Bean HttpClient oauthHttpClient()` — `connectTimeout(10s)`, không đổi redirect policy để hành vi giữ nguyên |
| `service/auth/impl/GoogleAuthInterfaceImpl.java` | Bỏ khởi tạo inline → `private final HttpClient httpClient;` (Lombok thêm thành tham số thứ 3) |
| `service/auth/impl/GithubAuthInterfaceImpl.java` | Như trên |
| `service/auth/impl/SsoAuthInterfaceImpl.java` | Như trên |

Hai quyết định có chủ ý:

1. **Chỉ tiêm `HttpClient`, không tiêm `ObjectMapper`.** Trong context hiện tại **không** có bean
   `ObjectMapper`; thêm tham số đó sẽ làm Spring không khởi động được. Refactor phải
   behaviour-preserving nên `ObjectMapper` giữ nguyên `new ObjectMapper()` trong class.
2. **Không đổi redirect/timeout khác.** Bean mới chỉ đặt `connectTimeout(10s)` đúng như client cũ.

Vì constructor đổi, **phải rebuild** 4 file này vào `target/classes` (§2.5). Nếu quên, test compile
được nhưng chết với `NoSuchMethodError` — đây chính là lý do `rebuild-main.ps1` ra đời.

### 10.2. Kết quả

| Class | Test | Nội dung |
|---|---|---|
| `service/auth/impl/GoogleAuthInterfaceImplTest` | 17 | tokeninfo: `aud` / `iss` / `exp` / `email_verified` / email rỗng, lỗi mạng, status ≠ 200, user mới / cũ / xóa mềm / bị khóa, `name` fallback, chỉ gọi `tokeninfo` 1 lần |
| `service/auth/impl/GithubAuthInterfaceImplTest` | 17 | 3 lời gọi tuần tự, từ chối code, `/user` 500, `/user/emails` (primary → first verified → không có), 503 bị nuốt, user mới dùng `login` làm tên |
| `service/auth/impl/SsoAuthInterfaceImplTest` | 10 | `getPublicConfig`, cache discovery, discovery 503 / thiếu endpoint / lỗi mạng, login chưa cấu hình / thiếu code / bị từ chối / thiếu `id_token` |

Tổng sau Batch 2: **317 test / 26 class / 0 fail** (auth 104, security 89, core 124).

### 10.3. Ba điều đáng nhớ

1. **`verifyIdToken` của SSO vẫn là gap.** `NimbusJwtDecoder.withJwkSetUri(...)` tự fetch JWKS, không đi
   qua `HttpClient` được tiêm. Mọi thứ **trước và tới** bước exchange thì đã phủ.
2. **`StubHttpClient` là fake, không phải mock.** Nó giữ thứ tự response **và** lỗi trong cùng một hàng
   đợi, nên assert được "gọi endpoint nào, mấy lần, theo thứ tự nào" — thứ mà `mock(HttpClient.class)`
   không làm rõ được vì `send` là generic.
3. **Đừng kỳ vọng lỗi mạng ở `/user/emails`.** Nhánh đó `catch (Exception) → return ""`; test ghi lại
   đúng hành vi này thay vì hành vi "đúng lý thuyết".

## 11. Nhật ký thay đổi

Thêm mới (chưa commit):

| File | Nội dung |
|---|---|
| `src/test/java/com/aiinterview/platform/service/auth/impl/LoginInterfaceImplTest.java` | 14 test cho `POST /auth/login` |
| `src/test/java/com/aiinterview/platform/controller/auth/AuthControllerSessionTest.java` | 14 test cho `refresh` / `me` / `logout` / `register` / `client-id` |
| `src/test/java/com/aiinterview/platform/service/auth/impl/GoogleAuthInterfaceImplTest.java` | 17 test Google OAuth, đủ nhánh sau lời gọi `tokeninfo` (Batch 2) |
| `src/test/java/com/aiinterview/platform/service/auth/impl/GithubAuthInterfaceImplTest.java` | 17 test GitHub OAuth, assert thứ tự 2–3 lời gọi HTTP (Batch 2) |
| `src/test/java/com/aiinterview/platform/service/auth/impl/SsoAuthInterfaceImplTest.java` | 10 test SSO / OIDC: config, cache discovery, exchange (Batch 2) |
| `src/test/java/com/aiinterview/platform/service/auth/impl/StubHttpClient.java` | Fake `HttpClient` dùng chung (không phải `*Test`, phải khai báo trong `$sources`) |
| `src/main/java/com/aiinterview/platform/config/HttpClientConfig.java` | **Production (R1)**: bean `HttpClient oauthHttpClient()` |
| `src/main/java/com/aiinterview/platform/service/auth/impl/GoogleAuthInterfaceImpl.java` | **Production (R1)**: `HttpClient` thành tham số constructor |
| `src/main/java/com/aiinterview/platform/service/auth/impl/GithubAuthInterfaceImpl.java` | **Production (R1)**: như trên |
| `src/main/java/com/aiinterview/platform/service/auth/impl/SsoAuthInterfaceImpl.java` | **Production (R1)**: như trên |
| `src/test/java/com/aiinterview/platform/security/jwt/JwtUtilTest.java` | 16 test token factory (access / refresh / 2FA) |
| `src/test/java/com/aiinterview/platform/security/jwt/JwtFilterTest.java` | 14 test filter, toàn bộ nhánh fail-open |
| `src/test/java/com/aiinterview/platform/security/utils/JwtUtilsTest.java` | 5 test bước refresh |
| `src/test/java/com/aiinterview/platform/security/utils/SecurityUtilsTest.java` | 7 test IP + current user |
| `src/test/java/com/aiinterview/platform/security/utils/UserAgentParserTest.java` | 12 test nhãn thiết bị |
| `src/test/java/com/aiinterview/platform/service/auth/impl/UserSessionServiceImplTest.java` | 12 test vòng đời phiên đăng nhập |
| `src/test/java/com/aiinterview/platform/service/auth/impl/UserServiceImplTest.java` | 23 test đổi mật khẩu + avatar |
| `scripts/testcheck/build-classpath.ps1` | Dựng argfile + classpath offline (Batch 2 thêm 3 jar: `oauth2-jose`, `oauth2-core`, `nimbus-jose-jwt`) |
| `scripts/testcheck/TestRunner.java` | JUnit Platform launcher thay `mvn test` |
| `scripts/testcheck/compile-and-run.ps1` | Engine compile + chạy |
| `scripts/testcheck/rebuild-main.ps1` | Rebuild file `src/main` đã sửa vào `target/classes` (có Lombok + guard `javap`) |
| `scripts/testcheck/run-auth-tests.ps1` | Bộ 104 test folder 00 (Batch 2: +`StubHttpClient`, +`SsoAuthInterfaceImplTest`) |
| `scripts/testcheck/run-security-tests.ps1` | Bộ 89 test Batch 1 |
| `scripts/testcheck/run-core-tests.ps1` | Bộ 124 test luồng nghiệp vụ chính |
| `docs/testing.md` | Tài liệu này |

Lần đầu (folder 00 + core flow + Batch 1): **không** sửa `src/main`, không sửa `pom.xml`.
Batch 2: sửa `src/main` đúng 4 file (refactor R1, hành vi không đổi) và **không** sửa `pom.xml` — 3 jar
của SSO (`spring-security-oauth2-jose`, `spring-security-oauth2-core`, `nimbus-jose-jwt`) đã có sẵn
trong `.m2` với đúng version `pom.xml` khai báo, chỉ cần thêm vào allow-list của harness.

### 11.1. Lỗi đã gặp riêng ở Batch 2

| Triệu chứng | Nguyên nhân thật | Cách sửa |
|---|---|---|
| `package lombok does not exist` khi rebuild | Lombok chỉ nằm trên `-processorpath`, không có trên `-cp` | thêm lombok vào cả classpath lẫn processorpath |
| `NoSuchMethodError: <init>(UserRepository, PasswordEncoder)` lúc chạy test | `target/classes` còn constructor cũ (IDE chỉ build lại 1 trong 3 file) | chạy `rebuild-main.ps1`, nó `javap` kiểm tra lại constructor |
| Test lỗi mạng của GitHub báo sai message (`Không thể trao đổi mã GitHub`) | `StubHttpClient` ưu tiên cờ lỗi hơn hàng đợi response → lỗi rơi vào lời gọi exchange | đưa lỗi vào cùng hàng đợi với response |

### 11.2. Bổ sung comment Arrange/Act/Assert cho toàn bộ 317 test

Quét comment **không đổi hành vi**: chỉ thêm dòng comment vào 26 file test của cả 3 bộ; không sửa
`src/main`, không sửa `pom.xml`, không đổi một assertion nào.

| Hạng mục | Chi tiết |
|---|---|
| Phạm vi | 26 class / 317 test (auth 104 + security 89 + core 124) |
| Quy ước | mỗi test 3 khối `// Arrange:` → `// Act` → `// Assert:`; gộp `// Act & Assert:` khi gọi và assert cùng dòng; class javadoc thêm 1 dòng nhắc quy ước (§8) |
| Ngoài phạm vi | 9 `*Test.java` không nằm trong script nào của harness: `AdminPositionServiceImplTest`, `AvatarReportEvaluatorTest`, `TwoFactorIntegrationTest` (cần Docker), `CvServiceImplTest`, `EmailServiceImplGmailTest`, `InterviewReminderServiceImplTest`, `QuestionPoolServiceImplTest`, `PositionRecommendationServiceImplTest`, `RegisterRequestValidationTest` |
| Kiểm chứng | chạy lại 3 script: 104/104, 89/89, 124/124 — exit 0, không case nào đổi kết quả |
| Số liệu đã sửa theo code | Google 15 → 17, GitHub 16 → 17, `UserAgentParserTest` 11 → 12 (9 `@Test` + 3 dòng `@CsvSource`) |

Lỗi có sẵn trong test cũ **giữ nguyên**, chỉ ghi chú bằng comment chứ không "sửa lén": import lặp
`TotpVerifier` trong `TwoFactorServiceImplTest`; gọi `forgotPassword` hai lần trong
`PasswordResetServiceImplTest.softDeletedUserReturnsTheSameMessageWithoutIssuingAToken`; message có
khoảng trắng đầu `" User not found with id : 9"` trong `UserServiceImplTest`; lỗi chính tả
"Khoảng thởi gian không hợp lệ" trong `SlotServiceImpl`.

Kế hoạch gốc của đợt test nằm ở
`C:\Users\LENOVO\AppData\Roaming\Qoder\SharedClientCache\cache\plans\Unit_First_Test_Strategy_6edac833.md`
(Phase 1: folder 00 + core flow + Batch 1 + Batch 2 xong; Phase 2 contract test chờ Docker).
