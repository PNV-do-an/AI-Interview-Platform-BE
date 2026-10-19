# Feature Specification: Màn User Directory của admin — phần BE còn thiếu

**Feature Branch**: `feature/mange-user` (002 chưa tách branch riêng; 001 đang chờ anh duyệt commit)
**Created**: 2026-10-19 · **Status**: Draft · **Input**: mockup HTML "User Management & Access Control"
(Stitch/Gemini export, gửi ngày 2026-10-19) + spec đã giao `specs/001-admin-user-management/`

## Why file 002 chứ không sửa 001

`001-admin-user-management` đã build xong và **đã đo** (79/79 test, M-1..M-18). Mockup này không mô tả
lại 001 — nó mô tả một màn **rộng hơn** 001: KPI đếm số, mời tài khoản, xuất CSV, "Last Active",
tab Role Permissions, và hành động do **hệ thống** ghi audit. Đó là 6 khả năng mới, mỗi cái đều chạm
một quyết định đã chốt của 001 (R5 enum trạng thái, R14 "không làm", R10 "không có admin giả").
Sửa chồng lên 001 sẽ làm mất dấu thứ đã đo.

## User Stories

**US-01** Admin mở màn và nhìn thấy 4 con số tổng quan (tổng thành viên, đang hoạt động, chờ kích hoạt,
bị khóa/nghỉ) mà không phải tải hết danh sách.
**US-02** Tab bộ lọc có huy hiệu đếm ("All Members 3,420", "Pending Invites 24") — số đếm phải khớp với
kết quả danh sách cùng bộ lọc, kể cả khi đang search theo tên/email.
**US-03** Admin lọc được đúng nhóm "Pending Invite" (người đã được tạo tài khoản nhưng chưa bấm link verify).
**US-04** Mỗi dòng bảng hiển thị "Last Active" (lần cuối người đó dùng hệ thống) và "Joined Date".
**US-05** Admin bấm "Add User" / "Invite Members" → nhập họ tên + email + role → hệ thống tạo tài khoản
chưa kích hoạt và gửi email mời; người nhận bấm link mới đặt được mật khẩu và đăng nhập.
**US-06** Trên dòng đang "Pending Invite", admin bấm "Resend" (gửi lại mail mời) hoặc "Revoke" (hủy link
đã gửi, tài khoản vẫn còn).
**US-07** Admin bấm "Export CSV" / "Download CSV Export" → tải về đúng tập tài khoản đang nhìn thấy
(theo bộ lọc hiện hành), không phải toàn bộ bảng.
**US-08** Tab "Audit Log" render từ dữ liệu thật: ai thao tác, lên ai, kết quả, lý do nếu bị từ chối.
**US-09** Tab "Role Permissions" cho admin thấy role nào làm được gì (đọc được, không sửa được ở kỳ này).
**US-10** Nút "Edit" trên dòng: admin sửa được họ tên / email / số điện thoại của thành viên.
**US-11** Checkbox chọn nhiều dòng (mockup có, **không** có nút hành động hàng loạt nào trong mockup).

## Functional Requirements

- **FR-001** `GET` số liệu tổng quan trả ≥ 4 chỉ số: tổng tài khoản chưa xóa, theo trạng thái, chờ verify,
  bị khóa/không kích hoạt. Mỗi chỉ số là **một con số đếm từ DB**, không suy từ `Page.totalElements` của
  một trang dữ liệu.
- **FR-002** Mọi chỉ số/count phải **cùng bộ lọc** với danh sách: search + role + status cho ra cùng một tập
  dòng khi đếm và khi liệt kê. *(xem Clarify C-2.)*
- **FR-003** Danh mục trạng thái phải phân biệt được "chưa verify email" (Pending Invite) với
  "bị tắt/đóng băng" (Suspended) và "admin khóa" (Locked). 001 chỉ có `ACTIVE|INACTIVE|LOCKED|DELETED`.
- **FR-004** Mỗi dòng danh sách có mốc "lần cuối vào hệ thống" lấy được cho từng tài khoản, hiển thị dạng
  tương đối ("10 mins ago", "Yesterday", "Never") do FE định dạng. Theo C-1, mốc này là **lần đăng nhập
  thành công gần nhất** (suy từ `login_attempts`), **không** phải lần hoạt động gần nhất; tài liệu và nhãn
  FE phải nói đúng điều đó, không được ghi "Last Active" kiểu hoạt động thời gian thực.
- **FR-005** Admin tạo được tài khoản thành viên mới kèm role, ở trạng thái **chưa kích hoạt**, và hệ thống
  gửi email mời; email gửi hỏng ⇒ **502 + hoàn tác** (hợp đồng 001 R1, giữ nguyên).
- **FR-006** Gửi lại email mời cho một tài khoản đang chờ verify.
- **FR-007** Thu hồi link mời đang hiệu lực của một tài khoản (tài khoản vẫn tồn tại, vẫn chưa đăng nhập được).
- **FR-008** Xuất CSV theo cùng bộ lọc của danh sách, có **trần số dòng** để không kéo cả bảng, và ghi audit.
- **FR-009** Sửa hồ sơ thành viên (họ tên, email, số điện thoại) từ phía admin; đổi email phải giữ
  tính unique và không làm hỏng trạng thái verify.
- **FR-010** Đọc được ma trận "role → nhóm quyền" để render tab Role Permissions.
- **FR-011** Mọi thao tác ghi mới phải ghi audit với **admin người thật** (theo R10 của 001) và chặn lúc ghi
  (Constitution III), không phải để lưu rồi lộ ở GET.
- **FR-012** Không tổ hợp input nào (kể cả id lạ, role lạ, ngày lạ, search chứa `%`) được trả 500.

## Out of scope (đề xuất, chờ anh chốt — xem Clarify)

- RBAC động (ma trận quyền lưu DB, sửa qua UI):quyền hiện hard-code trong `JwtSecurityConfig` + `@PreAuthorize`;
  FR-010 chỉ yêu cầu **đọc**.
- Bulk actions thật ở BE (US-11): mockup **có** checkbox nhưng **không có** nút "Lock selected"/"Delete
  selected" → coi là đồ dựng hình; chọn phương án tối thiểu là FE gọi tuần tự từng endpoint đã có.
- Chuông thông báo + "Real-time updates": không có SSE/websocket; FE poll là đủ, không thêm endpoint mới.
- Mã thành viên dạng chuỗi ("USR-4091"): BE chỉ có `id` số; thêm cột `public_id` là đổi hợp đồng mọi endpoint.
- Hồ sơ candidate nâng cao (avatar, LinkedIn, GPA…) — thuộc `spec/known-gaps.md` mục "thiếu endpoint",
  không phải màn này.
- Job Approvals / Platform Analytics (2 tab khác trong sidebar): feature riêng, không nằm ở 002.

## Clarification — ĐÃ CHỐT 2026-10-19 (anh Huy trả lời 4/4 theo hướng tối thiểu)

- **C-1 "Last Active" + KPI "Active in the last 30 days"** = **suy từ `login_attempts`**, **không sửa schema**.
  Hệ quả phải ghi nhận thẳng vào tài liệu: con số này là "lần ĐĂNG NHẬP gần nhất", **không phải** "lần HOẠT ĐỘNG
  gần nhất" — một phiên đang chạy không tự cập nhật nó. Đổi email (B4) làm đứt chuỗi của user đó
  (`login_attempts` khóa theo email, không theo `user_id`). Nếu sau này anh cần đúng nghĩa "activity",
  phương án thêm `users.last_active_at` + cập nhật có giới hạn 5 phút vẫn còn mở, nhưng **không** làm kỳ này.
- **C-2 "Pending Invite"** = **`verification_token IS NOT NULL`** (chưa verify email). **Không** thêm cột
  `invited_by`, nên tab "Pending Invites" liệt kê **cả** user tự đăng ký chưa bấm link lẫn user admin mời —
  hai nguồn này không phân biệt được ở kỳ này.
- **C-3 Audit actor hệ thống** = **không cho hệ thống ghi audit**; R10 của 001 giữ nguyên
  (`admin_id NOT NULL`, hết giá trị giả `0`/`system`). **Anh đã nhận việc báo bỏ dòng
  "Account Suspended — Automated policy check" khỏi mockup.** BE từ chối mọi thiết kế cần dòng đó.
- **C-4 "Add User" không nhận mật khẩu**: tạo `enabled=false` + token + mail mời, user tự đặt mật khẩu qua
  link. BE không bao giờ thấy mật khẩu của user do admin tạo.

## Acceptance criteria (đo được)

- AC-1: số trên huy hiệu tab = `totalElements` của danh sách cùng bộ lọc (đo bằng curl, ≥3 bộ lệch nhau).
- AC-2: tạo user qua "Add User" ⇒ user `enabled=false`, chưa verify, mail mời đi thật; mail hỏng ⇒ 502 và
  DB **không còn** user vừa tạo (rollback), không có dòng audit thành công.
- AC-3: "Revoke" link mời ⇒ tài khoản vẫn còn trong danh sách, đăng nhập bằng mật khẩu cũ vẫn bị từ chối
  vì chưa verify, và gọi "Resend" được tiếp.
- AC-4: CSV export có bộ lọc ⇒ số dòng file = số dòng danh sách cùng bộ lọc; vượt trần ⇒ 400 nêu trần.
- AC-5: không có 500 ở bất kỳ ô nào của ma trận input (kế thừa ma trận 001, thêm các endpoint mới).
- AC-6: toàn bộ endpoint mới đều 403 với `ROLE_STAFF`, 401 với anonymous (kế thừa M-13 của 001).
