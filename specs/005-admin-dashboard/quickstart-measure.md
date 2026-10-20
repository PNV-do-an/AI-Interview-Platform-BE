# Bộ đo 005 — Dashboard tổng hợp cho admin (đo 2026-10-20)

## Môi trường đo

- Chạy trong container maven với Testcontainers MySQL 8.0 thật (công thức Docker ghi ở
  `specs/001-admin-user-management/quickstart.md`), `GET /api/v1/admin/dashboard` qua MockMvc.
- Mọi con số trong response đều bị đối chiếu lại bằng `JdbcTemplate` chạy câu SQL đếm thủ công
  trên đúng DB vừa seed — không có test nào chỉ dừng ở HTTP code.
- `@BeforeEach` xóa ngược thứ tự có FK: `job_moderation_records` → `interview_sessions` →
  `job_descriptions` → `admin_audit_log` → `users`, rồi `delete from login_attempts`
  (`login_attempts` do DB sinh, không có entity). Nhờ vậy DB thật sự rỗng để đo AC-4.

## Kết quả

Class `AdminDashboardIntegrationTest` — **4/4 pass**, 112.7s (lần chạy chốt 2026-10-20).

Suite đầy đủ `mvn -B -o test` (lần chạy gộp 21h45 2026-10-20): **Tests run: 154, Failures: 0, Errors: 15** —
15 error đó là `JobControllerIntegrationTest.setUp` vướng FK của bảng 004 thêm (sửa trong 004),
không liên quan dashboard. Sau khi sửa thứ tự xóa, chạy lại riêng class đó (22h53 cùng ngày)
⇒ **16/16 BUILD SUCCESS**, tức **154/154**.

| AC | Test | Số đo được |
|---|---|---|
| AC-1 | `testDashboard_NumbersMatchSql` | Seed 4 user + 1 employer, 4 job (1 PENDING_APPROVAL / 1 OPEN / 2 DRAFT), 1 session COMPLETED điểm 8 + 1 IN_PROGRESS, 2 login thành công + 1 fail. Bảy con số `users.total / jobs.total / interviews.total / logins.successInPeriod / logins.failedInPeriod / jobs.pendingModeration / moderation.pendingApproval` bằng đúng SQL đếm cùng DB. `interviews.avgOverallScore = 8.0`, `users.loginsInPeriod = 1` (đếm email DISTINCT thành công — semantics 002 C-1), `rangeDays = 30` |
| AC-2 | `testDashboard_DaysWindow` | Aged data: user tạo 40 ngày trước, session ở ngày 3 và 20, login ở ngày 3 / 20 / 45. `days=7`: `users.newInPeriod=1`, `interviews.inPeriod=1`, `successInPeriod=1`, `failedInPeriod=0`. `days=30`: 2/2/2/1. `users.total` vẫn 4 ở cả 7/30/90 — cửa sổ chỉ chạm nhóm con số "trong kỳ" |
| AC-3 | `testDashboard_BadRangeAndGuards` | `days=5` ⇒ 400, message nêu rõ `7, 30, 90`; anonymous ⇒ 401 "Yêu cầu đăng nhập"; `ROLE_STAFF` ⇒ 403 "Bạn không có quyền truy cập chức năng quản trị" |
| AC-4 | `testDashboard_EmptyDatabase` | DB rỗng ⇒ mọi số đếm = 0, `avgOverallScore` = `null` (không phải 0), HTTP 200, không 500 |

## Sai lệch so với plan (có chủ đích)

- Mockup vẽ thêm biểu đồ tuần/tháng, phân bố địa lý, "activation rate" ⇒ **không có endpoint**:
  những số đó không có nguồn dữ liệu thật trong DB này, bịa số thì vô nghĩa (chốt phương án
  "Endpoint tổng hợp": chỉ số đo được).
- `avgOverallScore` dùng `AVG(overall_score)` trần ⇒ trả `null` khi chưa có buổi nào có điểm.
  Test assert `null`, không assert 0 — 0 sẽ là dữ liệu sai.

## Chưa đo (nằm ngoài kỳ này)

- Chưa đo trên Render staging — deploy theo superrepo, sau merge `dev`.
- Chưa có số "user hoạt động theo tuần" vì `login_attempts` chỉ lưu từng lần đăng nhập,
  không có bảng session đang hoạt động (JWT stateless).
