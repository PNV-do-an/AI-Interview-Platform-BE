# Quickstart / Bộ đo verify: Quản lý tài khoản (admin)

Mọi mục phải có **số đo thật** (curl + SQL) — Constitution II. Cấm ghi "đã chạy".
Số đo ngày 2026-10-19 ở cuối file; kịch bản đo lưu trong `target/verify/run2.sh` (không commit).

## Prerequisites

```bash
# MySQL: container aiinterview-mysql (root/admin123) cổng 3307, DB riêng cho đo
docker exec aiinterview-mysql mysql -uroot -padmin123 \
  -e "CREATE DATABASE IF NOT EXISTS admin_mgnt_verify CHARACTER SET utf8mb4"

# Build jar (mvn trên host hỏng → Docker; volume m2-ai-be là cache dependency)
MSYS_NO_PATHCONV=1 docker run --rm -v "$(pwd -W)":/app -v m2-ai-be:/root/.m2 -w /app \
  maven:3.9-eclipse-temurin-17 mvn -B -o -DskipTests package
# Lần đầu (hoặc khi thêm dependency test) bỏ -o để tải surefire-junit-platform + test deps

# Chạy app: PORT 8090 (8080/8081 đã có stack khác chiếm), mail trỏ fake Gmail ở mục dưới
DB_HOST=127.0.0.1 DB_PORT=3307 DB_NAME=admin_mgnt_verify DB_USERNAME=root DB_PASSWORD=admin123 \
PORT=8090 GMAIL_CLIENT_ID=x GMAIL_CLIENT_SECRET=x GMAIL_REFRESH_TOKEN=x MAIL_FROM=a@b.local \
GMAIL_TOKEN_URI=http://127.0.0.1:4590/token GMAIL_API_BASE_URL=http://127.0.0.1:4590 \
java -jar target/ai-interview-platform-0.0.1-SNAPSHOT.jar
```

`DataInitializer` seed sẵn `admin@aiinterview.com` / `Admin@12345` (ROLE_ADMIN, enabled) khi DB trống,
nên không phải tạo admin thủ công.

### Kênh mail giả (đo cả chiều success lẫn 502 mà không cần credential Gmail thật)

`EmailServiceImpl` chỉ gọi `/token` và `/gmail/v1/users/me/messages/send`, cả hai URL đều cấu hình qua
env → chạy một HTTP server giả ở cổng 4590:
- `POST /token` → `{access_token:...}`;
- `POST .../messages/send` → ghi body `raw` (base64url → RFC822) ra log, trả 200;
- có flag `/FAIL` → trả 503 để đo hợp đồng "mail fail ⇒ 502 + rollback".

Token xác thực/reset lấy từ chính nội dung mail ghi ra → chạy đủ luồng verify, không đoán mò.

## Test tự động

```bash
MSYS_NO_PATHCONV=1 docker run --rm --add-host=host.docker.internal:host-gateway \
  -v "$(pwd -W)":/app -v m2-ai-be:/root/.m2 -v /var/run/docker.sock:/var/run/docker.sock \
  -e DOCKER_HOST=unix:///var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -e TESTCONTAINERS_RYUK_DISABLED=true \
  -w /app maven:3.9-eclipse-temurin-17 mvn -B -o test
```

Thiếu `TESTCONTAINERS_HOST_OVERRIDE` thì MySQL container khởi động được nhưng JDBC báo
`Connection refused` ở `172.17.0.1:<port>` (testcontainers suy ra gateway của VM, còn port publish nằm
trên Windows) → retry ~6 phút rồi fail. Có biến này thì **Testcontainers chạy xanh ngay trên máy local**,
không phải đợi CI; CI vẫn là cổng chốt trước khi merge.

## Ma trận trạng thái × thao tác (SC-001)

Chuẩn bị 4 tài khoản: `ACTIVE`, `INACTIVE` (enabled=false), `LOCKED`, `DELETED`.
Gọi từng thao tác (lock, unlock, status, delete, reset-password, role) trên từng trạng thái, ghi
`HTTP code` + `message`. Kỳ vọng lấy từ `contracts/admin-user-api.md`.
**Yêu cầu**: không tổ hợp nào trả 500; mọi ô "đã xóa mềm" trả 404 giống hệt nhau.

## Các phép đo bắt buộc

| # | Tình huống | Kỳ vọng |
|---|---|---|
| M-1 | Lọc `status=FOO` | **400**, message liệt kê giá trị được phép (trước sửa: trả trang rỗng 200) |
| M-2 | `role=ROLE_FOO` | **400** (trước sửa: nghi 500) |
| M-3 | `page=-1`, `size=0`, `size=100000` | **400** cả ba, message nêu tên tham số (trước: 500 / kéo cả bảng) |
| M-4 | **Mail fail → rollback** | **502** + SQL cho thấy `enabled/locked/token_version` **không đổi** và **không có dòng audit mới**; token reset cũ không bị đè |
| M-5 | Audit có admin thật | `adminId` = id admin đăng nhập, **không** `0`/`system` |
| M-6 | Audit nỗ lực thất bại | dòng `succeeded=false` + `reason` đúng thông báo nghiệp vụ |
| M-7 | Guard admin cuối | `DELETE` và `PATCH .../role` trên admin **400** (self-guard); `COUNT` admin sống vẫn ≥ 1 |
| M-8 | Đăng nhập khi bị admin khóa | **423** kèm message "bị khóa bởi quản trị viên" (trước sửa: **500** do `Map.of` nhận null) |
| M-9 | Vòng đời xóa/restore | `DELETE` → biến mất khỏi danh sách live → có ở `/users/deleted` (`status=DELETED`) → doc được bằng `GET /users/{id}` → mọi thao tác tac động **404 cùng một message** → `restore` → login lại **200**, `enabled`/`locked` nguyên như trước xóa |
| M-10 | Token của email đã bị xóa vật lý | **401** (trước sửa: 500 do NPE trong `AuthController#getMe`) |
| M-11 | Thu hồi phiên sau lock/disable | access token cũ → `/auth/me` **401**; refresh token có `tv` cũ → `/auth/refresh` **401** |
| M-12 | Search escape | `search=%` → 0 dòng; `search=verify` → đúng số user; 101 ký tự → **400** |
| M-13 | Staff bị chặn | token `ROLE_STAFF` gọi 11 endpoint → tất cả **403**; không đăng nhập → **401** |
| M-14 | 500 không rò nội bộ | message `"Lỗi hệ thống. Vui lòng thử lại sau."`, không chứa text exception |
| M-15 | Hồi quy user-side | register 201, verify 200, login 200, forgot-password 200 |
| M-16 | TTL reset link | `reset_token_expiry` chênh **30 phút** và nội dung mail nói "30 phút" |
| M-17 | Body tiếng Việt ở tầng filter | 401/403 do `JwtSecurityConfig` ghi ra phải dựng đúng dấu (không phải `Yu c?u ??ng nh?p`) |

## Kết quả đo thật (2026-10-19, app + MySQL 8.0.46 local, kênh mail giả)

`mvn -B -o test` toàn bộ: **Tests run: 79, Failures: 0, Errors: 0** — trong đó
`AdminUserControllerIntegrationTest` 41, `PersistenceTest` 12, `GlobalExceptionHandlerTest` 3.

| # | Số đo |
|---|---|
| M-1 | `400 {"message":"Tham số 'status' không hợp lệ. Giá trị được phép: [ACTIVE, INACTIVE, LOCKED, DELETED]"}` |
| M-2 | `400 {"message":"Tham số 'role' không hợp lệ. Giá trị được phép: [ROLE_USER, ROLE_STAFF, ROLE_ADMIN, ROLE_INTERVIEWER]"}` |
| M-3 | `400 detail {"page":"page không được âm"}` / `{"size":"size phải ≥ 1"}` / `{"size":"size tối đa là 100"}` |
| M-4 | bật `/FAIL`: `PATCH .../lock` → `502`; DB trước = sau `en=1 lk=0 tv=0 audit=5`; `reset-password` → `502`, `reset_token` trước/sau đều `NULL`; tắt `/FAIL` → `200` |
| M-5 | 15 dòng audit, `admin_id=1 / admin@aiinterview.com` cả 15; `COUNT(admin_id IS NULL OR admin_email='system')` = **0** |
| M-6 | `id=11 LOCK_USER ok=0 reason=Tài khoản đã bị khóa`; `id=4 DISABLE_USER ok=0 reason=Tài khoản đã ở trạng thái tắt`; tổng `succeeded=0` = 5/15 |
| M-7 | `DELETE .../users/1` → `400 "Không thể xóa tài khoản của chính mình"`; `role` → `400`; `lock` → `400`; `COUNT` admin sống = 1 |
| M-8 | login user bị khóa → `423 "Tài khoản của bạn đã bị khóa bởi quản trị viên. Vui lòng liên hệ hỗ trợ."` (trước fix: `500` + `NullPointerException` tại `GlobalExceptionHandler:48`) |
| M-9 | `DELETE`→`200`; `GET /users?search=long`→`totalElements=0`; `GET /users/deleted`→`status=DELETED`; `GET /users/3`→`200`; lock/status/reset/role trên nó → `404 "Không tìm thấy tài khoản (đã bị xóa)"` cả 4; login → `401 "Tài khoản không tồn tại hoặc đã bị xóa"`; `restore`→`200`, `en=1 lk=0 del=-`; login lại → `200` |
| M-10 | `GET /auth/me` bằng token của user vừa `DELETE FROM users` → `401 "Yêu cầu đăng nhập"` (trước fix: `500` NPE) |
| M-11 | token phát hành trước khi lock → `401`; `POST /auth/refresh` với `tv=0` khi user đã `tv=1` → `401 "Phiên đăng nhập không còn hiệu lực. Vui lòng đăng nhập lại."` |
| M-12 | `search=%` → `totalElements=0`; `search=verify` → 4 dòng; search 101 ký tự → `400 "Từ khóa tìm kiếm tối đa 100 ký tự"` |
| M-13 | 11/11 endpoint: STAFF → `403`, ANON → `401` |
| M-14 | `handleOther` trả `500 "Lỗi hệ thống. Vui lòng thử lại sau."`; unit test chứng minh không chứa `"JDBC"`/`"secret"` |
| M-15 | register `201` ×3, verify `200` ×3, login `200`, forgot-password `200` |
| M-16 | `exp=2026-10-19 08:56:34` khi mail phát lúc 15:26 local (=08:26 UTC) → **30 phút**; mail ghi "Link có hiệu lực trong 30 phút" |
| M-17 | `401 {"message":"Yêu cầu đăng nhập"}`, `403 {"message":"Bạn không có quyền truy cập chức năng quản trị"}` — dựng dấu đúng sau khi sửa `writeError` thêm `;charset=UTF-8` (trước đó ra `Yu c?u ??ng nh?p`) |
| M-18 | **đo lại 15:45 sau chỉnh "xóa lần 2"**: `mvn -B -o test` vẫn **79/79**; `DELETE .../users/2` #1 → `200`, #2 → `404 "Không tìm thấy tài khoản (đã bị xóa)"` — **cùng một message** với lock/status/role/reset-password trên chính user đó (trước chỉnh: #2 trả `400 "Tài khoản đã bị xóa"`, lệch hợp đồng FR-001). `GET /users/2` → `200` (`enabled:false` dù DB `en=1`, vì `User.isEnabled()` là trạng thái hiệu dụng). Audit `15 → 22` dòng: 1 `DELETE_USER` ok + 1 `VIEW_USER` ok + 5 dòng `succeeded=0` mang `reason` = đúng message 404. `restore` → `200` (`en=1 lk=1 del=-`), `DELETE` lại → `200` |

**Chưa đo được bằng HTTP**: guard "admin cuối cùng" (`rejectIfLastAdmin`) không tới được khi actor ≠ target,
vì mọi request thành công đều có admin actor còn sống và được tính vào `countByRoleAndDeletedAtIsNull`
→ count luôn ≥ 2. Đây là guard chống race/nghiệp vụ data-repair, giữ theo luật "giữ guard phòng vệ";
đường tự thao tác thì 400 bởi self-guard (M-7).
