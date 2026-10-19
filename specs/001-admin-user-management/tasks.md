# Tasks: Hoàn thành chức năng quản lý tài khoản người dùng (admin)

**Input**: Design documents from `specs/001-admin-user-management/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/admin-user-api.md, quickstart.md

**Tests**: CÓ — spec/plan yêu cầu test (Constitution II + mục Test tự động trong quickstart).
**Đính chính khi build**: integration test chạy được **cả ở local** (Testcontainers + docker socket mount),
không chỉ trên CI — xem quickstart mục "Test tự động" và memory `project-run-maven-tests-docker`.
Số đo thật M-1..M-17 nằm trong `quickstart.md` (bảng "Kết quả đo thật 2026-10-19"), **không** tách file
`baseline-measurements.md` như kế hoạch ban đầu.

**Organization**: nhóm theo user story; US1 = MVP.

## Path Conventions

`src/main/java/com/aiinterview/platform/` (viết tắt `…/` bên dưới), test ở
`src/test/java/com/aiinterview/platform/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: không khởi tạo mới — chỉ đảm bảo nền đo được

- [x] T001 Chạy build baseline qua Docker để xác nhận repo sạch compile được:
      `MSYS_NO_PATHCONV=1 docker build -t ai-be-baseline .`
- [x] T002 [P] Đo **hiện trạng** 8 endpoint theo quickstart M-1..M-3, M-7 (phần "trước sửa"), ghi số vào
      `specs/001-admin-user-management/baseline-measurements.md` (Constitution VIII: đo trước khi sửa)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: hạ tầng lỗi + dữ liệu mà mọi story cần

**⚠️ CRITICAL**: story work không bắt đầu trước khi phase này xong

- [x] T003 Tạo enum giá trị đóng `…/model/enums/AuditAction.java` (9 giá trị theo R10)
- [x] T004 [P] Sửa `…/model/entity/AdminAuditLog.java`: thêm cột `success` (boolean not null default true),
      `reason` (VARCHAR(500) nullable), `@Table(indexes=…)` 3 index theo data-model mục 2
      — *làm thật*: cột/JSON đặt tên `succeeded` (tránh nhầm với `success` của envelope `ApiResponse`),
      `columnDefinition = "TINYINT(1) NOT NULL DEFAULT 1"` để `ddl-auto` thêm cột vào bảng đã có dữ liệu.
      Index khai báo qua `columnList` thường (`@Index` JPA không mô tả DESC).
- [x] T005 [P] Sửa `…/model/entity/User.java`: thêm `lockedAt` (LocalDateTime nullable) cho audit/hiển thị khóa
- [x] T006 [P] Sửa `…/model/repository/JpaUserRepository.java`:
      `countByRoleAndDeletedAtIsNull(Role)` (R2), `findDeletedWithSearch` (R5),
      escape + `ESCAPE '\'` cho `findAllWithFilters` (R6), tham số `status` là enum
- [x] T007 [P] Sửa `…/model/repository/AdminAuditLogRepository.java`: query lọc theo
      adminId/targetUserId/action/from/to + `findFirstByTargetUserIdAndActionOrderByCreatedAtDesc` (R7)
- [x] T008 `…/model/dto/request/UserStatusFilter.java` (hoặc enum nội bộ) + `…/model/dto/request/UserStatusRequest.java`
      (`enabled` `@NotNull`, message tiếng Việt)
- [x] T009 Sửa `…/common/exception/GlobalExceptionHandler.java`: handler mới cho
      `MethodArgumentTypeMismatchException` (400), `ConstraintViolationException` (400);
      500 bỏ nối `ex.getMessage()` (R11). **Bỏ** `DataIntegrityViolationException`→409 (không còn đường
      tới, xem R11/R7 đính chính). **Thêm ngoài kế hoạch** (do đo M-16 phát hiện): `InvalidAccountLockedException`
      không còn nổ 500 khi `lockUntil=null` (dùng `HashMap`, trả 423), `AccessDeniedException` message tiếng Việt,
      `HttpMessageNotReadableException` (body sai JSON) → 400
- [x] T010 [P] Sửa `…/service/UserService`/`UserServiceImpl` không đổi, nhưng `…/security/jwt/JwtFilter.java:59`
      catch thêm `InvalidAccountException` (R12) — **chỉ sau khi đo M-9 xác nhận hiện trạng**
- [x] T011 [P] Sửa `…/security/jwt/JwtSecurityConfig.java`: `.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")`
      + `exceptionHandling` trả envelope `ErrorModel` cho 401/403 (R13, US4)
- [x] T012 [P] Sửa `…/service/impl/EmailServiceImpl.java:158`: bỏ hard-code "1 giờ", dùng 30 phút (FR-008)

**Checkpoint**: nền tảng lỗi/audit/phân trang sẵn sàng — vào story

---

## Phase 3: User Story 1 — Thao tác luôn đúng trạng thái (P1) 🎯 MVP

**Goal**: siết hợp đồng 8 endpoint: nhất quán lỗi "đã xóa", chặn tham số xấu ở cửa vào, mail fail = 502+rollback,
guard admin-cuối bằng count, escape search, bump tokenVersion.

**Independent Test**: ma trận 6 thao tác × 4 trạng thái (quickstart) + M-1..M-4, M-7, M-10, M-11, M-13, M-15.

### Tests for US1

- [x] T013 [P] [US1] Thêm case vào `…/controller/AdminUserControllerIntegrationTest.java`:
      status/role sai giá trị → 400; page/size biên → 400; unlock/delete trên user đã xóa → 404 thống nhất;
      guard admin cuối ở **đường xóa**; mail fail (`@MockBean EmailService` ném `MailDeliveryException`) → 502
      và DB không đổi; search chứa `%`

### Implementation for US1

- [x] T014 [US1] `…/controller/AdminUserController.java`: thêm `@Validated`, `@Min/@Max` cho page/size
      (cả `/users` và `/audit-logs`), `status` đổi sang enum, chặn `status=DELETED` ở `/users` (R3, R4)
- [x] T015 [US1] `…/service/impl/AdminUserServiceImpl.java`: hợp nhất rule "đã xóa mềm" → 404 cho mọi thao tác
      (FR-001), escape + chặn độ dài search (R6), guard count cho `changeRole` **và** `deleteUser` (R2)
- [x] T016 [US1] `…/service/impl/AdminUserServiceImpl.java`: bỏ 4 khối `try/catch` nuốt lỗi mail (dòng 74-78,
      93-97, 115-119, 135-139) để `MailDeliveryException` → 502 + rollback (R1); message controller giữ đúng
      thực tế ("… đã được gửi" chỉ khi mail thật đi)
- [x] T017 [US1] `…/service/impl/AdminUserServiceImpl.java`: bump `tokenVersion` khi lock/disable/delete/role
      (R9); đặt/xóa `lockedAt` khi lock/unlock; TTL reset 30 phút (FR-008)
- [x] T018 [US1] Chạy lại ma trận trạng thái trên app Docker, ghi số đo thật vào
      `specs/001-admin-user-management/baseline-measurements.md`

**Checkpoint**: US1 chạy độc lập được — đây là MVP có thể merge

---

## Phase 4: User Story 2 — Vòng đời tài khoản đầy đủ (P2)

**Goal**: bật/tắt `enabled`, liệt kê + khôi phục tài khoản đã xóa, detail đủ thông tin ra quyết định.

**Independent Test**: M-8 (xóa → deleted list → restore → login), toggle status + login thất bại/bật lại,
case "email đã bị chiếm khi restore" → 409.

### Tests for US2

- [x] T019 [P] [US2] Thêm case `AdminUserControllerIntegrationTest`: `PATCH /status` (bật/tắt, chặn self,
      chặn khi user còn `verificationToken`), `GET /users/deleted`, `POST /restore` (happy + chưa xóa → 400;
      case "email xung đột → 409" **bỏ** vì không dựng được tình huống), detail trả `phone`/`blockedUntil`.
      Tổng class: 42 test (thêm case xóa lần 2 → 404 thống nhất FR-001)

### Implementation for US2

- [x] T020 [US2] `…/service/AdminUserService.java` + `…/service/impl/AdminUserServiceImpl.java`:
      `setUserEnabled(id, enabled)` và `restoreUser(id)` (restore **chỉ** xóa `deletedAt` — R7 đính chính,
      không có snapshot/409), audit
      `ENABLE_USER`/`DISABLE_USER`/`RESTORE_USER`
- [x] T021 [US2] `…/controller/AdminUserController.java`: `PATCH /users/{id}/status`,
      `GET /users/deleted`, `POST /users/{id}/restore`
- [x] T022 [P] [US2] `…/model/dto/response/UserSummaryResponse.java` (+`deletedAt`) và
      `UserDetailResponse.java` (+`phone`, `lockedAt`, `blockedUntil`, `failedLoginAttempts`) theo data-model mục 4.
      Đính chính: giữ tên `locked` cho "admin khóa" (không đổi thành `lockedByAdmin`) và **không** trả `tokenVersion`

**Checkpoint**: US1 + US2 độc lập hoạt động

---

## Phase 5: User Story 3 — Audit log tra cứu được (P2)

**Goal**: không còn admin giả, có `success`/`reason`, hành động đóng, lọc được, trả email/tên nạn nhân.

**Independent Test**: M-5, M-6 + lọc theo từng tiêu chí đối chiếu SQL.

### Tests for US3

- [x] T023 [P] [US3] Case test: `adminId` trong audit = id admin đăng nhập (dùng `@WithMockUser` email thật
      có trong DB, không phải `@MockUser` chung); nỗ lực bị từ chối tạo dòng `success=false`;
      filter `action`/`targetUserId`/`from`/`to`; response có `targetEmail`

### Implementation for US3

- [x] T024 [US3] `…/service/impl/AdminUserServiceImpl.java`: `resolveAdmin()` trả null ⇒ **từ chối thao tác**
      (403), không ghi `0`/`system`; mọi `saveAuditLog` dùng `AuditAction`; ghi audit `succeeded=false` +
      `reason` khi rule từ chối — qua `AdminAuditWriter` `REQUIRES_NEW` để dòng vẫn sống sau rollback của caller
      (`…/service/impl/AdminAuditWriter.java`, **file mới ngoài kế hoạch**)
- [x] T025 [US3] `…/service/impl/AdminUserServiceImpl.java` + `…/model/dto/response/AuditLogResponse.java`:
      `getAuditLogs` nhận bộ lọc (adminId/targetUserId/action/from/to), LEFT JOIN users để trả
      `targetEmail`/`targetFullName` (kể cả đã xóa)
- [x] T026 [US3] `…/controller/AdminUserController.java`: query params lọc cho `GET /audit-logs`
      (enum `AuditAction` cho `action` → 400 nếu sai, R3)

**Checkpoint**: audit tra cứu được

---

## Phase 6: User Story 4 — Không phá luồng khác (P3)

**Goal**: nhân viên vận hành (Staff) bị chặn ở cả 2 tầng; token của tài khoản bị khóa/tắt/xóa chết;
luồng user-side không đổi hành vi.

**Independent Test**: M-12 (Staff 403 mọi endpoint), M-10 (refresh 401 sau lock), M-14 (hồi quy
register/login/forgot), M-9 (token email đã xóa → 401).

### Implementation for US4

- [x] T027 [P] [US4] Verify lớp URL rule (T011) + `@PreAuthorize` cùng chặn: đo bằng token ROLE_STAFF trên cả
      11 endpoint, ghi số đo
- [x] T028 [P] [US4] Đo M-10 + M-9, M-14 trên app Docker; nếu access token cũ vẫn dùng được ở endpoint
      `permitAll`, ghi vào `known-gaps.md` (không tự đổi TTL — cần anh Huy chốt)

---

## Phase 7: Polish & Cross-Cutting Concerns

- [x] T029 [P] Rà lại toàn bộ message mới thêm bằng script scan tiếng Việt
      (`grep -rP '[\x{00C0}-\x{00FF}\x{0100}-\x{017F}\x{1E00}-\x{1EFF}]'` + soi ký tự Hán/lẫn từ EN)
- [x] T030 [P] Cập nhật `spec/known-gaps.md` (repo này + bản superrepo): mục audit log `targetUserId` thô,
      bộ lọc `DELETED`, Q-12 Staff, hợp đồng 502 cho đường admin
- [ ] T031 [P] Copy tài liệu tính năng sang superrepo `AI-Interview-Platform-deploy-staging/spec/features/manage-user.md`
      theo luật repo (chỉ khi anh Huy đồng ý commit doc) — **CHƯA LÀM, chờ anh duyệt**
- [x] T032 Chạy trọn bộ quickstart M-1..M-15, ghi kết quả thật (đo tới M-17, xem quickstart)
- [ ] T033 `docker build` sạch + push branch, chờ Actions `mvn -B clean test` xanh rồi mới merge dev
      — **CHƯA PUSH** (luật: chỉ push khi anh Huy đồng ý). Đính chính: Testcontainers **xanh được ở local**
      (41/41 + 79/79 qua docker socket mount), CI vẫn cần chạy trước khi merge vì đây là nguồn chân lý chung.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1** (T001-T002): bắt đầu ngay; T002 **chặn** mọi quyết định sửa (Constitution VIII)
- **Phase 2** (T003-T012): phụ thuộc T001; **chặn toàn bộ story**
- **Phase 3 (US1)**: phụ thuộc Phase 2 — MVP
- **Phase 4 (US2)**: phụ thuộc Phase 2 + T015/T017 (chung file `AdminUserServiceImpl` với US1 → làm **tuần tự** sau US1)
- **Phase 5 (US3)**: phụ thuộc Phase 2 (T003/T004/T007) + T016 (audit path dùng chung)
- **Phase 6 (US4)**: phụ thuộc T011 (đã ở Phase 2) + các story có tokenVersion (T017)
- **Phase 7**: sau khi story được chọn xong

### Within Each User Story

Test → service → controller → DTO/response → đo thật. Kiểm soát xung đột file:
`AdminUserServiceImpl.java` được sửa ở T015, T016, T017, T020, T024, T025 → **không parallel** các task đó.

### Parallel Opportunities

- T003..T009, T012 khác file → chạy song song trong Phase 2
- Trong mỗi story: các task `[P]` ở test/DTO/response chạy song song được
- T029-T031 song song với T032

---

## Implementation Strategy

### MVP First

1. Phase 1 + Phase 2
2. Phase 3 (US1) → **STOP & VALIDATE** bằng ma trận trạng thái + M-1..M-4, M-7
3. Merge/deploy staging nếu anh Huy muốn tách lô

### Incremental Delivery

US1 → đo → US2 → đo → US3 → đo → US4 (polish/security) → đo trọn bộ → CI xanh → merge dev.

---

## Notes

- Không tạo migration Flyway mới (Flyway đang tắt; cột/index qua annotation để `ddl-auto` tự thêm).
  Nếu `ddl-auto` không tạo index như mong muốn → **báo cáo, không tự bật Flyway**.
- Không đụng `ProfileController`/`ViewController` (đồ chết, ngoài phạm vi).
- Commit sau mỗi phase; message tiếng Việt không dấu theo style repo (`feat(admin): ...`).
- Không ghi ước lượng thời gian vào báo cáo gửi leader.
