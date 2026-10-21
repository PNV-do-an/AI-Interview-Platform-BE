# Feature Specification: Hoàn thành dữ liệu thật cho các khối admin còn trống (wave 2026-10-21)

**Feature Branch**: `feature/admin-console-009`
**Created**: 2026-10-21 · **Status**: Draft
**Input**: FE `D:\AI-Interview-Platform-FE` branch `dev` HEAD `b2da092` — 10 màn `/admin/*`
đã nối số thật theo 004–008, còn lại các khối FE gắn `Pill icon="hive"` "Khối demo".
Spec này xoá từng khối demo mà DB **đã có dữ liệu**; khối nào thật sự không có nguồn thì
giữ nhãn demo và ghi rõ ở mục Out of scope.

## Why file 009

Sau 004–008, service layer của FE (`admin.service.ts`) khớp 100% route BE đang có — nghĩa là
mọi khối còn "Khối demo" không phải thiếu call API, mà thiếu **endpoint**. Soi từng khối theo
schema hiện tại thì phần lớn đếm được từ bảng đã tồn tại, không cần migration:

| Khối FE đang gắn nhãn demo | Nguồn thật trong DB |
|---|---|
| Biểu đồ 12 tháng "Tần suất & Xu hướng Lượt phỏng vấn AI" | `interview_sessions.created_at` (mock) + `interview_slots.start_time` với slot đã đặt (formal) |
| Ô "Thời lượng" trên dashboard | `TIMESTAMPDIFF(created_at, completed_at)` của session COMPLETED |
| "14 phòng live" | `interview_sessions.status = IN_PROGRESS` |
| "Địa chỉ IP gần nhất" + lần đăng nhập cuối | `login_attempts.email/ip_address/timestamp` (success = 1) |
| "Điểm sẵn sàng AI" theo user | trung bình `overall_score` session COMPLETED của chính user đó |
| Tab "Hoạt động" (đơn / phiên / buổi + timeline) | `applications`, `interview_sessions`, `interview_slots.booked_by` |
| Chip kỹ năng, kinh nghiệm, mục tiêu, tệp CV | `candidate_profiles` (skills, experience_years, career_goal, cv_file_name) |
| 4 chip thống kê hàng chờ JD | `job_descriptions` theo `status` (đã có `countLiveByStatus`) |
| "Điểm tín nhiệm NTD" + lịch sử đăng tin | `job_moderation_records` theo `employer_id` |
| 4 thẻ thẩm định AI + cột `aiSafetyScore` (luôn trống) | sàng lọc bằng **rule tất định** trên nội dung JD, không gọi mô hình |
| Màn `/admin/categories` (chưa có endpoint nào) | giá trị distinct của `department` / `experienceLevel` / `employmentType` / `workplace_model` trên job chưa xóa |
| ROLE_STAFF nhận 403 khi mở hàng chờ JD | `PATCH /jobs/{id}/review` đã cho STAFF, 3 endpoint đọc lại chặn ADMIN-only |

005 từng ghi "biểu đồ chuỗi thời gian: cần bảng daily aggregate" nên cắt. Cách làm kỳ này
không lập bảng aggregate: nhóm trực tiếp trên bảng sự kiện bằng `GROUP BY` tháng, chỉ phục vụ
12 mốc nên chi phí đọc chấp nhận được — đây là **đảo lại quyết định cũ của 005**, có chủ đích.

## User Stories

**US-01** Admin mở dashboard: biểu đồ 12 tháng có hai chuỗi (luyện tập AI / phỏng vấn chính
thức theo tháng), ô thời lượng hiển thị trung bình thật, card hệ thống cho thấy số phiên
đang diễn ra — mọi con số đều truy ngược được về một câu `SELECT`.

**US-02** Admin mở hồ sơ một người dùng: thấy lần đăng nhập cuối + IP, điểm sẵn sàng AI của
riêng người đó, tab "Hoạt động" liệt kê số đơn ứng tuyển / số phiên luyện / số buổi đã đặt
kèm timeline pha trộn ba loại sự kiện, và khối kỹ năng/kinh nghiệm/CV lấy từ hồ sơ ứng viên.

**US-03** Admin/Staff mở hàng chờ kiểm duyệt JD: 4 chip thống kê theo trạng thái tin; trong
chi tiết một tin thấy hồ sơ nhà tuyển dụng (tổng số tin đã đăng, bao nhiêu tin từng bị từ
chối, điểm tín nhiệm dẫn xuất từ chính tỷ lệ đó) và 4 thẻ thẩm định mô tả vì sao tin bị trừ
điểm an toàn. Staff không còn nhận 403 ở màn này.

**US-04** Admin mở màn Danh mục: thấy các giá trị ngành/bình cấp/kiểu làm việc đang thực sự
được dùng trên tin tuyển dụng, kèm số tin mỗi giá trị.

## Functional Requirements

- **FR-001** `GET /api/v1/admin/dashboard` (005) được **mở rộng thêm**, không đổi các nhóm cũ:
  - `interviews.avgDurationSeconds` — trung vị? không: **trung bình** số giây giữa
    `created_at` và `completed_at`, chỉ tính dòng có cả hai mốc; null khi không có.
  - `interviews.liveNow` — đếm session `status = IN_PROGRESS`.
  - `trends.months[12]` — 12 tháng gần nhất kết thúc ở tháng hiện tại, mỗi phần tử
    `{ month: "yyyy-MM", mock, formal }`, tháng không có dữ liệu ⇒ 0 chứ không thiếu mốc.
    `mock` = số `interview_sessions` tạo trong tháng (mọi mode); `formal` = số slot
    `booked_by NOT NULL AND cancelled = false AND start_time` thuộc tháng đó.
- **FR-002** `GET /api/v1/admin/users/{id}` trả thêm `lastLoginAt`, `lastIp` (dòng
  `login_attempts` success mới nhất theo email), `aiReadinessScore` (trung bình
  `overall_score` session COMPLETED của user, làm tròn 1 chữ số, null khi chưa có),
  `activity { applications, mockSessions, bookedInterviews }`, và `profile` (null với user
  chưa có hồ sơ ứng viên): `{ skills: [ … ], experienceYears, summary, careerGoal,
  cvFileName, updatedAt }`.
- **FR-003** `GET /api/v1/admin/users/{id}/activity?page=&size=` — timeline pha trộn ba
  nguồn (đơn ứng tuyển, phiên luyện, slot đã đặt), mỗi phần tử
  `{ type: APPLICATION | MOCK_SESSION | INTERVIEW_SLOT, id, title, status, occurredAt }`,
  mới nhất trước, phân trang chuẩn `PagedResponse`; user không tồn tại ⇒ 404; size > 100 ⇒ 400.
- **FR-004** `GET /api/v1/admin/jobs/moderation/stats` — `{ pending, approved, rejected,
  draft, total }` trên job chưa xóa mềm: `pending` = `status = PENDING_APPROVAL`,
  `draft` = `DRAFT`, `approved` = số tin từng có quyết định APPROVED, `rejected` = số tin
  đang `DRAFT` sau khi bị trả lại hoặc từng có quyết định REJECTED (định nghĩa ghi ở plan),
  `total` = số job live.
- **FR-005** Chi tiết kiểm duyệt (`GET /admin/jobs/moderation/{id}`) trả thêm `employer`
  `{ id, companyName, email, accountCreatedAt, jobs { total, approved, rejected, pending },
  trustScore }` với `trustScore` = `round(100 × approved / (approved + rejected))`,
  mẫu số 0 ⇒ null (không giả định 100).
- **FR-006** Sàng lọc chính sách JD **bằng rule, không gọi mô hình AI**: điểm 0–100 trừ dần
  theo 4 kiểm tra (tiêu đề liên quan IT, độ dài mô tả, có yêu cầu kỹ năng, không lộ thông
  tin liên lạc). Điểm được ghi vào cột `ai_safety_score` khi employer nộp tin
  (`submitForReview`), và chi tiết kiểm duyệt trả `aiSafety { score, checks[] }` tính lại từ
  đúng các rule đó trên nội dung đang lưu — hai chỗ phải khớp nhau vì rule là hàm thuần của
  dữ liệu đã lưu. Không có mô hình ML nào đứng sau; tên trường giữ `aiSafetyScore` cho FE
  khỏi đổi hợp đồng.
- **FR-007** Ba endpoint đọc của hàng chờ JD (`GET /admin/jobs/moderation`, `/{id}`,
  `/history*`) mở cho `ROLE_STAFF` (URL rule + `@PreAuthorize`), khớp quyền của
  `PATCH /jobs/{id}/review`. Admin vẫn là role duy nhất được xem dashboard/user/settings/RBAC.
- **FR-008** `GET /api/v1/admin/categories` — `{ groups: [ { key, label, values: [
  { value, jobCount } ] } ] }` cho 4 khóa `department`, `experienceLevel`, `employmentType`,
  `workplaceModel`; đếm trên job chưa xóa mềm, bỏ giá trị null/rỗng, sắp theo số giảm dần.
- **FR-009** Mọi endpoint mới đều: anonymous ⇒ 401; role ngoài hợp đồng ⇒ 403; DB trống ⇒
  cấu trúc đầy đủ với 0/null, không 500; không có tham số nào gây lỗi máy chủ.

## Out of scope (không có nguồn số thật — FE giữ nhãn "Khối demo")

- Uptime 99.98%, độ trễ 320ms, token 1.240/2.500, card "Hạ tầng kết nối AI", version
  `v2.4.0-prod`: BE không đo hạ tầng, không có bảng nào chứa các giá trị này.
- Ngày sinh / nơi cư trú: không có cột, và FE cũng không có form nhập ⇒ không thêm cột rỗng.
- Avatar upload/xóa, 2FA, danh sách phiên đăng nhập đang hoạt động + thu hồi từng phiên:
  JWT stateless, không có kho file, không có bảng session.
- "Doanh nghiệp đã xác minh" + MST, thứ hạng tín nhiệm vùng miền: không có nguồn xác minh.
- Taxonomy CRUD (thêm/sửa/xóa danh mục chuẩn): `department…` hiện là chuỗi tự do trên từng
  job; tạo bảng danh mục riêng là đổi mô hình dữ liệu, vượt quá "hoàn thành backend màn admin".
  Kỳ này chỉ trả **aggregate đọc từ dữ liệu đang có**.
- Điểm AI "thẩm định nội dung" bằng mô hình ngôn ngữ: chỉ rule tất định (FR-006).

## Acceptance criteria (đo được)

- AC-1: `trends.months` luôn 12 phần tử, mốc cuối = tháng hiện tại; mỗi `mock` khớp
  `SELECT COUNT(*) FROM interview_sessions WHERE created_at …` của cùng tháng; `formal` khớp
  câu đếm slot đã đặt, chưa hủy, `start_time` cùng tháng (spot-check 2 tháng có dữ liệu).
- AC-2: `interviews.avgDurationSeconds` khớp `AVG(TIMESTAMPDIFF(SECOND, created_at,
  completed_at))` trên session COMPLETED; DB không có session hoàn thành ⇒ null.
  `interviews.liveNow` khớp đếm `status='IN_PROGRESS'`.
- AC-3: user có 2 lần đăng nhập thành công ⇒ `lastLoginAt`/`lastIp` là dòng mới nhất;
  user chưa từng đăng nhập ⇒ cả hai null. `aiReadinessScore` khớp trung bình điểm session
  COMPLETED của riêng user đó, user khác điểm không làm nhiễu.
- AC-4: `activity` counts khớp ba câu đếm; timeline trả đúng `type` từng nguồn, sắp mới
  nhất trước, `page=0&size=5` cắt đúng 5 dòng và `totalElements` không đổi theo page.
- AC-5: `stats` khớp `countLiveByStatus` + đếm quyết định trong `job_moderation_records`;
  `total` = tổng job chưa xóa mềm.
- AC-6: employer có 3 tin (2 approved, 1 rejected) ⇒ `trustScore` = 67; employer chưa có
  quyết định nào ⇒ `trustScore` null, không phải 100 hay 0.
- AC-7: JD nộp duyệt ⇒ `ai_safety_score` được ghi, `aiSafety.checks` có 4 phần tử, và
  `score` trong response chi tiết **bằng đúng** giá trị đã lưu; tin thiếu mô tả/yêu cầu bị
  trừ điểm nêu rõ lý do bằng lời Việt.
- AC-8: ROLE_STAFF: `GET /admin/jobs/moderation` ⇒ 200, `GET /admin/dashboard` ⇒ 403.
  ROLE_USER ⇒ 403. Anonymous ⇒ 401.
- AC-9: `GET /admin/categories` khớp `SELECT department, COUNT(*) … WHERE deleted_at IS NULL
  GROUP BY department`; giá trị null/rỗng không xuất hiện.
- AC-10: các số 005 đã chốt (`users.total`, `byRole`, `jobs.byStatus`…) không đổi hợp đồng
  sau khi mở rộng response — test 005 vẫn nguyên màu xanh.
