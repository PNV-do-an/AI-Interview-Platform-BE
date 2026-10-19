# Implementation Plan: BE cho màn User Directory (delta 001 → mockup)

**Branch**: `feature/mange-user` | **Date**: 2026-10-19 | **Spec**: [spec.md](./spec.md)
**Base**: 001 đã build xong (11 endpoint admin + audit + vòng đời), đo 79/79 test + M-1..M-18.

## Summary

Mockup không đòi viết lại 001; nó đòi **6 khả năng mới** đặt lên nền đã có. Kế hoạch tách phần
"render được UI bằng API đã tồn tại" (Lô A, **không sửa schema**) khỏi phần "phải ghi dữ liệu mới"
(Lô B: mời tài khoản, resend/revoke link, sửa hồ sơ, CSV) và phần cắt bỏ (Lô C).
Bốn câu Clarify C-1..C-4 đã anh Huy chốt ngày 2026-10-19, trong đó C-1 và C-3 giữ nguyên quyết định
của 001 (**không** cột `last_active_at`, **không** actor hệ thống trong audit), nên **schema `users`
không đổi ở kỳ này**.

## Current-state matrix (đọc code, không đoán)

| Khối trong mockup | BE hiện có | Khoảng trống |
|---|---|---|
| Bảng thành viên: tên/email, Role, Status, Joined Date, phân trang | `GET /admin/users` (search ≤100 ký tự có escape, `role`, `status`, `page/size≤100`, sort `createdAt DESC`) | **không còn gì** — dùng được ngay |
| Hành động dòng: Lock / Reactivate / more_vert | `PATCH /{id}/lock`, `/unlock`, `/status`, `DELETE`, `POST /restore`, `POST /reset-password`, `PATCH /role` | **không còn gì** |
| Tab Audit Log (ai, lên ai, kết quả) | `GET /admin/audit-logs` + `targetEmail`/`targetFullName`/`succeeded`/`reason`, filter `action`/`from`/`to` | thiếu **kiểu sự kiện** "Invitation Sent" → A4 thêm action; dòng actor-hệ-thống trong mockup **bỏ** theo C-3 |
| 4 KPI card | — | **không có endpoint đếm**; `login_attempts`/`register_attempts` không đủ cho "30 ngày gần nhất" |
| Huy hiệu đếm trên tab | `Page.totalElements` từng bộ lọc | cần 1 request đếm **nhiều bộ lọc cùng lúc** |
| Status "Pending Invite" | enum `ACTIVE|INACTIVE|LOCKED|DELETED` (`model/enums/UserStatusFilter.java`) | **không phân biệt** "chưa verify" với "bị tắt" |
| "Last Active" | `User` **không có** cột mốc hoạt động (18 field + `locked_at`); chỉ có `login_attempts(email, created_at)` | **thiếu** — C-1 chốt: suy từ `login_attempts`, chỉ đúng nghĩa "đăng nhập gần nhất" |
| Add User / Invite Members | luồng user-side `POST /auth/register` (tạo `enabled=false` + mail verify) và `EmailService.sendVerificationEmail` **đã tồn tại** | chưa có endpoint **admin** tạo/mời + audit + role do admin chọn |
| Resend (dòng pending) | `EmailService.sendResendVerificationEmail` + `POST /auth/resend-verification` (user-side) | chưa có đường admin gọi, chưa có audit |
| Revoke (dòng pending) | `users.verification_token` + `verification_token_expiry` | chưa có endpoint xóa token |
| Export CSV | — | **không có** |
| "Edit" hồ sơ (tên/email/điện thoại) | `User.fullName/email/phone` | chưa có endpoint admin sửa |
| Tab Role Permissions | quyền nằm hard-code: `JwtSecurityConfig` URL rule + `@PreAuthorize` từng method | chưa có API đọc ma trận; ma trận đầy đủ để lại kỳ sau |
| Audit "Automated policy check" (actor = hệ thống) | `admin_audit_log.admin_id` **NOT NULL**, R10 đã cấm `0`/`system` và đã đo | **xung đột trực tiếp với R10** |
| Checkbox nhiều dòng | từng endpoint đơn lẻ | mockup **không có** nút bulk → đề xuất bỏ khỏi BE |

## Technical Context

- Stack không đổi: Java 17, Spring Boot 3.2.5, JPA `ddl-auto: update` (Flyway tắt), Gmail API qua
  `EmailService`, JWT + `tokenVersion`. **Không thêm dependency** (Constitution VII).
- Cột mới đều `nullable` để `ddl-auto` thêm được mà không phá dữ liệu (bài học `known-gaps.md` mục 1).
- Kênh đo lại được không cần Render: fake Gmail ở `:4590` (xem quickstart 001), DB nhánh
  `admin_mgnt_verify` ở container `aiinterview-mysql:3307`, app cổng `8090`.

## Scope decision — 3 lô, theo chi phí thay đổi (C-1..C-4 đã chốt 2026-10-19)

### Lô A — render đủ UI bằng API đã có + số đếm (**không sửa schema**)

| # | Việc | File |
|---|---|---|
| A1 | `GET /api/v1/admin/users/stats?search=&role=` → `{total, active, pendingVerification, locked, disabled, deleted, activeLast30Days}` — JPQL `count ... case when` trên `users`, **dùng lại đúng predicate của `GET /users`** (D1) để huy hiệu đếm khớp danh sách; `activeLast30Days` theo C-1 = số email có `login_attempts.success=1` trong 30 ngày gần nhất (đếm distinct email, **không** đếm user id) | `AdminUserController`, `AdminUserService(Impl)`, `UserRepository`, `LoginAttemptRepository`, DTO mới `AdminUserStatsResponse` |
| A2 | Thêm giá trị lọc `PENDING` vào `UserStatusFilter` (điều kiện `verification_token IS NOT NULL`) + giữ nguyên 4 giá trị cũ; `DELETED` vẫn 400 ở `/users` | `model/enums/UserStatusFilter.java`, `JpaUserRepository.findAllWithFilters` |
| A3 | Thêm `pendingVerification` (boolean) + `lastLoginAt` (nullable, suy từ `login_attempts`) vào `UserSummaryResponse`; **một** query `max(created_at) group by email` cho cả trang, không N+1 | `model/dto/response/UserSummaryResponse.java`, `LoginAttemptRepository` |
| A4 | Audit action mới: `INVITE_USER`, `RESEND_INVITATION`, `REVOKE_INVITATION`, `EXPORT_USERS`, `UPDATE_PROFILE` | `model/enums/AuditAction.java` |

Rủi ro A2: `status` là hợp đồng FE đang đọc. Thêm enum thì an toàn (không xóa giá trị cũ); **không** đổi
ý nghĩa `INACTIVE`.
Rủi ro A1/A3 (chấp nhận theo C-1): `login_attempts` khóa bằng **email** ⇒ tài khoản đổi email mất chuỗi
đăng nhập; tài khoản chưa từng đăng nhập trả `lastLoginAt = null` (FE hiển thị "Never" — đúng mockup).

### Lô B — khả năng ghi dữ liệu mới (schema + luồng mail)

| # | Việc | Ghi chú hợp đồng |
|---|---|---|
| B1 | `POST /api/v1/admin/users` (họ tên, email, role, số điện thoại — **không** có `password`, C-4) → tạo `enabled=false` + `verification_token` UUID, gửi mail mời, audit `INVITE_USER`; email hỏng ⇒ **502 + rollback** (không còn user mồ côi) | tái dùng `EmailService.sendVerificationEmail`; trùng email ⇒ **409** "Email đã tồn tại" — chặn bằng `existsByEmail` **trước khi ghi**, và bắt riêng `DataIntegrityViolationException` ngay trong method tạo user để race 2 request cùng email vẫn ra 409 (không phải 500), **không** thêm handler toàn cục (001 đã bỏ handler đó vì hết đường tới) |
| B2 | `POST /api/v1/admin/users/{id}/invitation/resend` | `sendResendVerificationEmail`; user đã verify ⇒ 400 "Tài khoản đã xác thực email"; mail fail ⇒ 502 + rollback token |
| B3 | `DELETE /api/v1/admin/users/{id}/invitation` — thu hồi link, **không** khóa tài khoản | set `verification_token=null`, `verification_token_expiry=null`; audit `REVOKE_INVITATION`; phục hồi được bằng B2 |
| B4 | `PATCH /api/v1/admin/users/{id}` — sửa `fullName`/`email`/`phone` | đổi email ⇒ unique + **reset verify** (email mới chưa chứng minh ⇒ `enabled=false` + token mới + mail): chặn lúc ghi, không để "sống bằng email chưa verify"; kèm hệ quả C-1 là mất chuỗi `lastLoginAt` |
| B5 | `GET /api/v1/admin/users/export?…cùng bộ lọc` → `text/csv`, **trần 10.000 dòng**, vượt ⇒ 400 | stream qua `StreamingResponseBody`, không chất `List` cả bảng; audit `EXPORT_USERS`; **không** lộ `password`/token trong CSV |

**B6 (cột `users.last_active_at`) ĐÃ BỎ** theo C-1: không sửa schema, không chạm `JwtFilter`.
Nếu sau này cần "last active" đúng nghĩa thì đó là spec riêng, không phải phần còn sót của 002.

Lưu ý B1/B4 về email của tài khoản **đã xóa mềm**: hàng xóa mềm vẫn giữ unique email (đã đo ở 001 R7),
nên mời lại email đó ⇒ **409** với message chỉ đường "khôi phục tài khoản đã xóa" (`POST /{id}/restore`),
chứ không phải message "Email đã tồn tại" cụt. FE cần message này để gợi ý đúng hành động.

### Lô C — đề xuất KHÔNG làm kỳ này (ghi rõ để FE khỏi chờ)

- **Bulk actions**: mockup có checkbox nhưng không có nút hàng loạt; endpoint số nhiều làm hỏng hợp đồng
  "mỗi nỗ lực từ chối là 1 dòng audit + 502 rollback" của 001. FE gọi loop từng id bằng endpoint đã có.
- **RBAC động / sửa ma trận quyền qua UI** (tab Role Permissions): chỉ đọc ma trận tĩnh, nếu làm.
- **Chuông thông báo + "Real-time updates"**: không SSE; FE poll `stats`.
- **Mã `USR-4091`**: không thêm `public_id`; FE hiển thị `id`.
- **Dòng audit actor hệ thống** ("Automated policy check"): **đã chốt C-3 — không làm**, R10 của 001 giữ
  nguyên (`admin_id NOT NULL`, không `0`/`system`). Anh Huy đã nhận việc bỏ dòng đó khỏi mockup. Nếu FE
  vẫn render dòng "hệ thống tự động" thì nguồn dữ liệu cho nó **không tồn tại** trong BE kỳ này.

## Architecture Decisions

- **D1 — một nguồn sự thật cho bộ lọc**: `stats` và `export` phải dùng **cùng** predicate builder với
  `GET /users` (một hàm `buildFilters(search, role, status)`), nếu không huy hiệu đếm sẽ lệch danh sách —
  đây là AC-1 và là bug phổ biến của màn dạng này.
- **D2 — không tách service mới**: mở rộng `AdminUserServiceImpl` (đã có `attempt(...)` wrap audit +
  `requireAdmin()` + `findLiveUserOrThrow`) thay vì tạo `AdminDirectoryService`; mọi endpoint mới tự động
  thừa hợp đồng 404/400/audit/502 đã đo ở 001.
- **D3 — "pending" là trạng thái suy diễn, không phải cột** (C-2 đã chốt): `verification_token IS NOT NULL`
  là đủ; **không** có `invited_by`, **không** có `last_active_at` (C-1). Kỳ này schema `users` **không đổi**.
- **D4 — mail vẫn là một phần của transaction ghi** (R1 của 001): thêm user mới nào gửi mail cũng đặt
  **sau** `saveAuditLog` và để `MailDeliveryException` rollback toàn bộ.
- **D5 — CSV là endpoint GET trả file, không phải job bất đồng bộ**: dữ liệu màn này nhỏ (hàng nghìn dòng),
  job + bảng trạng thái = thêm phức tạp không cần (Constitution VII).

## Constitution Check

| Nguyên tắc | Đạt chưa | Ghi chú |
|---|---|---|
| II — số đo thật | ☐ | mọi endpoint mới phải có dòng M-x trong quickstart, không ghi "đã chạy" |
| III — chặn lúc ghi | ☐ | B1/B3/B4/B5 đều validate ở service, không để trạng thái hỏng lộ ở GET |
| IV — audit thật | ☐ | A4 thêm action; C-3 chưa chốt thì **không** cho actor hệ thống |
| VI — phòng vệ nhiều tầng | ☑ sẵn | endpoint mới nằm dưới `/api/v1/admin/**` (URL rule 001) + `@PreAuthorize` |
| VII — phạm vi tối thiểu | ☐ | Lô C là chỗ cắt; C-1 chọn (c) là thay đổi lớn nhất, cần cân nhắc |
| VIII — đo trước khi vòng | ☑ | C-1 chốt hướng **không sửa schema** sau khi đã kiểm tra cấu trúc `login_attempts` (khóa email, có index `idx_login_email_timestamp`, không job dọn) thay vì đoán |

## Testing Strategy

- **Integration**: mở rộng `AdminUserControllerIntegrationTest` (Testcontainers chạy được local — công thức
  trong quickstart 001), thêm: `stats` khớp `totalElements` từng bộ lọc; `PENDING` filter; invite happy +
  trùng email 409 + mail-fail 502 và **không còn user**; resend trên user đã verify 400; revoke rồi login vẫn
  bị chặn; CSV đúng số dòng + trần; edit email đổi trạng thái verify.
- **Hồi quy 001**: ma trận M-1..M-18 chạy lại nguyên vẹn (SC-005 của 001) — đặc biệt enum `status` thêm
  giá trị mới không được làm đổi 4 response cũ.
- **Đo thật**: dùng lại harness `target/verify/` (fake Gmail + DB nhánh) — không cần credential Gmail,
  không đụng staging.
- **CI**: `mvn -B clean test` trên Actions vẫn là cổng chốt trước merge `dev`.

## Phases (thứ tự, không ước lượng thời gian)

1. **Lô A** (A1→A4, không sửa schema) → đo `stats` khớp `totalElements` từng bộ lọc → merge độc lập được.
2. **Lô B** theo thứ tự B1 → B2 → B3 → B4 → B5 (mỗi task: test trước, rồi service, rồi controller, rồi đo HTTP
   thật bằng harness fake-Gmail như 001).
3. **Rà hợp đồng FE**: `status` thêm `PENDING`, `UserSummaryResponse` thêm 2 field — đối chiếu screen admin
   đang dùng (`UserDetailModal`, `UsersPage`) để không vỡ render.
4. **CI**: push branch, chờ `mvn -B clean test` xanh rồi mới merge `dev` (chỉ khi anh đồng ý push).

## Open risks

- **R-1 (chấp nhận có ý thức, theo C-1)**: `login_attempts` khóa theo **email**, không theo `user_id`; user đổi
  email (B4) làm đứt chuỗi "last login". Hệ quả hiển thị: dòng đó hiện "Never" dù người dùng từng hoạt động.
- **R-2**: "last active" thật (mọi request, không chỉ đăng nhập) **không tồn tại** trong BE kỳ này. FE **không**
  được đặt nhãn "Hoạt động 10 phút trước" cho giá trị lấy từ `login_attempts`; phải là "Đăng nhập gần nhất".
- **R-3**: `lastLoginAt` cho cả trang phải tính bằng **một** query `max(created_at) … group by email` với danh
  sách email của trang; làm N+1 (1 query/email) thì mỗi trang 100 user = 100 lượt đọc `login_attempts`, và
  bảng đó **không có job dọn** (known-gaps mục attempts). Index `idx_login_email_timestamp` đã có, nên bản thân
  query không chậm — chỉ sợ gọi nhiều lần.
- **R-4**: B4 (admin đổi email) va chạm logic verify; phải đo cả luồng "đổi email ⇒ mất đăng nhập" để không tạo
  tài khoản không vào được hệ thống.
- **R-5**: `stats` và `export` phải dùng chung predicate với `GET /users` (D1). Mỗi chỗ tự dựng `WHERE` thì huy
  hiệu đếm lệch danh sách ngay lần đầu có search — đó là AC-1, và là lỗi hay gặp nhất của màn dạng này.

## Complexity Tracking

> Không thêm dependency, không migration Flyway, **không sửa schema `users`** (B6 và actor-hệ-thống đều
> đã bị cắt ở Clarify). Chỗ nặng nhất còn lại là B5 (CSV streaming + trần dòng) và B4 (đổi email ⇒ reset
> verify), mỗi cái đều có test riêng trong phần Testing Strategy.
