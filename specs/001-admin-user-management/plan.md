# Implementation Plan: Hoàn thành chức năng quản lý tài khoản người dùng (admin)

**Branch**: `feature/mange-user` | **Date**: 2026-10-19 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-admin-user-management/spec.md`

## Summary

Chức năng admin quản lý tài khoản đã có 8 endpoint (`AdminUserController`, gate ROLE_ADMIN).
Kế hoạch này **siết hợp đồng các luồng đã có** (trạng thái mù, lỗi không nhất quán, nuốt lỗi mail,
audit ghi admin giả, phòng vệ một tầng) và **bịt vòng đời còn thiếu** (bật/tắt, liệt kê + khôi phục
tài khoản đã xóa, tra cứu audit). Không viết lại module, không thêm dependency.

## Technical Context

**Language/Version**: Java 17 (`pom.xml` `java.version=17`)

**Primary Dependencies**: Spring Boot 3.2.5 (web, data-jpa, security, validation), jjwt,
mysql-connector-j, Gmail API qua `EmailService` (không đổi), Lombok

**Storage**: MySQL 8, schema tiến hoá bằng `ddl-auto: update` (Flyway `enabled: false` — giữ nguyên
theo quyết định đã chốt; mọi cột/index mới khai báo qua annotation trên entity để `ddl-auto` tạo được)

**Testing**: JUnit 5 + SpringBootTest + MockMvc + Testcontainers (`AdminUserControllerIntegrationTest`);
**chỉ chạy thật trên CI**, local Windows không chạy được container (xem `spec/known-gaps.md` mục 1b)

**Target Platform**: Container Docker trên Render (staging), dev local qua Laragon MySQL 3306

**Project Type**: Single-module Spring Boot REST backend (`src/main/java/com/aiinterview/platform`)

**Performance Goals**: Danh sách admin trả kết quả trong < 1s với bảng `users` cỡ 10k dòng (đo bằng
curl thật); không nạp cả bảng vào bộ nhớ cho bất kỳ check nào

**Constraints**: Mail fail → 502 + rollback (hợp đồng 2026-10-19); chrome tiếng Anh, message nghiệp
vụ tiếng Việt; không đổi cơ chế JWT hiện có; không đổi hành vi register/login/forgot phía người dùng

**Scale/Scope**: 8 endpoint hiện có + 3 endpoint mới + 1 entity audit sửa; ~10 file Java thay đổi

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Gate | Kết quả | Ghi chú |
|---|---|---|---|
| I. Hợp đồng API trước, code sau | Hợp đồng endpoint phải nằm trong spec/contracts | PASS | `contracts/admin-user-api.md` tạo ở Phase 1, trước code |
| II. Số đo thật | Verify bằng curl/SQL/DR trên app chạy thật, không dựa container local | PASS | `quickstart.md` định nghĩa bộ đo; kết quả ghi vào `spec/features/manage-user.md` (superrepo) |
| III. Chặn lúc ghi | Mọi rule trả 400/422 ở request ghi, không để lưu rồi lộ khi GET | PASS | whitelist status/role, `@Validated` page/size, guard admin cuối bằng count |
| IV. Audit thật, nhất quán chiều trạng thái | Không admin giả; không thao tác vô nghĩa | PASS | FR-013: từ chối thao tác khi không resolve được admin, không ghi `0/system` |
| V. Hợp đồng lỗi nhất quán | "đã xóa" một mã lỗi cho mọi thao tác; mail fail = 502; không rò nội bộ | PASS | FR-001/004/005; xóa handler 500 in `ex.getMessage()` |
| VI. Phòng vệ nhiều tầng | URL rule + @PreAuthorize cùng chặn `/api/v1/admin/**` | PASS | thêm `requestMatchers("/api/v1/admin/**").hasRole("ADMIN")` |
| VII. Phạm vi tối thiểu | Không dashboard, không bulk, không hard delete, không migration | PASS | mục "Không làm" trong spec; không đụng `src/main/resources/db/migration` |
| VIII. Kiểm chứng trước workaround | Đo hiện trạng trước khi sửa; đối chiếu PersistenceTest/luồng cũ | PASS | bước P0 của tasks: đo lại 8 endpoint hiện trạng bằng curl |

**Gate**: không có vi phạm → không cần Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/001-admin-user-management/
├── plan.md              # file này
├── research.md          # Phase 0 — quyết định kỹ thuật + bằng chứng đọc code
├── data-model.md        # Phase 1 — User/AdminAuditLog thay đổi + chuyển trạng thái
├── quickstart.md        # Phase 1 — bộ đo curl/SQL bắt buộc trước khi merge
├── contracts/
│   └── admin-user-api.md# Phase 1 — hợp đồng 11 endpoint (method, status, message, envelope)
└── checklists/
    └── requirements.md  # đã đạt sau clarify 2026-10-19
```

Bản chính thức của tài liệu tính năng (theo luật repo) sẽ được sao/sync sang superrepo
`AI-Interview-Platform-deploy-staging/spec/features/manage-user.md` khi xong — không tự tạo ở đó trong
session này.

### Source Code (repository root)

```text
src/main/java/com/aiinterview/platform/
├── controller/AdminUserController.java          # + @Validated, + 3 endpoint mới, tham số lọc
├── security/jwt/JwtSecurityConfig.java          # + requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
├── security/jwt/JwtFilter.java                  # + catch InvalidAccountException (đường token chết)
├── common/exception/GlobalExceptionHandler.java # + handler TypeMismatch/ConstraintViolation/HttpMessageNotReadable/AccessDenied; 500 không rò message
├── model/entity/User.java                       # + lockedAt (nullable, ddl-auto tạo cột); KHÔNG thêm lockedByAdminId
├── model/entity/AdminAuditLog.java              # + success, reason + @Table(indexes=...)
├── model/enums/AuditAction.java                 # MỚI: tập giá trị đóng cho cột action
├── model/repository/JpaUserRepository.java      # + findDeleted..., + countByRoleAndDeletedAtIsNull
├── model/repository/AdminAuditLogRepository.java# + filter method (Specification/JPQL)
├── model/dto/request/...                        # UserStatusFilter enum, audit filter params
├── model/dto/response/UserSummaryResponse.java  # + deletedAt; status có DELETED thật
├── model/dto/response/UserDetailResponse.java   # + phone, lock info (admin/brute-force), tokenVersion
├── model/dto/response/AuditLogResponse.java     # + success, reason, targetEmail, targetFullName
└── service/impl/AdminUserServiceImpl.java       # siết guard, mail fail = 502+rollback, bump tokenVersion

src/test/java/com/aiinterview/platform/
└── controller/AdminUserControllerIntegrationTest.java  # thêm case ma trận trạng thái + guard + restore
```

**Structure Decision**: giữ nguyên layout single-module hiện có; không thêm package mới ngoài
`model/enums/AuditAction.java`.

## Complexity Tracking

> Không có vi phạm Constitution — bảng trống.

## Đính chính sau build (2026-10-19)

Bốn chỗ kế hoạch khác bản đã giao, đều theo hướng **bỏ việc không cần làm** (Constitution VII):

1. **`User` chỉ thêm 1 cột `locked_at`**, không có `locked_by_admin_id` — "ai khóa" đã nằm trong
   `admin_audit_log` (`admin_id`/`admin_email`), thêm cột nữa là trùng dữ liệu.
2. **Bỏ 409 xung đột email khi restore**: hàng xóa mềm vẫn giữ unique email của nó nên tình huống
   không dựng được (R7 đính chính). Kéo theo bỏ handler `DataIntegrityViolationException`.
3. **Bỏ JSON snapshot trong audit `DELETE_USER`**: xóa mềm không đổi `enabled`/`locked`/`role` nên
   restore chỉ việc xóa `deletedAt`; `detail` là chuỗi tiếng Việt mô tả.
4. **Cột audit tên `succeeded`** (không phải `success`) để không nhầm với `success` của envelope
   `ApiResponse`; `@Validated`/enum param ở controller là nơi chặn 400, không phải service.

Ngoài kế hoạch, ba lỗi thật chỉ lộ ra khi đo HTTP đã được sửa + có test giữ lại
(`GlobalExceptionHandlerTest`): 500 khi đăng nhập tài khoản do admin khóa (`Map.of` nhận null),
500 ở `/auth/me` khi không có authentication, và body 401/403 do filter ghi ra bị vỡ dấu tiếng Việt
(thiếu `;charset=UTF-8`). Chi tiết số đo: `quickstart.md` mục "Kết quả đo thật".
