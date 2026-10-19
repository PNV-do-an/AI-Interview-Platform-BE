# Phase 0 Research: Hoàn thành quản lý tài khoản (admin)

**Date**: 2026-10-19 | **Plan**: [plan.md](./plan.md) | Mọi "bằng chứng" là đường dẫn + số dòng đọc
từ code tại tip `f773246` (branch `feature/mange-user`).

## R1. Mail fail trong thao tác admin → 502 + rollback

- **Decision**: bỏ toàn bộ `try/catch (Exception)` quanh `emailService.*` trong `AdminUserServiceImpl`
  (dòng 74-78, 93-97, 115-119, 135-139); để `MailDeliveryException` lan ra ngoài method `@Transactional`.
- **Rationale**: `EmailServiceImpl` đã ném `MailDeliveryException` (dòng 70/83/110/113),
  `GlobalExceptionHandler:80-85` đã map nó thành 502, và Spring rollback transaction khi RuntimeException
  thoát ra — đúng hợp đồng đã chốt cho register (`RegisterInterfaceImpl.java:41,91,137`) và forgot-password
  (`PasswordResetServiceImpl.java:83`). Không cần cơ chế mới.
- **Hệ quả audit**: bản ghi audit cũng bị rollback → DB trở về nguyên trạng, "không có gì xảy ra" là đúng
  bản chất. **Không** ghi vết "đã hủy" bằng `REQUIRES_NEW` (phức tạp, dễ log giả); spec US1.7 được nghiệm thu
  bằng: DB nguyên trạng + response 502 + log WARN có stacktrace (mục đo quickstart M-4).
- **Alternatives considered**:
  - Ghi `after_commit` event (`@TransactionalEventListener`) — **bị loại**: mail phải đi trước commit thì mới
    rollback được; gửi sau commit thì trạng thái đã đổi vĩnh viễn, trái hợp đồng 502+hoàn tác.
  - Nuốt lỗi như hiện tại nhưng đổi message thành "chưa gửi được mail" — **bị loại**: trái chốt 2026-10-19.

## R2. Guard "còn ít nhất một ROLE_ADMIN" bằng phép đếm, áp cho cả đổi vai trò lẫn xóa

- **Decision**: thêm `long countByRoleAndDeletedAtIsNull(Role role)` vào `JpaUserRepository`; dùng trong
  `changeRole` (thay `Pageable.unpaged()` hiện tại, dòng 154-155) và cả `deleteUser` (đường xóa hiện **không**
  có guard này).
- **Rationale**: nạp cả bảng vào RAM là lỗi hiệu năng + đường xóa lọt được admin cuối cùng (đo hiện trạng ở
  quickstart M-7).
- **Alternatives**: giữ unpaged load (loại — SC-001 yêu cầu không nổ 500/không nạp bảng); thêm cột `is_primary_admin` (loại — ngoài phạm vi).

## R3. Bộ lọc status/role: enum đóng + chặn ở cửa vào

- **Decision**:
  - `status` dùng type `UserStatusFilter` (giá trị hợp lệ `ACTIVE|INACTIVE|LOCKED|DELETED`) làm type của
    `@RequestParam` trong controller (thay `String` + `toUpperCase()` tùy tiện ở service:44).
  - `role` giữ type `Role` (enum đã có 4 giá trị).
  - Thêm `@ExceptionHandler(MethodArgumentTypeMismatchException)` trả **400** với message tiếng Việt liệt kê
    giá trị hợp lệ, thay vì rơi vào handler `RuntimeException` → 500 rò message nội bộ (`GlobalExceptionHandler:97-103`).
- **Rationale**: hiện giá trị lạ bị **lặng lẽ trả trang rỗng** (`JpaUserRepository:46-51` chỉ so 3 chuỗi hard-code);
  Constitution III bắt chặn lúc ghi/đọc ở cửa vào.
- **Alternatives**: giữ String + tự validate tay trong service (loại — trùng lặp, dễ quên khi thêm endpoint).

## R4. Phân trang hợp lệ: `@Validated` + `@Min/@Max`

- **Decision**: `@Validated` trên `AdminUserController`; `page @Min(0)`, `size @Min(1) @Max(100)` cho cả
  `GET /users` và `GET /audit-logs` (controller:28-29, 81-82).
- **Rationale**: `page=-1`/`size=0` hiện nổ `IllegalArgumentException` từ `PageRequest.of` → 500;
  `size=100000` kéo cả bảng.
- **Alternative**: clamp thay vì chặn (loại — che lỗi client, vi phạm Constitution III).

## R5. DELETED: query riêng cho tài khoản đã xóa mềm

- **Decision**:
  - Giữ `findAllWithFilters` cho 3 trạng thái sống (ACTIVE/INACTIVE/LOCKED).
  - Thêm `findDeletedWithSearch(search, pageable)` (chỉ `deletedAt IS NOT NULL` + search).
  - `status=DELETED` ⇒ đi query deleted, mọi `role` vẫn áp dụng được; các status khác + `DELETED` không bao giờ
    trả lẫn nhau.
- **Rationale**: `JpaUserRepository:42` hard-code `u.deletedAt IS NULL` nên giá trị `DELETED`
  (`UserSummaryResponse:18`) bất khả thi — admin không tìm lại được tài khoản xóa nhầm (US2.2).
- **Alternative**: một query với cờ `includeDeleted` và `:status` rỗng (loại — JPQL với 2 chế độ NULL rất khó
  đọc và khó test; 2 method rõ ràng hơn mà không thêm abstraction thừa).

## R6. LIKE escaping

- **Decision**: service escape `\` → `\\`, `%` → `\%`, `_` → `\_` rồi truyền xuống
  `LIKE ... ESCAPE '\\'`; trim; chặn chuỗi search > 100 ký tự bằng 400.
- **Rationale**: `JpaUserRepository:43-44` không escape ⇒ gõ `a%` khớp mọi dòng (báo cáo khảo sát mục 8.B.15).
- **Alternative**: dùng Criteria API (loại — rewrite query đã ổn, phạm vi tối thiểu).

## R7. Khôi phục: chỉ xóa `deletedAt`, không cần snapshot trạng thái

*(đính chính khi build — bản kế hoạch trước đây định ghi JSON snapshot vào audit `DELETE_USER` và chặn
409 khi email trùng; cả hai đều thừa, lý do ở dưới)*

- **Decision**: `deleteUser` **chỉ** set `deletedAt=now` + bump `tokenVersion`; nó không đụng
  `enabled`/`locked`/`role`. Vì vậy `restoreUser` chỉ cần `deletedAt=null` là tài khoản trở lại đúng
  trạng thái trước xóa — không đọc audit, không khôi phục trường nào khác.
  - chặn: không có `deletedAt` → 400 "Tài khoản không ở trạng thái đã xóa";
  - mọi thao tác khác trên tài khoản đã xóa mềm → 404 "Không tìm thấy tài khoản (đã bị xóa)".
- **Vì sao không có 409 xung đột email**: cột `email` unique và hàng xóa mềm **vẫn giữ email đó**,
  nên không tài khoản nào khác đăng ký/chiếm được chuỗi email ấy (`existsByEmail` vẫn thấy hàng deleted).
  Xung đột restore là trạng thái không dựng được → không viết code chặn.
- **Vì sao không cần snapshot**: snapshot chỉ có giá trị nếu thao tác xóa làm thay đổi trạng thái. Đo lại
  `AdminUserServiceImpl.deleteUser` thấy nó không sửa `enabled`/`locked`/`role` ⇒ snapshot là dữ liệu
  trùng lặp, và còn phải giữ đồng bộ qua nhiều vòng xóa/restore (Constitution VII).
- **Hệ quả phụ**: `UserDetailResponse.enabled` lấy từ `User.isEnabled()` = `enabled && deletedAt == null`,
  nên chi tiết của tài khoản đã xóa mềm hiển thị `enabled:false` dù cột trong DB vẫn `1`. Đây là
  "trạng thái hiệu lực đăng nhập", không phải cột; đã có `status:"DELETED"` + `deletedAt` nói rõ.
- **Alternative**: thêm `pre_delete_enabled`/`pre_delete_locked` vào `users`, hoặc JSON snapshot vào
  `detail` của audit (cả hai loại — không có nhu cầu đo được).

## R8. `enabled` chưa từng có đường admin nào chạm tới

- **Decision**: `PATCH /api/v1/admin/users/{id}/status` body `{"enabled": true|false}`.
  - `enabled=false`: tắt đăng nhập, bump `tokenVersion`, audit `DISABLE_USER`, **không gửi mail**.
  - `enabled=true`: chỉ bật khi tài khoản chưa xác minh email **đã từng** được admin tắt — hệ thống không
    tự bỏ yêu cầu xác minh; nếu user chưa từng verify email (`verificationToken` còn hạn) thì trả message
    "Tài khoản chưa xác thực email" và **không** set `enabled` (giữ nguyên chốt "mail xác thực là bắt buộc").
- **Rationale**: con đường duy nhất bật `enabled` hiện tại là verify email (`RegisterInterfaceImpl:113`).
- **Alternative**: admin bypass luôn xác minh (loại — phá hợp đồng đã deploy 19/10).

## R9. Thu hồi phiên tức thời

- **Decision**: bump `user.tokenVersion` trong **khóa / tắt / xóa mềm / đổi vai trò**; giữ nguyên trong
  mở khóa / khôi phục.
- **Rationale**: `JwtFilter:49` đã chặn im lặng khi `!enabled || locked || deletedAt != null`; refresh token
  chết theo `tokenVersion` (cơ chế `AuthController/refresh` đã dùng `users.token_version`, xem known-gaps:49).
  Access token vẫn sống tới 24h nếu chỉ dựa `JwtFilter` cho các endpoint `permitAll` — bump tokenVersion là
  điều kiện đủ cho SC-002 ở đường refresh. **Đo thật** hành vi access-token-cũ trên endpoint `permitAll` và
  ghi kết quả vào phần verify (không cam kết đổi TTL trong plan này).
- **Alternative**: blacklist access token (loại — cần bảng/redis, ngoài phạm vi VII); rút TTL access token
  (loại — đổi hành vi login toàn hệ thống, phải anh Huy chốt riêng).

## R10. Audit log: admin thật, hành động đóng, có kết quả

- **Decision**:
  - Thêm enum `AuditAction` (VIEW_USER, LOCK_USER, UNLOCK_USER, DELETE_USER, RESTORE_USER, RESET_PASSWORD,
    CHANGE_ROLE, ENABLE_USER, DISABLE_USER) — cột vẫn là string, validate ở tầng service.
  - `resolveAdmin()` trả `null` ⇒ **từ chối thao tác ghi** (403 "Không xác định được tài khoản quản trị"),
    không ghi `adminId=0`/`"system"` giả (`AdminUserServiceImpl:209-210`).
  - Thêm cột `success` (boolean, not null default true) + `reason` (varchar nullable) vào `AdminAuditLog`;
    mọi nỗ lực bị guard từ chối ghi 1 dòng `success=false, reason=<message>` (US3.3 đã chốt).
  - `AuditLogResponse` thêm `targetEmail`, `targetFullName` (join `users` bằng JPQL LEFT JOIN, kể cả user đã
    xóa mềm) — hết cảnh id thô (known-gaps:57).
  - `GET /audit-logs` nhận `adminId`, `targetUserId`, `action`, `from`, `to` (ISO date) + phân trang.
- **Rationale**: Constitution IV + SC-004.
- **Alternative**: bảng audit mới + retention job (loại — ngoài phạm vi, chưa có nhu cầu đo được).

## R11. Lỗi 500 rò thông tin nội bộ + các handler còn thiếu

- **Decision**: `GlobalExceptionHandler`:
  - handler `RuntimeException` bỏ nối `"Internal server error: " + ex.getMessage()`, trả message nghiệp vụ
    chung + log stacktrace (dòng 97-103).
  - thêm handler `MethodArgumentTypeMismatchException` → 400 (kèm-liệt kê giá trị enum được phép),
    `ConstraintViolation`/`@Validated` param violation → 400, `HandlerMethodValidationException` → 400,
    `HttpMessageNotReadableException` → 400 "Dữ liệu gửi lên không đúng định dạng".
  - **Bỏ** kế hoạch `DataIntegrityViolationException` → 409: restore trùng email là trạng thái không dựng
    được vì hàng xóa mềm vẫn giữ unique email (R7).
- **Rationale**: FR-004, SC-005.
- **Alternative**: trả nguyên message (loại — lộ câu lệnh nội bộ/ngoại lệ DB cho client).

## R12. Token của email không còn trong DB → nổ 500

- **Decision**: `JwtFilter:59` bắt thêm `InvalidAccountException` (thực tế `UserServiceImpl:19-22` ném loại này,
  không nằm trong `JwtException | UsernameNotFoundException`) → xử lý như token không hợp lệ (đi tiếp anonymous),
  **không** thêm RuntimeException bắt mù quáng.
- **Rationale**: đây là bug thật nghi ngờ từ khảo sát; **phải đo lại trước khi sửa** (Constitution VIII):
  quickstart M-9 dùng token của email đã bị xóa vật lý khỏi DB để xác nhận hiện trạng 500.
- **Alternative**: để UserServiceImpl ném `UsernameNotFoundException` (loại — đổi hợp đồng của service đang dùng
  ở nhiều nơi, ảnh hưởng luồng login).

## R13. Phòng vệ nhiều tầng cho `/api/v1/admin/**`

- **Decision**: thêm `.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")` **trước** `anyRequest().permitAll()`
  (`JwtSecurityConfig.java:36-38`); cấu hình `httpBasic`/`formLogin` vẫn không bật; `exceptionHandling` trả envelope
  `ErrorModel` (401 anonymous / 403 sai role) thay vì body rỗng mặc định.
- **Rationale**: Constitution VI; hiện chỉ `@PreAuthorize` là lớp chặn duy nhất.
- **Alternative**: chỉ dựa annotation (loại — controller mới quên annotation là public).

## R14. Không đổi (chốt "không làm")

- Dashboard thống kê (FR-ADM-03), bulk actions, API admin tạo user, hard delete/GDPR anonymize, endpoint danh
  sách phiên đăng nhập, sửa TTL access token, dọn `ProfileController`/`ViewController` (đồ chết — ghi nhận, chưa đụng),
  migration Flyway mới, job dọn bảng attempt.
- **Lý do**: spec mục "Không làm lần này"; Constitution VII (phạm vi tối thiểu).

## R15. Ba lỗi chỉ lộ ra khi đo thật (không thấy từ đọc code)

Bật app thật + MySQL + kênh mail giả rồi gọi đủ chuỗi admin (quickstart M-8/M-10/M-17) thì gặp ba chỗ mà
đọc `AdminUserServiceImpl` không ra:

1. **`GlobalExceptionHandler:44-53` NPE ⇒ login trên tài khoản bị admin khóa trả 500.**
   `new HashMap<>(Map.of("lockedUntil", ex.getLockUntil()))` — `Map.of` không nhận giá trị `null`, mà
   `LoginInterfaceImpl:63-66` khóa-admin thì truyền `null` (chỉ khóa brute-force mới có `lockUntil`).
   Số đo trước sửa: `POST /auth/login` → `500 {"error":"Internal Server Error"}` (vượt cả envelope của
   advice). Sau sửa (dùng `HashMap` + chỉ put khi khác null): `423 "Tài khoản của bạn đã bị khóa bởi quản
   trị viên. Vui lòng liên hệ hỗ trợ."`. Có unit test `GlobalExceptionHandlerTest` giữ cả hai nhánh
   (khóa-admin null và khóa-tạm-trình có `lockedUntil`).
2. **`AuthController#getMe` NPE ⇒ 500 khi request không có danh tính.**
   Sau khi khóa/vô hiệu hóa/xóa vật lý, `JwtFilter` bỏ authentication, controller vẫn gọi
   `authentication.getName()`. Trước: `500 "Lỗi hệ thống..."` cho một request lẽ ra chưa được xác thực.
   Sau: chặn `null`/`AnonymousAuthenticationToken` → `InvalidAccountException` → `401 "Yêu cầu đăng nhập"`.
3. **Body lỗi ở tầng filter vỡ dấu tiếng Việt.**
   `writeError` đặt `Content-Type: application/json` không charset nên `getWriter()` dùng ISO-8859-1:
   `401 {"message":"Yu c?u ??ng nh?p"}`. Sửa thành `application/json;charset=UTF-8` + `setCharacterEncoding`.
   Nhân tiện đổi thông điệp `AccessDeniedException` trong advice từ `"Access denied"`/`"Authentication
   required"` sang tiếng Việt cho khớp phần còn lại của hợp đồng lỗi (FR-004).

- **Rationale**: Constitution II + VIII — chính bộ đo curl bắt được, nếu chỉ tin unit test
  (`@WithMockUser` không đi qua `JwtFilter`, không đụng `AuthController#getMe`) thì cả ba vẫn live.
- **Phòng vệ thêm**: guard "còn ≥1 admin sống" (`rejectIfLastAdmin`) **không tới được qua HTTP** khi
  actor ≠ target (mọi actor thành công cũng là admin sống, nên count luôn ≥ 2); giữ lại vì nó chặn
  race/nghiệp vụ data-repair — đường tự thao tác chính mình đã có self-guard trả 400 (M-7 đo được).

## NEEDS CLARIFICATION còn lại

**Không còn.** 4 câu chốt đã nhận từ anh Huy ngày 2026-10-19 (xem spec mục "Phạm vi đã chốt").
Riêng R8 (điều kiện `enabled=true` có giữ yêu cầu verify email không) là **quyết định kỹ thuật theo hợp đồng
đã deploy**, không đổi hành vi công khai; nếu anh muốn admin được bỏ qua verify email thì phải nói lại —
hiện plan chọn "không bỏ qua".
