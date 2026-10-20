# Feature Specification: Dashboard tổng hợp cho admin (màn 16)

**Feature Branch**: `feature/admin-console-004-008`
**Created**: 2026-10-20 · **Status**: Draft · **Input**: mockup Stitch "Admin Dashboard"
+ chốt phạm vi 2026-10-20 (phương án "Endpoint tổng hợp").

## Why file 005

Màn 16 gom ~20 con số từ mọi miền của hệ thống. Hiện chỉ có `GET /admin/users/stats`
(một miền). FE tự gộp từ nhiều endpoint thì thiếu các số đếm chéo (JD chờ duyệt, phiên
phỏng vấn trong 7 ngày, lượt đăng nhập) và mỗi lần load phải gọi 5–6 request. Một endpoint
tổng hợp duy nhất, **chỉ trả số có nguồn thật trong DB**, là toàn bộ phạm vi kỳ này.

## User Stories

**US-01** Admin mở dashboard, một request nhận đủ: người dùng theo vai trò/trạng thái,
tin tuyển dụng theo trạng thái (kèm số chờ duyệt), phiên phỏng vấn AI (tổng, hoàn thành,
điểm trung bình), lượt đăng nhập thành công/thất bại, số tài khoản mới.
**US-02** Admin đổi khoảng thời gian (7/30/90 ngày) → các số "trong kỳ" (user mới, phiên,
đăng nhập, tin mới) tính lại theo kỳ; các số cộng dồn (tổng tất cả) không đổi.

## Functional Requirements

- **FR-001** `GET /api/v1/admin/dashboard?days=30` — `days ∈ {7, 30, 90}` (mặc định 30),
  sai giá trị ⇒ 400 kèm allowedValues. Dưới URL rule `/api/v1/admin/**` ⇒ chỉ ADMIN.
- **FR-002** Response `DashboardResponse` gồm 5 nhóm, mỗi số là **một câu đếm từ DB**:
  - `users`: `total` (chưa xóa mềm), `active`, `pendingVerification`, `locked`, `deleted`,
    `byRole {USER, STAFF, INTERVIEWER, ADMIN}`, `newInPeriod`, `loginsInPeriod` (distinct
    email có `login_attempts.success=1` trong kỳ — đúng nghĩa "đã đăng nhập", không phải
    "hoạt động", theo C-1 của 002).
  - `jobs`: `total` (chưa xóa), `byStatus {DRAFT, PENDING_APPROVAL, OPEN, CLOSED}`,
    `pendingModeration` (= `byStatus.PENDING_APPROVAL`, lặp lại để FE khỏi tra chồng),
    `newInPeriod`.
  - `interviews`: `total`, `inPeriod`, `completedInPeriod`, `avgOverallScore`
    (trung bình `overall_score` khác null, làm tròn 1 chữ số thập phân; null khi không có
    dữ liệu).
  - `logins`: `successInPeriod`, `failedInPeriod` (đếm dòng `login_attempts` theo `success`).
  - `moderation`: `pendingApproval` — đồng bộ với 004.
- **FR-003** Định nghĩa trạng thái user dùng **chung predicate với 001/002** (active =
  enabled && !locked; pendingVerification = verification_token IS NOT NULL) để số dashboard
  không lệch màn user management.
- **FR-004** Không tổ hợp tham số nào trả 500; anonymous 401; STAFF 403.

## Out of scope (không có nguồn dữ liệu thật, cắt có chủ đích)

- Uptime SLA, độ trễ LLM, token budget, dung lượng storage, db pool — chỉ số hạ tầng, BE
  không đo; FE tự để "—" hoặc lấy từ hệ thống khác.
- Biểu đồ chuỗi thời gian (line chart theo tháng), so sánh YOY — cần bảng daily aggregate;
  kỳ này chỉ trả con số tại thời điểm gọi.
- Phân bố người dùng dạng % (FE tự tính % từ `byRole`).
- "Tin bị cắm cờ", "Cần AI đánh giá lại", doanh nghiệp xác minh/tier — không có nguồn (như 004).
- Nhật ký hoạt động real-time + WebSocket: không SSE; FE dùng `GET /admin/audit-logs` có sẵn.
- Export báo cáo dashboard.

## Acceptance criteria (đo được)

- AC-1: mỗi con số trong response khớp câu SQL đếm tương ứng chạy trên cùng DB (spot-check
  ≥ 6 số lệch miền nhau: users.total, users.byRole.ADMIN, jobs.pendingModeration,
  interviews.completedInPeriod, logins.failedInPeriod, users.newInPeriod).
- AC-2: `days=7` vs `days=30` ⇒ `newInPeriod`/`inPeriod`/`successInPeriod` khác nhau đúng
  theo dữ liệu test; `users.total` giữ nguyên giữa 2 lần gọi.
- AC-3: `days=5` ⇒ 400 kèm allowedValues `7, 30, 90`; anonymous 401; STAFF 403.
- AC-4: DB rỗng (mới create) ⇒ mọi số = 0, `avgOverallScore` = null, không 500.
