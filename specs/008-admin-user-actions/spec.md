# Feature Specification: Bổ sung hành động admin trên user — lý do khóa, bulk, tự đổi mật khẩu

**Feature Branch**: `feature/admin-console-004-008`
**Created**: 2026-10-20 · **Status**: Draft · **Input**: modal khóa tài khoản (màn 17 v1/v2,
màn 22 chi tiết), bulk action bar (màn 17 v2), card "Đổi mật khẩu" + "Thiết bị đăng nhập"
(màn 10 "Tài khoản của tôi"). Đây là phần **bổ sung** cho nền 001/002 đã build — không lặp
lại gì đã có.

## Why file 008

001/002 đã phủ danh sách/hành động đơn. Mockup mới hơn mockup của 002 ở 3 điểm: modal khóa
**bắt buộc lý do + chọn gửi mail**; thanh **bulk action** (khóa/mở khóa/đổi vai trò nhiều
dòng); và màn tài khoản cá nhân có **tự đổi mật khẩu**. Ba điểm này chưa có ở BE.

## User Stories

**US-01** Admin khóa một tài khoản phải nhập lý do; lý do lưu vào audit; nếu chọn "gửi
email thông báo" thì user nhận mail (đã có sẵn `sendAccountLockedEmail`).
**US-02** Admin không thể khóa tài khoản vai trò ADMIN (mockup: "Không thể khóa Super
Admin") — kể cả admin khác, kể cả chính mình (chính mình đã bị chặn từ 001).
**US-03** Admin tick nhiều dòng → khóa / mở khóa / đổi vai trò **hàng loạt** trong một
request; kết quả trả per-id (đạt/lỗi vì gì), hành động không đổ vỡ vì 1 id lỗi.
**US-04** User (mọi vai trò, trong đó có admin) tự đổi mật khẩu: phải nhập đúng mật khẩu
hiện tại; mật khẩu mới đạt 4 rule của màn (≥8 ký tự, hoa+thường, số, ký tự đặc biệt);
đổi xong mọi phiên đăng nhập cũ bị thu hồi (bump `tokenVersion` — cơ chế có sẵn).

## Functional Requirements

- **FR-001** `PATCH /api/v1/admin/users/{id}/lock` nhận **body tùy chọn**
  `{reason: string ≤500, notifyUser: bool mặc định true}`. Không body ⇒ hành vi hôm nay
  (khóa + mail) — tương thích ngược với FE đang chạy. Có body: `reason` ghi vào detail
  audit (đằng sau thông điệp cũ), `notifyUser=false` ⇒ **không** gửi mail khóa.
  Unlock giữ nguyên (mockup không có modal lý do cho mở khóa).
- **FR-002** Khóa tài khoản role ADMIN ⇒ 400 "Không thể khóa tài khoản quản trị viên"
  (FR-002 mới; áp cả bulk). Self-lock vẫn 400 như 001.
- **FR-003** `POST /api/v1/admin/users/bulk-lock` `{ids: [≤100], reason ≤500, notifyUser}` →
  `{requested, succeeded, failed:[{id, message}]}` — mỗi id xử lý độc lập trong cùng
  transaction logic; 1 id lỗi không cản id khác; từng id thành công vẫn ghi audit riêng
  (giữ hợp đồng "mỗi hành động là 1 dòng audit" của 001, dùng action `LOCK_USER` hiện có).
  `POST /users/bulk-unlock` `{ids}` tương tự (không cần reason).
  `PATCH /users/bulk-role` `{ids ≤100, role}` — id là chính admin gọi ⇒ fail riêng id đó
  ("không thể tự đổi vai trò của mình").
- **FR-004** `POST /api/v1/user/change-password` `{currentPassword, newPassword}` (mọi user
  đã đăng nhập): `currentPassword` sai ⇒ 400; `newPassword` vi phạm rule ⇒ 400 với message
  chỉ đúng rule fail; thành công ⇒ hash mới + `tokenVersion+1` + message nói rõ phải
  đăng nhập lại trên mọi thiết bị.
- **FR-005** Không tổ hợp input trả 500 (`ids` rỗng, `ids` > 100, id không tồn tại, role lạ,
  mật khẩu trống).

## Out of scope (không có nguồn / cắt có chủ đích)

- Danh sách "Thiết bị đăng nhập hoạt động" + nút thu hồi từng phiên (màn 10): JWT stateless,
  không có bảng phiên — chỉ thu hồi **tất cả** qua `tokenVersion`. Ghi vào known-gaps.
- Email cảnh báo bảo mật sau đổi mật khẩu: cần template mail mới — kỳ sau.
- Lý do mở khóa, quy trình kháng nghị ("Chờ kháng nghị: 5 hồ sơ"): BE không có luồng
  kháng nghị nào; mail khóa hiện có chưa đính hướng dẫn khiếu nại — nâng cấp nội dung mail
  là chuyện template, để sau.
- 2FA ("Cấp độ bảo mật: 2FA Đã kích hoạt" trong mockup): BE không có 2FA — mockup nói dối,
  không làm gì để theo nó.

## Acceptance criteria (đo được)

- AC-1: lock kèm `{reason:"vi phạm", notifyUser:false}` ⇒ audit chứa lý do, mail **không**
  đi (mock `EmailService` verify 0 lần gọi), tài khoản `locked=true`, phiên chết
  (`tokenVersion+1`).
- AC-2: lock không body ⇒ hành vi hôm nay nguyên vẹn (mail đi — hồi quy 001).
- AC-3: khóa user role ADMIN ⇒ 400; bulk-lock [validUser, adminUser, id-404] ⇒ succeeded=1,
  failed=2 với message riêng từng id; audit chỉ 1 dòng cho id thành công.
- AC-4: bulk-role [self, userThường] ⇒ self fail "không thể tự đổi", user thường đổi được;
  bulk 101 ids ⇒ 400 nêu trần.
- AC-5: change-password sai mật khẩu hiện tại ⇒ 400; mới yếu ("abc") ⇒ 400 nêu rule; đủ rule
  ⇒ 200, login bằng mật khẩu cũ ⇒ 401, bằng mới ⇒ 200, refresh token cũ ⇒ 401.
- AC-6: anonymous ⇒ 401 cho tất cả endpoint mới; STAFF ⇒ 403; `ids` rỗng/`role=FOO` ⇒ 400.
