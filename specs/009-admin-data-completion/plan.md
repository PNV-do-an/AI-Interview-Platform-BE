# Plan — spec 009: hoàn thành dữ liệu thật cho các khối admin còn trống

## Cách chọn khối làm

FE wave 2026-10-21 dán nhãn "Khối demo" lên mọi block không có endpoint. Soi từng block xuống
schema đang có: block nào đếm được bằng một câu `SELECT` trên bảng đã tồn tại thì làm, block nào
cần số BE không hề đo (uptime, độ trễ, token, 2FA, danh sách phiên, ngày sinh) thì **giữ nhãn
demo** — không dựng cột rỗng hay con số bịa chỉ để gỡ nhãn.

## F1 — dashboard (`AdminDashboardServiceImpl`)

- `trends.months[12]`: hai `GROUP BY DATE_FORMAT(..., '%Y-%m')` — `interview_sessions.created_at`
  cho chuỗi mock, `interview_slots.start_time` (đã đặt, chưa hủy) cho chuỗi formal. Không lập bảng
  aggregate: 005 cắt biểu đồ vì lý do đó, nhưng 12 mốc thì hai câu group-by là đủ rẻ.
  Bucket dựng trước 12 ô trong `LinkedHashMap`, hàng ngoài cửa sổ (13 tháng trước) bị bỏ — test
  chứng minh đúng một phiên 13 tháng tuổi không rơi vào ô nào.
- `interviews.avgDurationSeconds`: `AVG(TIMESTAMPDIFF(SECOND, created_at, completed_at))` qua
  native query (MySQL-only) vì phép trừ mốc nằm trong DB, không phải trong JVM. Null khi chưa có
  session hoàn thành ⇒ FE hiện "—", không hiện 0.
- `interviews.liveNow`: `countByStatus(IN_PROGRESS)`.

## F2 — chi tiết user (`AdminUserServiceImpl`)

- `lastLoginAt`/`lastIp`: `findTop1ByEmailAndSuccessTrueOrderByTimestampDesc` — **chỉ dòng
  success**, vì màn hình cần "lần đăng nhập cuối" chứ không phải "lần thử cuối".
  `login_attempts` khóa theo email (bảng không có user_id), nên user đổi email thì mất dấu — chấp
  nhận, đó là dữ liệu đang có.
- `aiReadinessScore`: trung bình `overall_score` session COMPLETED của riêng user, null khi chưa có.
- `activity` ba số đếm + `GET /users/{id}/activity` gộp timeline: mỗi nguồn đọc đúng
  `(page+1)*size` dòng rồi trộn trong bộ nhớ, vì không có bảng sự kiện chung. Cửa sổ 1000 dòng để
  không ai phải tải vô hạn; `totalElements` vẫn là tổng thật của ba nguồn.
- `profile`: `candidate_profiles` → `skills` tách chuỗi CSV (trim, bỏ rỗng, distinct),
  `experienceYears`, `summary`, `careerGoal`, `cvFileName`, `updatedAt`. User chưa có hồ sơ ⇒ null.
- Năm field mới **chỉ** được nạp ở `GET /users/{id}`; các endpoint ghi trả DTO nhẹ như cũ để mỗi
  hành động lock/role không kéo theo 6 truy vấn.

## F3 — hàng chờ kiểm duyệt

- `GET /admin/jobs/moderation/stats`: bốn con số từ `countLiveByStatus` +
  `countDistinctJobsByAction(REJECTED)`. `rejected` giao `draft` là cố ý (tin bị trả về nháp).
- `employer` trong detail: `countByEmployerIdAndDeletedAtIsNull*` +
  `countDistinctJobsOfEmployerByAction`; `trustScore = round(100 × approved / (approved + rejected))`,
  mẫu số 0 ⇒ **null** (không mặc định 100 cho người mới).
- `JdPolicyScreener` (`service/ai/`): 4 kiểm tra tất định, mỗi kiểm tra trượt trừ 25. Ghi
  `ai_safety_score` lúc `submitForReview`, và `updateJob` tính lại nếu cột đã có giá trị (tin đã
  OPEN bị sửa thì điểm lưu phải theo nội dung mới). Chi tiết trả `aiSafety{score, checks[]}` tính
  lại từ cùng rule ⇒ `score` khớp cột lưu với mọi tin nộp qua 009; `checks[].note` là tiếng Việt
  mô tả lý do, đủ cho 4 thẻ thẩm định và block "Cảnh báo kiểm duyệt".
  **Không có mô hình AI nào** — muốn thay engine thì thay mỗi class này, hợp đồng response giữ nguyên.

## F4 — `GET /admin/categories`

Bốn `GROUP BY` trên `job_descriptions` chưa xóa mềm, loại null/rỗng (`TRIM(x) <> ''`), sắp trong
Java theo số giảm dần rồi theo chữ. `workplaceModel` là enum nên stringify ở service.
Chỉ đọc: chưa có bảng taxonomy nên không thêm/sửa/xóa giá trị.

## Quyền

`/api/v1/admin/jobs/moderation/**` → `hasAnyRole('ADMIN','STAFF')` ở **cả hai** tầng
(URL rule trong `JwtSecurityConfig` + `@PreAuthorize` của controller), khớp `PATCH /jobs/{id}/review`
vốn đã cho staff quyết định. Test 004 cũ assert "STAFF ⇒ 403 trên 4 endpoint console" đã đổi thành
"STAFF đọc được hàng chờ, admin-only endpoint khác vẫn 403".

## Test

`AdminDataCompletionIntegrationTest` (13 test) — mỗi test đối chiếu một con số với câu SQL/JPA đếm
trên cùng DB. Hồi quy: `JobModerationConsoleIntegrationTest` đổi kỳ vọng STAFF; suite đầy đủ phải
giữ nguyên 154 test của 001–008.
