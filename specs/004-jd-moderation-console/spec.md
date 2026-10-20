# Feature Specification: Console kiểm duyệt JD cho admin (màn 18, 18.1, 18.2)

**Feature Branch**: `feature/admin-console-004-008`
**Created**: 2026-10-20 · **Status**: Draft · **Input**: mockup Stitch "JD Moderation",
"JD Review Detail", "Moderation History" (zip gửi 2026-10-20) + chốt phạm vi 4/4 cùng ngày
(phương án "Trọn bộ").

## Why file 004

Spec 003 (create-job) đã dựng được **vòng đời JD**: `POST /jobs` tạo `DRAFT` →
`PATCH /{id}/submit` đưa lên `PENDING_APPROVAL` → `PATCH /{id}/review` (ADMIN/STAFF)
cho kết quả `OPEN` (duyệt, xóa note) hoặc về `DRAFT` (từ chối kèm `reviewNote`).
Mockup màn 18 không đòi vòng đời mới — nó đòi **góc nhìn admin**: hàng đợi chờ duyệt,
trang chi tiết kèm timeline, và lịch sử + thống kê mọi quyết định. Ba khả năng này chưa
có endpoint nào; đồng thời mockup có vài chi tiết BE không có nguồn (điểm AI, cờ VIP,
điểm tín nhiệm NTD) phải cắt có chủ đích.

## User Stories

**US-01** Admin mở console kiểm duyệt, thấy danh sách tin `PENDING_APPROVAL` (kèm tổng số,
lọc theo trạng thái/từ khóa, phân trang) mà không phải đoán id.
**US-02** Admin bấm một tin → xem toàn bộ nội dung JD + thông tin NTD + `reviewNote` gần nhất
+ timeline các sự kiện kiểm duyệt (nộp, nộp lại, duyệt, từ chối — ai, lúc nào, ghi chú gì).
**US-03** Admin duyệt/từ chối ngay trên console; từ chối bắt buộc có lý do; tin bị từ chối quay
về `DRAFT` để NTD sửa rồi gửi duyệt lại (đúng cơ chế 003 đã có).
**US-04** Admin tra cứu lịch sử: mọi quyết định duyệt/từ chối (kèm lý do, người duyệt, thời
gian xử lý), lọc theo quyết định/khoảng ngày/người duyệt/từ khóa, có thống kê tổng quan
(tổng đã xử lý, số duyệt, số từ chối, thời gian xử lý trung bình).
**US-05** Mỗi lần NTD submit/resubmit và mỗi lần admin duyệt/từ chối đều để lại một bản ghi
lịch sử kiểm duyệt (bảng riêng, không phải chuỗi audit string).

## Functional Requirements

- **FR-001** `GET /api/v1/admin/jobs/moderation` — danh sách cho console: lọc `status`
  (`PENDING_APPROVAL` mặc định; nhận đủ 4 giá trị `JobStatus`), `search` khớp tiêu đề /
  tên công ty / email NTD, `page/size≤100`, sắp xếp `createdAt DESC` (hằng số, không nhận
  tham số sort ở kỳ này). Mỗi dòng: id, tiêu đề, công ty, NTD (tên + email), trạng thái,
  `reviewNote`, `submittedAt`, `decidedAt`, `createdAt`.
- **FR-002** `GET /api/v1/admin/jobs/moderation/{id}` — chi tiết đầy đủ (bằng hợp đồng
  `JobDetailResponse` 003) + `submittedAt`, `decidedAt`, `aiSafetyScore` + timeline các
  bản ghi lịch sử (mới nhất lên đầu). Tin đã xóa mềm ⇒ 404.
- **FR-003** Bảng mới `job_moderation_records`: `job_id`, `actor_id` (user thật, không
  null), `actor_role` (`EMPLOYER|ADMIN|STAFF`), `action`
  (`SUBMITTED|RESUBMITTED|APPROVED|REJECTED`), `note` (lý do khi REJECTED, null khi khác),
  `created_at`. Ghi bản ghi tại: submit (SUBMITTED), resubmit (RESUBMITTED — submit từ
  `DRAFT` sau khi đã từng bị từ chối), review duyệt/từ chối (APPROVED/REJECTED).
- **FR-004** `PATCH /api/v1/jobs/{id}/review` giữ nguyên hợp đồng 003 (`approved`, `reason`
  bắt buộc khi từ chối) — thêm: ghi record lịch sử + set `decidedAt`. Từ chối = "yêu cầu
  sửa và gửi lại" của mockup: tin về `DRAFT` kèm `reviewNote`, NTD sửa được và submit lại
  được; **không** tách trạng thái `REJECTED` thứ ba (vòng đời 003 không có, thêm vào là
  đổi hợp đồng FE đang chạy).
- **FR-005** Cột mới trên `job_descriptions`, đều nullable để ddl-auto thêm không phá dữ liệu:
  `submitted_at` (set khi submit/resubmit, reset khi bị từ chối), `decided_at` (set khi
  review), `ai_safety_score` (Integer 0–100, **không có writer ở kỳ này** — chỉ là field
  lưu sẵn cho FE render, xem Out of scope).
- **FR-006** `GET /api/v1/admin/jobs/moderation/history` — trang bản ghi quyết định
  (`APPROVED|REJECTED`), lọc `decision`, `from`, `to`, `moderatorId`, `search` (tiêu đề/
  mã tin/tên công ty), `page/size≤100`. Mỗi dòng: jobId, tiêu đề, công ty, NTD (tên+email),
  quyết định, người duyệt (id+email), ghi chú/lý do, thời gian xử lý (giây, tính
  `record.created_at − job.submitted_at`, null khi thiếu mốc), `created_at`.
- **FR-007** `GET /api/v1/admin/jobs/moderation/history/stats` — `{totalDecisions, approved,
  rejected, avgProcessingSeconds}` đếm từ bảng record (không suy từ `totalElements` một
  trang), `avgProcessingSeconds` bỏ qua bản ghi thiếu mốc `submitted_at`.
- **FR-008** Console nằm dưới `/api/v1/admin/**` ⇒ chỉ `ROLE_ADMIN` (URL rule có sẵn).
  `PATCH /{id}/review` giữ nguyên `hasAnyRole('ADMIN','STAFF')` của 003 ⇒ STAFF vẫn duyệt
  được qua endpoint cũ nhưng không vào được console — hạn chế chấp nhận, ghi rõ.
- **FR-009** Không tổ hợp input nào (id lạ, status lạ, ngày đảo chiều, search chứa `%`)
  trả 500; mọi endpoint mới 403 với STAFF, 401 với anonymous.

## Out of scope (cắt có chủ đích, chốt 2026-10-20)

- **Điểm AI pre-screening thật** (score 0–100, checkpoint pass/warning, bộ câu hỏi gợi ý):
  BE không gọi AI nào; `ai_safety_score` chỉ là cột lưu sẵn, không có writer — FE render
  "chưa có đánh giá" khi null.
- **Cờ ưu tiên** (`Khách hàng VIP`, `Nghi vấn Spam`, `Cảnh báo dải lương`, `Khẩn cấp`),
  ngành nghề taxonomy, mức lương có cấu trúc trong queue filter: JobDescription không có
  nguồn dữ liệu cho các khái niệm này.
- **Điểm tín nhiệm NTD** (trustScore), "Doanh nghiệp đã xác minh", `Enterprise Tier`,
  "42 tin / 0 vi phạm": không có entity Employer; đếm tin/vi phạm theo NTD có thể làm sau
  nếu anh cần.
- **Phê duyệt hàng loạt** (nút "Phê duyệt hàng loạt (3)"): mỗi quyết định là một dòng
  history + một dòng audit — bulk làm biến dạng ý nghĩa lịch sử; FE gọi loop như 002 đã
  làm với bulk user.
- **Email thông báo kết quả duyệt cho NTD**: màn 18.1 có nhắc, nhưng thêm template mail
  mới + luồng fail (502/rollback hay best-effort?) là bài toán riêng — để kỳ sau.
- **Lý do từ chối dạng checklist** (5 ô checkbox): giữ `reason` là một chuỗi tự do như 003
  đã chốt; FE tự ghép các ô chọn + chi tiết vào một chuỗi.
- **Xuất báo cáo vi phạm / Excel** ở màn history: CSV export có thể thêm sau theo mẫu 002
  (B5) nếu anh cần; không nằm ở kỳ này.

## Acceptance criteria (đo được)

- AC-1: queue với `status=PENDING_APPROVAL` trả đúng tập tin đang `PENDING_APPROVAL` trong DB
  (đối chiếu SQL); `totalElements` khớp khi lọc 3 trạng thái khác nhau.
- AC-2: submit một JD ⇒ xuất hiện record `SUBMITTED` (actor = NTD); sau khi review từ chối ⇒
  record `REJECTED` kèm lý do + tin về `DRAFT` + `reviewNote` + `decidedAt` khác null;
  submit lần nữa ⇒ record `RESUBMITTED`.
- AC-3: timeline ở endpoint chi tiết liệt kê đúng các record trên, mới nhất lên đầu.
- AC-4: history lọc `decision=APPROVED` chỉ trả bản ghi duyệt; stats khớp đếm SQL thủ công
  (total/approved/rejected/avgProcessingSeconds) trên cùng dữ liệu.
- AC-5: anonymous ⇒ 401, STAFF ⇒ 403 cho cả 4 endpoint console (queue, detail, history,
  stats); `PATCH /{id}/review` vẫn 200 với STAFF (hồi quy 003).
- AC-6: không 500 trên ma trận input (id không tồn tại, `status=FOO` ⇒ 400 kèm allowedValues,
  search `%`, ngày from>to).
