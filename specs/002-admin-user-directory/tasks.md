# Tasks: BE cho màn User Directory (delta 001 → mockup)

**Input**: `spec.md` + `plan.md` trong thư mục này. **Clarify đã chốt 4/4** (C-1..C-4, 2026-10-19).
**Không sửa schema `users`.** Không thêm dependency.

Ký hiệu: `[P]` = chạy song song được (khác file); `[A]`/`[B]` = theo lô; `M-x` = phép đo thật phải ghi số
vào `quickstart-measure.md` của thư mục này (Constitution II — cấm ghi "đã chạy").

## Lô A — UI render được, không sửa schema

- [x] T001 [A] Tách **một** nơi dựng bộ lọc chung: hàm `buildUserFilter(search, role, status)` dùng cho
      `GET /users`, `GET /users/stats`, `GET /users/export` (D1, R-5). File: `JpaUserRepository.java`,
      `UserRepository.java`. **Chặn toàn bộ Lô A** — làm sai chỗ này là huy hiệu đếm lệch danh sách.
- [x] T002 [A] Thêm `PENDING` vào `model/enums/UserStatusFilter.java` (`verification_token IS NOT NULL`) +
      điều kiện trong `JpaUserRepository.findAllWithFilters`; giữ nguyên 4 giá trị cũ, `DELETED` vẫn 400 ở
      `/users`.
- [x] T003 [P] [A] `model/dto/response/AdminUserStatsResponse.java` (record): `total, active,
      pendingVerification, locked, disabled, deleted, activeLast30Days`.
- [x] T004 [P] [A] `model/enums/AuditAction.java`: thêm `INVITE_USER, RESEND_INVITATION, REVOKE_INVITATION,
      EXPORT_USERS, UPDATE_PROFILE` (A4).
- [x] T005 [A] `LoginAttemptRepository`: `Map<String,LocalDateTime> findLastSuccessByEmailIn(Collection<String>)`
      — **một** query `select email, max(created_at) ... where success = true and email in :emails group by email`
      (R-3, cấm N+1).
- [x] T006 [A] `UserSummaryResponse`: thêm `pendingVerification` (boolean) + `lastLoginAt` (nullable) (A3);
      `AdminUserServiceImpl.getUsers/getDeletedUsers` nạp `lastLoginAt` theo cách T005.
- [x] T007 [A] `AdminUserService(Impl).getStats(search, role)` đếm theo predicate chung (dùng T001) +
      `activeLast30Days` từ `login_attempts`.
- [x] T008 [A] `AdminUserController`: `GET /users/stats` với `@Validated` (`search ≤100`, `role` enum) —
      **phải đặt trước `anyRequest().permitAll()` đã có sẵn vì nằm dưới `/api/v1/admin/**`** (R13 của 001).
- [x] T009 [P] [A] Test Lô A trong `AdminUserControllerIntegrationTest`: `stats` khớp `totalElements` ở ≥3 bộ
      lọc lệch nhau (AC-1); `status=PENDING` trả đúng user còn `verification_token`; `status=ACTIVE` không
      đổi hành vi (hồi quy 001); `stats?role=FOO` ⇒ 400; Staff ⇒ 403, anonymous ⇒ 401 (AC-6).

## Lô B — khả năng ghi mới (theo sau A, tuần tự vì chung `AdminUserServiceImpl`)

- [x] T010 [B] `model/dto/request/AdminInviteUserRequest.java`: `fullName @NotBlank`, `email
      @NotBlank @Email`, `role @NotNull`, `phone` optional — **không có field `password`** (C-4).
- [x] T011 [B] `AdminUserServiceImpl.inviteUser(...)`: kiểm tra trùng email **trước khi ghi** (409; email của
      tài khoản xóa mềm ⇒ message gợi ý `POST /{id}/restore`), tạo `enabled=false` + `verification_token`
      UUID + expiry, `saveAuditLog(INVITE_USER)`, **sau đó** `emailService.sendVerificationEmail`; mail hỏng ⇒
      `MailDeliveryException` ⇒ 502 + rollback (user không còn). Bắt `DataIntegrityViolationException`
      **ngay trong method** này → 409 (race), không thêm handler toàn cục.
- [x] T012 [B] `AdminUserController`: `POST /users` message "Đã gửi email mời tài khoản" (chỉ nói khi mail
      thật sự đi — hợp đồng 001 T016).
- [x] T013 [P] [B] Resend: `POST /users/{id}/invitation/resend` → `sendResendVerificationEmail`, token mới,
      audit `RESEND_INVITATION`; user đã verify ⇒ 400 "Tài khoản đã xác thực email"; mail fail ⇒ 502 + token
      quay lại giá trị cũ.
- [x] T014 [P] [B] Revoke: `DELETE /users/{id}/invitation` → `verification_token=null`,
      `verification_token_expiry=null`, audit `REVOKE_INVITATION`; **không** đổi `enabled`/`locked`/`tokenVersion`;
      mail không liên quan (không có 502 ở đường này).
- [x] T015 [B] Edit hồ sơ: `model/dto/request/AdminUpdateProfileRequest.java` + `PATCH /users/{id}` —
      `fullName`/`phone` sửa trực tiếp; `email` đổi ⇒ unique check (409) + **reset verify**
      (`enabled=false`, token mới, mail mời lại) + bump `tokenVersion` (token cũ phải hết hiệu lực)
      + message nói rõ hệ quả; đo R-4.
- [x] T016 [B] CSV: `GET /users/export?search=&role=&status=` → `text/csv` qua `StreamingResponseBody`,
      **trần 10.000 dòng** (vượt ⇒ 400 "Kết quả vượt 10000 dòng, hãy thu hẹp bộ lọc"), cột
      `id,email,fullName,role,status,pendingVerification,lastLoginAt,createdAt,deletedAt`, **không** có
      `password`/token; audit `EXPORT_USERS`; escape ô chứa `,`, nháy kép, và xuống dòng trong ô
      (cell độc hại sinh từ `fullName` do người dùng tự nhập).
- [x] T017 [P] [B] Test Lô B: invite happy (mail đi, user `enabled=false`, audit `admin_id` thật) + trùng
      email 409 + **mail fail ⇒ 502 và `COUNT(users)` không tăng** + restore-link flow tới được (B2→B3→B4);
      resend trên user đã verify 400; revoke ⇒ login vẫn bị chặn "chưa xác thực"; CSV đúng số dòng theo bộ lọc
      (AC-4), CSV escape đúng; 403/401 cho toàn bộ endpoint mới (AC-6).

## Polish

- [x] T018 [P] Scan tiếng Việt toàn bộ message mới (script scan ký tự Hán + từ EN lọt câu) theo luật làm việc.
- [x] T019 [P] Cập nhật `contracts` của 001 (`admin-user-api.md`): enum `status` thêm `PENDING`,
      `UserSummaryResponse` thêm 2 field, thêm 6 endpoint mới — một nguồn hợp đồng cho FE.
- [x] T020 [P] Ghi `known-gaps.md`: "lastLoginAt ≠ last active", "không phân biệt lời mời admin / tự đăng ký",
      "mockup cần dòng audit hệ thống nhưng BE từ chối (C-3)".
- [x] T021 `mvn -B -o test` (công thức Docker ở quickstart 001) **xanh cả 001 + 002**, rồi `docker build` sạch.
- [x] T022 Push branch + chờ Actions `mvn -B clean test` — **chỉ khi anh Huy đồng ý**; merge `dev` sau khi CI xanh.

## Dependencies & order

T001 chặn T007/T016 (predicate chung). T002/T003/T004/T005 song song được. T011→T012 (chung controller/service
với T013/T014/T015 → **không parallel** các task đụng `AdminUserServiceImpl`). Lô B chỉ bắt đầu sau khi Lô A
đã đo AC-1 (stats khớp danh sách), vì mọi endpoint B đều đọc predicate chung đó.

## Definition of done cho từng task

Số đo thật (HTTP code + output SQL) ghi vào `quickstart-measure.md`, không mô tả chung chung; test integration
chạy trong container Testcontainers local; không có ô nào của ma trận input trả 500.

## Ghi chú khi nghiệm thu (2026-10-19)

Toàn bộ 22 task đã làm và đã đo; 3 sai lệch **có chủ đích** so với chữ trong task, số đo ở
`quickstart-measure.md` mục "Sai lệch so với plan":

- **T001**: không có hàm `buildUserFilter(...)`; bộ lọc chung là các **hằng JPQL** trong `JpaUserRepository`
  (`SEARCH_PREDICATE`/`ROLE_PREDICATE`/`STATUS_PREDICATE`/`LIVE_FILTER`/`LIVE_FILTERED`/`DELETED_FILTERED`)
  mà 5 query nối vào — một nguồn sự thật ngay ở tầng compile.
- **T005**: trả `List<Object[]>` chứ không phải `Map<String,LocalDateTime>` (Spring Data không projector tuple
  thành `Map`); service chuyển sang `Map` và đó cũng là cách tránh lỗi tuple một dòng bị nén.
- **T016**: `ResponseEntity<byte[]>` thay `StreamingResponseBody` (trần 10.000 dòng khiến lợi ích stream mất
  nghĩa; đo 881 KB / 0.61s cho 10.000 dòng).
- **T022**: anh Huy ra lệnh "làm hết tất cả trong plan xong rồi push lên" nên bước push được ủy quyền; điều kiện
  tiền đề vẫn giữ: `mvn -B -o test` xanh + `docker build` sạch trước push.

