# Bộ đo verify 002 — Admin user directory

Mọi số dưới đây là **kết quả chạy thật** (curl + SQL) ngày **2026-10-19**, không phải mô tả dự kiến.
Constitution II: cấm ghi "đã chạy" mà không có số.

## Môi trường đo

| Thành phần | Giá trị |
|---|---|
| Jar | `target/ai-interview-platform-0.0.1-SNAPSHOT.jar` build từ code 002 (`mvn -B -o -DskipTests package`, BUILD SUCCESS) |
| App | `http://127.0.0.1:8090` (8080/8081 do stack khác chiếm), context path `/api/v1` |
| DB | container `aiinterview-mysql` (MySQL 8, `root/admin123`), cổng host **3307**, schema `admin_mgnt_verify` (DB riêng cho đo) |
| Kênh mail | fake Gmail API `http://127.0.0.1:4590` (`GMAIL_TOKEN_URI`/`GMAIL_API_BASE_URL`), có cờ `/FAIL` để đo 502 |
| Admin đo | `admin@aiinterview.com` do `DataInitializer` seed (`Admin@12345`), `id=5` |
| Bộ test tự động | `AdminUserControllerIntegrationTest` = **63 test**; toàn repo `mvn -B -o test` = **Tests run: 101, Failures: 0, Errors: 0** (18:5x, log `mvn-002-full.log`). `docker build .` = **exit 0** |

Dataset SQL (nén thành `target/verify/seed002_data.sql`, không commit): admin seed + 8 tài khoản sống
phủ 4 role và 4 trạng thái + 2 tài khoản xóa mềm + 4 dòng `login_attempts` (1 fail, 1 thành công
hôm nay, 1 thành công **-9 ngày**, 1 thành công **-45 ngày**). Tổng khi đo AC-1: **9 sống + 2 đã xóa**.

## AC-1 — số trên huy hiệu = `totalElements` của danh sách cùng bộ lọc

`GET /users/stats?…` so với `GET /users?…&status=<tab>` và `GET /users/deleted?…` (4 bộ lọc):

| Bộ lọc (`search`/`role`) | stats | all | ACTIVE | PENDING | LOCKED | INACTIVE | DELETED |
|---|---|---|---|---|---|---|---|
| (không lọc) | total 9, active 5, pending 2, locked 1, disabled 3, deleted 2 | **9** | **5** | **2** | **1** | **3** | **2** |
| `search=active` | 3 / 2 / 0 / 0 / 1 / 0 | **3** | **2** | **0** | **0** | **1** | **0** |
| `role=ROLE_STAFF` | 2 / 1 / 1 / 0 / 1 / 0 | **2** | **1** | **1** | **0** | **1** | **0** |
| `role=ROLE_ADMIN` | 1 / 1 / 0 / 0 / 0 / 1 | **1** | **1** | **0** | **0** | **0** | **1** |

6/6 huy hiệu khớp danh sách ở cả 4 bộ lọc. Đo lại ở moc lớn (10.014 sống + 2 đã xóa, chèn hàng loạt bằng SQL):

```
stats: {"total":10014,"active":10007,"pendingVerification":4,"locked":1,"disabled":6,"deleted":2,"activeLast30Days":3}
list : all=10014  ACTIVE=10007  PENDING=4  LOCKED=1  INACTIVE=6  DELETED=2      -> 6/6 khớp
stats (search=bulk000): total=99 active=99   list: all=99 ACTIVE=99              -> khớp
thoi gian GET /users/stats?search= : 0.035s
```

`active + pending + locked + disabled` **lớn hơn** `total` là hành vi đúng (một tài khoản vừa `PENDING`
vừa `INACTIVE`: `u5.pending` có token và `enabled=0`).

## AC-1 phụ — `lastLoginAt` và KPI 30 ngày (Clarify C-1)

`GET /users?page=0&size=20` đối chiếu `SELECT email, MAX(created_at) FROM login_attempts WHERE success=1 GROUP BY email`
(hai cột thời gian **lệch 7 giờ hiển thị**: cột DB là giờ UTC của container MySQL, cột API là giờ local +07
do JDBC dịch — cùng một thời điểm, không phải hai giá trị khác nhau; ghi rõ để người sau không tưởng là bug):

| email | `lastLoginAt` (API, +07) | `MAX(thành công)` (DB, UTC) | pending | status |
|---|---|---|---|---|
| admin@aiinterview.com | hôm nay 19:00:54 | hôm nay 12:00:54 | false | ACTIVE |
| u1.active | hôm nay 18:35:25 | hôm nay 11:35:25 | false | ACTIVE |
| u2.active | 10/10 18:35:25 (**-9 ngày**) | 10/10 11:35:25 | false | ACTIVE |
| u3.inactive | 04/09 18:35:25 (**-45 ngày**) | 2026-09-04 11:35:25 | false | INACTIVE |
| u6.staff | hôm nay 18:43:00 | hôm nay 11:43:00 | false | ACTIVE |
| u4/u5/u7/u8/invitee1 | `null` (không có dòng success nào) | (không có trong GROUP BY) | u5,u7 = true | LOCKED/INACTIVE |

KPI: `activeLast30Days` API = **4**; SQL `COUNT(DISTINCT u.id) JOIN login_attempts success AND created_at >= NOW()-30 DAY` = **4**.
u3 bị loại vì lần đăng nhập gần nhất đã 45 ngày — **đúng** ý C-1: đây là "đăng nhập gần đây", không phải "đang hoạt động".

## AC-2 — Add User gửi lời mời; mail hỏng thì 502 + không còn dấu vết

```
POST /users {"fullName":"Duoc Thi Moi","email":"invitee1@x.com","role":"ROLE_USER","phone":"0911122233"}
-> 200 {"success":true,"message":"Đã gửi email mời tài khoản","data":{...,"enabled":false,
        "pendingVerification":true,"status":"INACTIVE"}}
SQL: id=16 enabled=0 locked=0 has_vtoken=1 token_version=0 pw_prefix=$2a$10$ deleted_at=NULL
audit id=32: admin_id=5 admin_email=admin@aiinterview.com target_user_id=16 action=INVITE_USER succeeded=1
             detail="Mời tài khoản: invitee1@x.com"
fake Gmail log: To: invitee1@x.com  Subject: Xác thực tài khoản AI Interview Platform
                "Xin chào Duoc Thi Moi, ... http://localhost:3000/verify?token=355bd0f8-e280-45f1-8911-b8c6b187a031
                 Link có hiệu lực trong 24 giờ."
```

C-4 (không nhận mật khẩu từ admin) đã đo: `password` trong DB là bcrypt của một UUID ngẫu nhiên.
Đo qua HTTP: `POST /users {"fullName":"Thu Nghiem","email":"probe.pw@x.com","role":"ROLE_USER",
"password":"Secret@123"}` → **200** (field thừa bị bỏ qua, không 400), user `id=16405` tạo ra có
`pw_prefix=$2a$10$` và **không phải** hash của `Secret@123` — bằng chứng trực tiếp là assertion
`assertFalse(passwordEncoder.matches("Secret@123", invited.getPassword()))` trong
`testInviteUser_HappyPath` (một trong 63 test xanh). Người được mời phải tự đặt mật khẩu qua luồng
"quên mật khẩu" hiện có — ghi ở known-gaps.

Mail hỏng (bật `/FAIL` trên fake Gmail, `users` = 12 dòng và `admin_audit_log` = 4 dòng trước request):

```
POST /users {"fullName":"Le Thi Fail","email":"invitee2@x.com","role":"ROLE_USER"}
-> 502 {"status":502,"message":"Không gửi được email. Vui lòng thử lại hoặc liên hệ hỗ trợ."}
SQL sau do: users = 12 (khong doi), admin_audit_log = 4 (khong doi),
            SELECT COUNT(*) WHERE email='invitee2@x.com' = 0   -> rollback sạch, không user mồ côi
```

Email trùng, chặn **lúc ghi**:

```
invitee1 lan 2 -> 409 "Email đã tồn tại trong hệ thống"
u9.deleted (xóa mềm, id 14) -> 409 "Email này đang thuộc về tài khoản đã bị xóa (id 14). Khôi phục
                                 tài khoản đó bằng POST /api/v1/admin/users/14/restore thay vì tạo mới."
```

## AC-3 — Thu hồi link mời

```
DELETE /users/16/invitation -> 200 "Đã thu hồi link mời"  pendingVerification=false
SQL: truoc vtoken=355bd0f8-... tv=0 enabled=0   ->  sau vtoken=NULL tv=0 enabled=0 (khong doi tv/enabled)
Danh sach: GET /users?search=invitee1 totalElements=1  (van con trong bang)
Tab PENDING: GET /users?search=invitee1&status=PENDING totalElements=0
Dang nhap invitee1/Admin@12345 -> 401 "Tài khoản chưa được kích hoạt. Vui lòng kiểm tra email."
audit id: action=REVOKE_INVITATION succeeded=1
Thu hoi khi khong co loi mo -> 400 "Tài khoản không có lời mời đang chờ xác thực"
                              + audit id=31 succeeded=0 reason="Tài khoản không có lời mời đang chờ xác thực"
```

Resend (user 10 `u5.pending` đang có token `tok-u5-pending`):

```
Mail OK  -> 200 "Đã gửi lại email mời"; SQL: vtoken=17bf7d3e-336e-4e0b-a53c-e1bf993dc157 (token MOI),
            expiry=2026-10-20 11:55:57 (+24h), audit RESEND_INVITATION succeeded=1
            "Gửi lại email mời: u5.pending@x.com"
Mail FAIL-> 502; SQL: vtoken=vantok-u5-pending, expiry khong doi, khong co dong audit RESEND moi
            -> hop dong "mail fail thi token quay lai gia tri cu" dat
User da verify email (id 5, id 6) -> 400 "Tài khoản đã xác thực email"
```

## AC-4 — CSV export

Số dòng dữ liệu của file = `totalElements` của danh sách cùng bộ lọc (đo 4 bộ lọc trên dataset 10 sống):

| Bộ lọc | CSV (tổng dòng) | dòng dữ liệu | `list.totalElements` |
|---|---|---|---|
| `search=` | 11 | **10** | **10** |
| `search=active` | 3 | **2** | **2** |
| `role=ROLE_STAFF` | 3 | **2** | **2** |
| `status=PENDING` | 5 | **4** | **4** |

```
Header: id,email,fullName,role,status,pendingVerification,lastLoginAt,createdAt,deletedAt
Content-Type: text/csv;charset=UTF-8
Content-Disposition: attachment; filename="users-export.csv"
grep -ci "password|$2a$10|verification_token|reset_token" tren file = 0   (khong lo bao giot bi mat)
```

Trần 10.000 dòng (chèn 10.000 user bằng `WITH RECURSIVE`, tong 10.010 sống):

```
GET /users/export?search=        (10.010)  -> 400 "Kết quả vượt 10000 dòng, hãy thu hẹp bộ lọc"  0.60s
GET /users/export?search=bulk    (10.000)  -> 200, 881 KB, 10.001 dong file (1 header + 10.000 data)  0.61s
GET /users/export?search=bulk0   (9.999)   -> 200, 10.000 dong file
GET /users/export?role=ROLE_INTERVIEWER    -> 200, 2 dong (1 header + 1 data)
GET /users/export?status=DELETED           -> 400 "Bộ lọc DELETED không dùng cho bảng xuất này,
                                                  hãy dùng GET /api/v1/admin/users/deleted"
```

Ô độc hại (tên do người dùng tự nhập, tạo qua `PATCH /users/{id}` để đi hết đường ghi thật):

```
16401,evil1@x.com,"'=HYPERLINK(""http://x"")",...   <- dau = bi chan bang ' + nhay kep doi thanh ""
16402,evil2@x.com,"Nguyen ""Hai"" Tran, Co dau",... <- dau phay duoc boc nhay kep
16403,evil3@x.com,"Xuống dòng
trong tên",...                                      <- xuong dong nam trong nhay kep (RFC 4180)
16404,evil4@x.com,'+SUM(A1:A2) text,...              <- dau + bi chan bang '
```

Audit: **mỗi** request xuất ghi **đúng 1** dòng `EXPORT_USERS` (`targetUserId=null`) — đo vi phân:
`COUNT(*) WHERE action='EXPORT_USERS'` = **25 → 26** sau 1 request; dòng mới nhất
`detail="Xuất CSV 1 dòng | bộ lọc: search=U5, role=, status="`. Tổng audit của phiên đo
(không tính các dòng `VIEW_USER`/`LOCK_USER`… của 001): `EXPORT_USERS ok=1 ×26`,
`INVITE_USER ok=1 ×2`, `RESEND_INVITATION ok=1 ×1 / ok=0 ×2`, `REVOKE_INVITATION ok=1 ×1 / ok=0 ×1`,
`UPDATE_PROFILE ok=1 ×6 / ok=0 ×3`.

## AC-5 — không có 500 ở bất kỳ ô input nào (endpoint mới)

```
GET /users/stats?search=<101 ky tu>   -> 400 "Từ khóa tìm kiếm tối đa 100 ký tự"
GET /users/stats?role=NOT_A_ROLE      -> 400 "Tham số 'role' không hợp lệ. Giá trị được phép: [...]"
GET /users/stats?search=ok&role=ROLE_USER -> 200
GET /users?status=NOT_A_STATUS        -> 400        GET /users?status=PENDING -> 200
GET /users/deleted?role=BOGUS         -> 400        GET /users/abc            -> 400
POST  /users/999999/invitation/resend -> 404 "Không tìm thấy người dùng với id: 999999"
DELETE /users/999999/invitation        -> 404       PATCH /users/999999       -> 404
POST /users {"email":"not-an-email","fullName":"","role":"ROLE_USER"}
     -> 400 "Dữ liệu không hợp lệ" detail={"fullName":"Thiếu họ tên (fullName)","email":"Email không hợp lệ"}
PATCH /users/6 {}                     -> 400 "Không có thông tin nào để cập nhật"
PATCH /users/6 {"fullName":"   "}     -> 400 "Họ tên không được để trống"   (kiem tra truc tiep luc ghi)
```
**Không ô nào trả 500.**

## AC-6 — 401 anonymous / 403 ROLE_STAFF trên endpoint mới

Token `ROLE_STAFF` = `u6.staff@x.com` (đăng nhập thật, không mock):

| Call | anonymous | ROLE_STAFF |
|---|---|---|
| `GET /users/stats` | **401** | **403** |
| `GET /users/deleted` | **401** | **403** |
| `GET /users/export` | **401** | **403** |
| `GET /users/6` | **401** | **403** |
| `PATCH /users/6` | **401** | **403** |
| `DELETE /users/6/invitation` | **401** | **403** |
| `POST /users/6/invitation/resend` | **401** | **403** |
| `POST /users` | **401** | **403** |

Message đã đo nguyên văn trên `GET /users/stats`:
anonymous → `{"status":401,"message":"Yêu cầu đăng nhập",…}`;
token `ROLE_STAFF` → `{"status":403,"message":"Bạn không có quyền truy cập chức năng quản trị",…}`
(giống nhau giữa các endpoint; 403 luôn có `path` là URL gọi thật).

## Sửa hồ sơ (T015) và R-4 — phiên cũ phải chết khi đổi email

```
PATCH /users/13 {"fullName":"Doi Ten U8","phone":"0988000111"}
-> 200 "Hồ sơ đã được cập nhật"; SQL truoc/sau: name + doi, enabled van 1, tv van 0, vtoken van NULL

PATCH /users/13 {"email":"u8.newmail@x.com"}
-> 200 "Hồ sơ đã được cập nhật. Email mới chưa được xác thực: tài khoản chuyển sang trạng thái chờ
        kích hoạt, link mời đã được gửi và phiên đăng nhập cũ không còn hiệu lực."
   SQL: email=u8.newmail@x.com enabled=0 tv=0->1 vtoken=623bd9f9...
   audit UPDATE_PROFILE succeeded=1 "Cập nhật hồ sơ, đổi email sang: u8.newmail@x.com"
   fake Gmail: To: u8.newmail@x.com (mail xac thuc di)
   POST /auth/login {u8.newmail@x.com} -> 401 "Tài khoản chưa được kích hoạt. Vui lòng kiểm tra email."

R-4 (login bang tai khoan bi chinh sua, id 6):
   POST /auth/refresh (token cua user do) TRUOC khi doi email  -> 200
   PATCH /users/6 {"email":"u1.moved@x.com"}                    -> 200, token_version 0 -> 1
   POST /auth/refresh CUNG token DO SAU khi doi email           -> 401 "User not found with email: u1.active@x.com"
   (tokenVersion tang + email trong JWT khong con trai DB -> ca 2 lop deu giết phiên cu)

PATCH /users/13 {"email":"u1.active@x.com"}  -> 409 "Email đã tồn tại trong hệ thống"; SQL: van la u8.newmail
PATCH /users/5  {"email":"admin.moved@x.com"} ( chinh admin dang login)
   -> 400 "Không thể đổi email của chính tài khoản quản trị đang đăng nhập"; SQL email van admin@aiinterview.com
```

## Sai lệch so với plan (đã đo, có chủ đích)

1. **T016 ghi `StreamingResponseBody` → implement `ResponseEntity<byte[]>`.** Trần 10.000 dòng buộc phải
   đếm trước khi viết, nên "stream" không còn lợi ích tránh tràn bộ nhớ; file 10.000 dòng đo được 881 KB /
   0.61s. Đổi lại đường vượt trần phải tải 10.001 entity (`PageRequest.of(0, 10001)`) — đo 0.60s cho
   request 400, chấp nhận được nên không thêm query đếm riêng.
2. **T001 ghi "hàm `buildUserFilter(...)`" → implement bằng hằng JPQL** (`SEARCH_PREDICATE`,
   `ROLE_PREDICATE`, `STATUS_PREDICATE`, `LIVE_FILTERED`, `DELETED_FILTERED`) trong `JpaUserRepository`,
   5 query nối chung. Một nguồn sự thật ở tầng compile, không thể lệch giữa badge và bảng.
3. **T005 ghi `Map<String,LocalDateTime>` → `List<Object[]>`** (`findLastSuccessByEmailIn`,
   `countLiveMetrics`): Spring Data không projector tuple thành `Map`; và tuple một dòng khai báo
   `Object[]` bị **nén thành mảng dài 1** — đo thật: `GET /users/stats` 500
   `ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 1`, sửa thành `List<Object[]>` +
   `metricRows.get(0)` thì 200.
4. **Bài học text block**: `"""...WHERE""" + LIVE_FILTERED` nuốt khoảng trắng cuối → sinh JPQL
   `WHEREu ...` (`SyntaxException`, 75/101 test lỗi vì context không load được). Kết thúc block phải
   xuống dòng. Đã sửa và đo lại 101/101.
5. **409 trùng email khi mời không ghi audit `succeeded=0`**: do `attempt()` của 001 chỉ ghi khi có
   target tồn tại ("a wrong id has no subject to audit"), mà lời mời bị chặn thì chưa có user nào.
   Đã đo: 2 request 409 → `admin_audit_log` vẫn 4 dòng. Các bị chặn trên user có thật
   (`REVOKE_INVITATION`, `UPDATE_PROFILE`) **vẫn ghi** `succeeded=0` + `reason`. Ghi ở known-gaps.

## Bàn giao để thử thủ công

App vẫn đang chạy `http://127.0.0.1:8090` (jar 002), DB đo `admin_mgnt_verify` trên container
`aiinterview-mysql:3307`, trạng thái cuối: **11 tài khoản sống + 2 đã xóa mềm**, trong đó **5 tài khoản
đang giữ `verification_token`** (2 lời mời + 3 hệ quả của luồng đổi email). Fake Gmail vẫn chạy ở
cổng 4590.

Script đo trong `target/verify/` (không commit): `login.sh`, `ac1.sh`, `ac2.sh`, `ac2b.sh`, `ac3.sh`,
`t015.sh`, `ac5.sh`, `seed002_data.sql`, `bulk.sql`, `evil.sql`, `fake-gmail.mjs`.
