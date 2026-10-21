# Hợp đồng API — spec 009 (dữ liệu thật cho các khối admin còn trống)

Branch 009 (tách từ `dev` khi wave này được commit). Mọi response bọc envelope `ApiResponse`
`{success, message, data}` như 001–008; lỗi dùng `ErrorModel`.

## 1. `GET /api/v1/admin/dashboard?days=7|30|90` (mở rộng 005)

`data` giữ nguyên 5 nhóm cũ của 005, thêm:

```jsonc
"data": {
  "interviews": {
    "total": 12, "inPeriod": 4, "completedInPeriod": 3,
    "avgOverallScore": 81.5,          // null khi chưa có session nào được chấm
    "avgDurationSeconds": 900,        // null khi chưa có session hoàn thành (FE tự đổi ra "15m 0s")
    "liveNow": 1                      // session đang IN_PROGRESS -> thay ô "14 phòng live"
  },
  "trends": {
    "months": [                       // ĐÚNG 12 phần tử, tháng cũ nhất trước, phần tử cuối = tháng hiện tại
      { "month": "2025-11", "mock": 0, "formal": 0 },
      { "month": "2026-10", "mock": 2, "formal": 2 }
    ]
  }
}
```

- `mock` = số `interview_sessions` tạo trong tháng (cả hai mode).
- `formal` = số `interview_slots` đã đặt (`booked_by` không null), chưa hủy, `start_time` rơi vào tháng đó.
- Tháng không có dữ liệu vẫn có mặt với 0 — FE không cần nội suy.

**Thay được khối demo**: biểu đồ area 12 tháng, ô "Thời lượng".
**Không thay được**: card "Tài nguyên AI & Hệ thống" (uptime/độ trễ/token) — chỉ `liveNow` là thật.

## 2. `GET /api/v1/admin/users/{id}` (mở rộng 001/002)

```jsonc
"data": {
  "...": "toàn bộ field cũ của UserDetailResponse",
  "lastLoginAt": "2026-10-21T09:12:03",   // null nếu chưa từng đăng nhập thành công
  "lastIp": "10.0.0.9",                    // null như trên
  "aiReadinessScore": 85.0,                // trung bình điểm session COMPLETED của RIÊNG user, 1 chữ số, null khi chưa có
  "activity": { "applications": 3, "mockSessions": 8, "bookedInterviews": 2 },
  "profile": {                              // null khi user chưa có candidate_profiles
    "skills": ["Java", "Spring Boot", "React"],
    "experienceYears": 4,
    "summary": "Backend engineer",
    "careerGoal": "Dẫn dắt team platform",
    "cvFileName": "cv-huy.pdf",
    "updatedAt": "2026-10-20T18:04:11"
  }
}
```

- `lastIp` lấy từ `login_attempts` (chỉ dòng `success = true` mới nhất); không có bảng session nên
  đây là IP gần nhất *mà hệ thống biết*, không phải danh sách thiết bị.
- Các endpoint ghi (`lock`, `role`, `status`, …) vẫn trả `UserDetailResponse` nhưng **không** kèm
  5 field mới (để không nhân bản truy vấn trên mỗi hành động). FE muốn số thì gọi lại `GET /users/{id}`.

**Thay được khối demo**: "Địa chỉ IP gần nhất", điểm sẵn sàng AI 94%, chip kỹ năng, khối
CV/kinh nghiệm/mục tiêu, 3 con số của tab "Hoạt động".
**Không thay được**: ngày sinh + nơi cư trú (không có cột và không có form nhập), ảnh avatar.

## 3. `GET /api/v1/admin/users/{id}/activity?page=0&size=20` (MỚI)

```jsonc
"data": {
  "content": [
    { "type": "INTERVIEW_SLOT", "id": 44, "title": "Employer One", "status": "BOOKED",  "occurredAt": "2026-10-24T09:00:00" },
    { "type": "MOCK_SESSION",   "id": 12, "title": "Java Spring Boot", "status": "COMPLETED", "occurredAt": "2026-10-20T08:00:00" },
    { "type": "APPLICATION",    "id": 7,  "title": "Backend Java", "status": "PENDING", "occurredAt": "2026-10-17T14:22:00" }
  ],
  "totalElements": 13, "totalPages": 1, "number": 0, "size": 20
}
```

- Sắp mới nhất trước, `occurredAt` = thời điểm sự kiện (slot: `start_time`; hai loại kia: lúc tạo).
- Guard: `size` 1–100, `page ≥ 0`, `(page+1)*size ≤ 1000` ⇒ vượt thì 400
  "Dòng thời gian chỉ xem tới 1000 dòng đầu"; id lạ ⇒ 404; STAFF ⇒ 403; anonymous ⇒ 401.
- Không ghi `admin_audit_log` (đọc phụ của màn chi tiết; VIEW_USER đã ghi ở `GET /users/{id}`).

**Thay được khối demo**: timeline tab "Hoạt động".

## 4. `GET /api/v1/admin/jobs/moderation/stats` (MỚI)

```jsonc
"data": { "pending": 0, "draft": 2, "approved": 2, "rejected": 1, "total": 4 }
```

- `pending`/`draft`/`approved` = số job live theo `status` (OPEN = đã qua duyệt trong luồng này).
- `rejected` = số **job khác nhau** từng có một quyết định REJECTED; nhóm này giao với `draft`
  vì tin bị trả lại trở về DRAFT — 4 chip không phải một phép chia tổng.
- `total` = số job live (khớp `jobs.total` trên dashboard).
- Quyền: ADMIN + STAFF (xem mục 6).

**Thay được khối demo**: 4 chip thống kê đầu màn hàng chờ.

## 5. `GET /api/v1/admin/jobs/moderation/{id}` (mở rộng 004)

```jsonc
"data": {
  "...": "job / submittedAt / decidedAt / timeline như 004",
  "aiSafetyScore": 75,               // giá trị ĐÃ LƯU lúc nộp; null với tin nộp trước 009
  "aiSafety": {
    "score": 75,                     // tính lại bằng đúng rule, luôn có
    "checks": [
      { "key": "IT_RELEVANT_TITLE",     "label": "Tiêu đề đúng lĩnh vực công nghệ", "passed": true,  "note": "Tiêu đề khớp từ khóa kỹ thuật \"java\"." },
      { "key": "DESCRIPTION_LONG_ENOUGH","label": "Mô tả đủ chi tiết",              "passed": true,  "note": "Mô tả có 312 ký tự (ngưỡng 200)." },
      { "key": "REQUIREMENTS_PRESENT",  "label": "Có yêu cầu và kỹ năng bắt buộc",   "passed": true,  "note": "Ứng viên biết rõ tin này đòi hỏi gì." },
      { "key": "NO_CONTACT_LEAKS",      "label": "Không lộ thông tin liên lạc ngoài nền tảng", "passed": false, "note": "Nội dung tin chứa địa chỉ email — ứng viên có thể bị dẫn ra ngoài nền tảng." }
    ]
  },
  "employer": {
    "id": 3, "companyName": "ACME", "email": "employer@example.com",
    "accountCreatedAt": "2026-09-01T08:00:00",
    "jobs": { "total": 3, "approved": 2, "rejected": 1, "pending": 0 },
    "trustScore": 67                  // null khi NTD chưa có quyết định nào — không mặc định 100
  }
}
```

Rule sàng lọc: 4 kiểm tra, mỗi kiểm tra trượt trừ 25 ⇒ `score ∈ {100, 75, 50, 25, 0}`.
**KHÔNG có mô hình AI nào** đứng sau — chỉ là hàm tất định trên chính dữ liệu tin đã lưu,
nên điểm ghi lúc nộp và điểm tính lại lúc đọc luôn khớp.

**Thay được khối demo**: 4 thẻ thẩm định, cột `aiSafetyScore`, "Điểm tín nhiệm NTD" + lịch sử đăng tin,
block "Cảnh báo kiểm duyệt JD vi phạm chính sách" (đọc `aiSafety.checks` có `passed = false`).
**Không thay được**: xác minh doanh nghiệp + MST.

## 6. Quyền mới cho ROLE_STAFF

`/api/v1/admin/jobs/moderation/**` (queue, `/{id}`, `/history`, `/history/stats`, `/stats`)
mở cho ADMIN **và** STAFF, khớp `PATCH /api/v1/jobs/{id}/review`. Mọi endpoint admin khác
(`users`, `dashboard`, `settings`, `permissions`, `categories`, `audit-logs`) vẫn ADMIN-only.
FE vì thế bỏ được ErrorBlock ở màn hàng chờ khi đăng nhập bằng STAFF.

## 7. `GET /api/v1/admin/categories` (MỚI — ADMIN only)

```jsonc
"data": {
  "groups": [
    { "key": "department",      "label": "Phòng ban",          "values": [ { "value": "Engineering", "jobCount": 2 }, { "value": "Sales", "jobCount": 1 } ] },
    { "key": "experienceLevel", "label": "Cấp kinh nghiệm",    "values": [ … ] },
    { "key": "employmentType",  "label": "Kiểu làm việc",      "values": [ … ] },
    { "key": "workplaceModel",  "label": "Hình thức làm việc", "values": [ { "value": "HYBRID", "jobCount": 1 }, … ] }
  ]
}
```

- Đếm trên job chưa xóa mềm, bỏ giá trị null/rỗng; sắp theo số job giảm dần rồi theo chữ.
- `workplaceModel` là enum nên giá trị là `ONSITE|HYBRID|REMOTE`; ba khóa còn lại là chuỗi tự do
  do employer nhập ⇒ **đọc, không CRUD** (chưa có bảng danh mục chuẩn).
