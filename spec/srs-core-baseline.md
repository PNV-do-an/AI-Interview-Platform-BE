# SRS Core Baseline — yêu cầu <-> code thật

Nguồn: `D:\SRS_AI_Interview_Platform_Core_Baseline.docx` (Software Requirements Specification, bản dự thảo 1.0, phạm vi "Core Baseline" giữa Candidate / Employer-Mentor / Admin). SRS đó tổng hợp từ hai tài liệu gốc: "Các chức năng chính của INTERVIEW PLATFORM.txt" và "File document về user story của toàn web.docx" (Product Backlog v1.1).

Doc này không chép lại SRS để lưu trữ: mỗi mã FR/NFR được đối chiếu với code đang có trong repo, kèm bằng chứng, để biết Core Baseline còn thiếu chỗ nào. Mã FR là mã biên tập của SRS, không phải mã Jira.

Đường dẫn code rút gọn: backend tính từ `backend/src/main/java/com/aiinterview/platform/`, frontend tính từ `frontend/src/`.

Mức kiểm chứng:
- `DO` = đã đo bằng curl/DOM trên code production đang chạy.
- `CODE` = đã đọc code tới nơi (kèm file:line), **chưa** đo runtime.
- `MOT PHAN` = có một phần, phần còn thiếu ghi rõ.
- `KHONG` = không có trong code.

## 1. Vai trò: SRS nói 3, code có 4

| SRS | Role thật (`model/enums/Role.java`) | Ghi chú |
|---|---|---|
| Candidate | `ROLE_USER` | `App.tsx` gate `/my-applications`, `/my-profile`, `/mock-interviews*`, `/cv-analyzer`, `/cover-letters`, `/device-check` cho `ROLE_USER` |
| Employer/Mentor | `ROLE_INTERVIEWER` | gate `/jobs/new`, `/jobs/:id/edit`, `/my-jobs`, `/my-slots*`, `/packages`, `/my-orders`; `controller/JobController.java:42`, `controller/ApplicationController.java:56` dùng `hasAnyRole('INTERVIEWER','ADMIN','STAFF')` |
| Admin | `ROLE_ADMIN` | `controller/AdminUserController.java:19` `@PreAuthorize` cấp class; `/admin/users` admin-only |
| (SRS không chốt) | `ROLE_STAFF` | **đã tồn tại trong code** và đang có quyền như Employer ở JD/applications + được vào `/admin/job-approvals|rubrics|analytics` (`App.tsx:102-104`) |

Kết luận cho Q-01/Q-07/Q-12: repo hiện đối xử Employer và Mentor là **một** role (`ROLE_INTERVIEWER`), và Staff **đã** là role riêng có quyền rộng. SRS viết "Customer/User/Client/Staff" lộn xộn — câu hỏi thật bây giờ không còn "có Staff hay không" mà là "Staff có được giữ quyền xem file ghi âm/hồ sơ khách hay không" (Core Baseline không mô tả chức năng này, code cũng chưa có).

## 2. FR -> code

### 2.1 Authentication & User Management

| Mã | Yêu cầu SRS | Trạng thái | Bằng chứng / khoảng cách |
|---|---|---|---|
| FR-AUTH-01 | Đăng ký + đăng nhập | `DO` | `POST /api/v1/auth/register` 201/400/409, `POST /login` 200, `GET /verify`, `POST /resend-verification`; OAuth Google/GitHub/SSO có thêm ngoài SRS. Chi tiết + đo: `features/sign-up.md` |
| FR-AUTH-01 (mở rộng) | Quên mật khẩu / đổi mật khẩu | `DO` | `POST /forgot-password`, `GET|POST /reset-password`, thu hồi phiên qua `POST /refresh`. Chi tiết: `features/password-reset.md` |
| — | Xác thực email **bắt buộc trước khi đăng nhập** | `KHONG` (chủ đích) | Tài khoản auto-enabled; `MAIL_PASSWORD` rỗng ở mọi env nên chặn là không ai đăng ký được. Quyết định đã chốt, xem mục 8 Q-01 |
| FR-AUTH-02 | RBAC, sai quyền -> 403 | `CODE` | `@PreAuthorize` cấp method/class như mục 1; `controller/SlotController.java`, `ApplicationController.java:50` `hasRole('USER')` |
| FR-AUTH-03 | Admin xem/tìm/lock/unlock tài khoản | `CODE` | `controller/AdminUserController.java`: `GET /api/v1/admin/users?page&size&search&status&role` (:26), `GET /users/{id}` (:38), `PATCH /users/{id}/lock|unlock` (:44,:50), `PATCH /users/{id}/role` (:70), `DELETE /users/{id}` (:56), `POST /users/{id}/reset-password` (:63), `GET /audit-logs` (:79). Lỗ hổng UI đã ghi: `known-gaps.md` (audit log hiện `targetUserId` thô, bộ lọc thiếu `DELETED`) |
| FR-AUTH-04 | Hồ sơ cá nhân + **tải lên CV** | `MOT PHAN` | Hồ sơ: `GET|PUT /api/v1/candidate-profile` (`controller/CandidateProfileController.java:20,27`). CV: **không có multipart** (0 kết quả `MultipartFile` toàn backend) — `POST /api/v1/cv/analyze` nhận `cvText` JSON (`model/dto/request/CvAnalyzeRequest.java:10-12`), `CandidateProfile.cvFileName` là chuỗi tự gõ |

### 2.2 Quản lý JD/chương trình

| Mã | Yêu cầu SRS | Trạng thái | Bằng chứng / khoảng cách |
|---|---|---|---|
| FR-JD-01 | Tạo/sửa JD, đóng/mở tuyển | `CODE` | `controller/JobController.java`: `POST /api/v1/jobs` (:51), `PUT /jobs/{id}` (:58), `PATCH /jobs/{id}/status` nhận `JobStatus` (:66), `DELETE` (:74), `GET /jobs/my-jobs` (:42) |
| FR-JD-02 | Candidate xem JD công khai | `CODE` | `GET /api/v1/jobs?page&size&search` (:27), `GET /jobs/{id}` (:36). Chỉ có `search` — **không** có lọc theo ngành/kỹ năng/địa điểm mà SRS để ngỏ |
| FR-JD-03 | **Admin phê duyệt / từ chối JD trước khi công khai** | `KHONG` | `model/enums/JobStatus.java` chỉ có `OPEN`, `CLOSED` — không có `PENDING/APPROVED/REJECTED`, không có cột duyệt/lý do từ chối/người duyệt/lịch sử duyệt. `frontend/views/pages/JobApprovalPage.tsx:3` tự khai "Static screen ported from the Stitch mockup", không gọi API. **Đây là điểm đứt duy nhất làm luồng 5.1 không chạy được** |
| FR-JD-04 | Gợi ý ứng viên phù hợp JD | `CODE` | `GET /api/v1/jobs/{id}/suggestions` (`JobController.java:82`) -> `service/impl/MatchingServiceImpl.java:33-70` (điểm chồng kỹ năng). Thuật toán matching SRS nói "chưa xác định" — code đang dùng overlap kỹ năng, cần chốt có chấp nhận mức này không |

### 2.3 Ứng tuyển và theo dõi trạng thái

| Mã | Yêu cầu SRS | Trạng thái | Bằng chứng / khoảng cách |
|---|---|---|---|
| FR-APP-01 | Nộp đơn vào JD | `CODE` | `POST /api/v1/applications/jobs/{jobId}` (`ApplicationController.java:24`) |
| FR-APP-02 | Theo dõi tiến độ | `CODE` | `GET /applications/my` (:32), `PATCH /applications/{id}/cancel` (:41). `model/enums/ApplicationStatus.java`: `PENDING, SCHEDULED, INTERVIEWED, ACCEPTED, REJECTED, CANCELLED` — phủ đủ 4 trạng thái SRS nêu (Chờ duyệt/Hẹn lịch/Đã phỏng vấn/Kết quả) và thừa 2 trạng thái quyết định cuối |
| FR-APP-03 | Employer xem ứng viên đã ứng tuyển | `CODE` | `GET /applications/jobs/{jobId}` (:56, phân quyền INTERVIEWER/ADMIN/STAFF) |

### 2.4 Lịch và phỏng vấn

| Mã | Yêu cầu SRS | Trạng thái | Bằng chứng / khoảng cách |
|---|---|---|---|
| FR-SCH-01 | Employer cấu hình khung giờ rảnh | `CODE` | `controller/SlotController.java`: `GET /api/v1/slots/my` (:23), `GET /slots/available?employerId` (:30), `POST` (:37), `PUT /{id}` (:44), `DELETE /{id}` (:52). Bug lệch giờ đã sửa (xem `known-gaps.md`); **rủi ro còn mở**: backend vẫn so `start.isBefore(LocalDateTime.now())` bằng đồng hồ UTC của container |
| FR-SCH-02 | Candidate chọn khung giờ đặt lịch | `CODE` | `POST /api/v1/applications/book-slot` (`ApplicationController.java:49`, `hasRole('USER')`). SRS hỏi "quy tắc xác nhận/đổi/hủy": hiện chỉ có **hủy đơn** (`PATCH /applications/{id}/cancel`) — **không** có endpoint đổi lịch hay hủy slot riêng (chỉ `cancelled` flag trên `InterviewSlot.java:40`) |
| FR-SCH-03 | Thông báo / lịch nhắc | `MOT PHAN` | `service/EmailService.java` có 11 loại email, gồm `sendApplicationStatusEmail`, `sendSlotBookedEmail`, `sendAccountLocked/Unlocked/DeletedEmail` — gửi **đồng bộ khi có hành động**, không có job định kỳ (0 `@Scheduled`), **không** có entity `Notification`, **không** real-time/WebSocket |
| FR-INT-01 | Phiên AI Interview: câu hỏi -> trả lời -> phản hồi | `CODE` | `controller/MockInterviewController.java`: `POST /api/v1/mock-interviews` (:29), `POST /{id}/answer` (:56), `POST /{id}/finish` (:66), `GET /my` (:38), `GET /{id}` (:48). **Bỏ ngoài**: ghi âm/STT/TTS không có code (câu trả lời là text qua `SubmitAnswerRequest`) |
| FR-INT-02 | Lịch sử + kết quả sơ bộ | `CODE` | `InterviewSession.overallScore` (`model/entity/InterviewSession.java:56`), `InterviewAnswer.score/feedback` (:37,:41) |
| FR-INT-03 | Employer nhập nhận xét + điểm sau phỏng vấn | `CODE` | `PATCH /api/v1/applications/{id}/evaluate` (`ApplicationController.java:66`); `Application.score` (:41), `Application.feedback` (:44). SRS chưa quy định thang điểm/tiêu chí -> repo hiện cũng **không** có bảng tiêu chí (mockup Rubric Studio là `STATIC`, `App.tsx:103` không có API) |

### 2.5 Quản trị và dashboard

| Mã | Yêu cầu SRS | Trạng thái | Bằng chứng / khoảng cách |
|---|---|---|---|
| FR-ADM-01 | Quản lý trạng thái tài khoản + quyền | `CODE` | như FR-AUTH-03 |
| FR-ADM-02 | Kiểm duyệt JD | `KHONG` | như FR-JD-03 |
| FR-ADM-03 | Dashboard thống kê người dùng / JD / lượt phỏng vấn | `KHONG` | không có endpoint aggregate nào; `frontend/views/pages/HiringAnalyticsPage.tsx:6` tự khai "No API calls". Số liệu hiện là bản copy từ mockup |

## 3. Luồng 5.1 (tạo JD -> phỏng vấn -> đánh giá) chạy tới đâu

| Bước SRS | Endpoint thật | Trạng thái |
|---|---|---|
| 1. Employer tạo JD | `POST /api/v1/jobs` | được |
| 2. Chuyển JD cho Admin kiểm duyệt | — | **đứt**: không có trạng thái chờ duyệt |
| 3. Admin duyệt/từ chối, chỉ JD duyệt mới công khai | `PATCH /jobs/{id}/status` (OPEN/CLOSED) | **đứt**: JD công khai ngay khi tạo, không có cổng duyệt |
| 4. Candidate xem JD + ứng tuyển | `GET /jobs`, `POST /applications/jobs/{jobId}` | được |
| 5. Theo dõi trạng thái / Employer xem ứng viên | `GET /applications/my`, `GET /applications/jobs/{jobId}` | được |
| 6. Employer mở khung giờ, Candidate đặt | `POST /slots`, `GET /slots/available`, `POST /applications/book-slot` | được (đổi lịch: thiếu) |
| 7. Phỏng vấn (AI cơ bản và/hoặc với người thật) | `POST /mock-interviews*` | AI text-based được; "buổi phỏng vấn với người thật" chỉ là trạng thái `INTERVIEWED`, không có công cụ họp |
| 8. Employer ghi nhận đánh giá, Candidate xem kết quả | `PATCH /applications/{id}/evaluate`, `GET /mock-interviews/{id}` | được |

Hệ quả: demo "luồng liên vai trò thông suốt" nghiệm thu được ở bước 1, 4-8; **không** nghiệm thu được bước 2-3 cho tới khi thêm trạng thái kiểm duyệt JD.

## 4. NFR

| Mã | SRS nói gì | Code hiện có |
|---|---|---|
| NFR-01 | Giới hạn chức năng theo vai trò, 403 khi sai quyền | `CODE` — `@PreAuthorize` rải ở `JobController`, `ApplicationController`, `SlotController`, `AdminUserController` (class-level) |
| NFR-02 | Dữ liệu tài khoản/hồ sơ quản lý theo quyền | `CODE`/một phần — phân quyền theo endpoint; **điểm yếu đã đo**: access token hạn 24h nên "đăng xuất mọi phiên" sau reset chỉ vô hiệu được refresh token (`known-gaps.md`, `features/password-reset.md` mục 9). Mật khẩu bcrypt, token reset UUID single-use, rate-limit theo cửa sổ (register 5 phút: ≥10/IP + ≥5/email; login 15 phút: ≥20/IP + ≥5/email; forgot-password 15 phút: ≥10/IP + ≥5/email, tính từ 2026-10-17) |
| NFR-03 | Ổn định phục vụ demo/nghiệm thu | `DO` một phần — luồng auth (đăng ký, đăng nhập email + OAuth, quên mật khẩu) đã đo trên staging; các luồng JD/application/slot/mock-interview mới ở mức `CODE`, chưa đo end-to-end |
| NFR-04 | Hiệu năng, scale, backup, logging, SLA: SRS nói "chưa đặc tả" | đồng ý với SRS: repo cũng chưa có. Logging chỉ là `log.info/warn` trong service, không có metrics/trace. Schema tiến hoá bằng `ddl-auto: update` (Flyway tắt) — xem `known-gaps.md` mục 1 |

## 5. Dữ liệu tổng quan -> entity thật

| Nhóm SRS | Entity thật | Thiếu so với SRS |
|---|---|---|
| User/Account | `model/entity/User.java` (id, name, email, role, enabled, createdAt, lockUntil, loginAttempts, resetToken, tokenVersion, deletedAt) | chưa chốt: trường bắt buộc nào, `phone` có bắt buộc không (mockup register bỏ phone) |
| Profile/CV | `CandidateProfile.java`, `CvDocument.java` | không có lưu file — `cvFileName` chỉ là text |
| JD/Program | `JobDescription.java` | **không có** trạng thái kiểm duyệt, người duyệt, lý do từ chối, lịch sử duyệt |
| Application | `Application.java` (status 6 giá trị, `coverLetter` text :37, `score` :41, `feedback` :44) | SRS hỏi "một ứng viên ứng tuyển nhiều lần được không" -> code không có unique (jobId, candidateId) được nêu ở doc này, chưa kiểm chứng ràng buộc |
| Availability/Appointment | `InterviewSlot.java` (`cancelled`, `bookedBy`) | không có quan hệ slot<->application ngược (khó suy ra lịch đã đặt của candidate), không có đổi lịch |
| Interview Session/Result | `InterviewSession.java`, `InterviewAnswer.java` | không lưu audio/transcript |
| Notification | — | **không có entity** |
| Audit | `AdminAuditLog.java` | UI hiển thị `targetUserId` thô |

## 6. Giao diện & tích hợp

| Hạng mục SRS | Reality trong repo |
|---|---|
| Khu vực theo 3 vai trò | có, qua `App.tsx` + sidebar lọc theo role (`known-gaps.md` mục quyết định) |
| Wireframe/UI | SRS nói "chưa có wireframe" — repo đã có 29 mockup Stitch, ánh xạ trong `INDEX.md`; đây là nguồn thiết kế đang dùng |
| Email | `EmailService` 11 loại; **SMTP chưa có credential thật** ở dev/staging -> mọi email nằm trong log, link reset/verify phải lấy từ log hoặc DB |
| Google Meet / gọi trực tiếp (Q-04) | 0 kết quả `M*eet` trong code backend -> chưa chọn phương án nào |
| Danh sách endpoint (SRS nói "chưa có") | 16 controller, prefix `/api/v1/*`; chi tiết ở mục 2 doc này |
| AI chấm điểm | rule-based (`CvAiEngine`, `MockInterviewServiceImpl`), không phải model thật (`known-gaps.md`) |

## 7. Tiêu chí nghiệm thu (mục 9 SRS) -> đạt chưa

| Tiêu chí | Trạng thái |
|---|---|
| Ba vai trò có khu vực chức năng đúng quyền | **đạt** (CODE) |
| Employer tạo/sửa JD; JD có trạng thái kiểm duyệt và không công khai trước khi duyệt | **không đạt** — nửa sau của vế (cổng duyệt) chưa tồn tại |
| Candidate xem JD công khai + gửi ứng tuyển + trạng thái được ghi nhận | đạt (CODE) |
| Employer mở khung giờ; Candidate chọn khung giờ đặt lịch | đạt (CODE); đổi lịch: chưa |
| Candidate hoàn thành AI Interview cơ bản + xem phản hồi | đạt ở mức text-based (CODE); STT/TTS ngoài phạm vi |
| Employer ghi nhận đánh giá sau phỏng vấn | đạt (CODE) |
| Admin quản lý tài khoản, khóa/mở, dashboard chỉ số cơ bản | khóa/mở đạt; **dashboard không đạt** (không có API) |
| Truy cập sai quyền bị từ chối | đạt (CODE) |
| Trình diễn được luồng tạo JD -> duyệt -> ứng tuyển -> phỏng vấn | **chặn ở bước duyệt JD** |

## 8. Q-01..Q-12 của SRS: code đã trả lời tới đâu, còn phải chốt gì

| ID | SRS hỏi | Code trả lời | Còn phải chốt (với anh Huy) |
|---|---|---|---|
| Q-01 | Tên/số vai trò | 4 enum `ROLE_USER/STAFF/ADMIN/INTERVIEWER`; UI map Candidate=USER, Employer=INTERVIEWER | giữ nguyên ngữ nghĩa hay đổi tên hiển thị; có xoá `ROLE_STAFF` khỏi quyền admin không |
| Q-02 | Phạm vi Core Baseline của sprint này | repo đã dựng 29 màn; `INDEX.md` đánh dấu `STATIC` cho màn không backend | xác nhận danh sách bắt buộc demo: CV upload, dashboard admin, job approval |
| Q-03 | AI Interview luồng nào trước | mock-interview theo text (POST -> answer -> finish), có `overallScore` | có lấy kết quả sơ bộ làm tiêu chí nghiệm thu, hay bắt buộc audio |
| Q-04 | Google Meet hay tự gọi | chưa có code cho cả hai | chọn một; nếu Meet: ai tạo, lưu ở column nào (`Application` chưa có field meeting link) |
| Q-05 | Trạng thái kiểm duyệt JD | **chưa có gì** | chốt bộ trạng thái + lý do từ chối + quy trình gửi lại -> kéo theo migration `job_descriptions` |
| Q-06 | Quy tắc chuyển trạng thái ứng tuyển / đặt-đổi-hủy lịch | 6 `ApplicationStatus`, chỉ có hủy đơn, không có đổi lịch | ai được chuyển trạng thái; đổi lịch có bắt buộc không |
| Q-07 | Employer vs Mentor | một role duy nhất `ROLE_INTERVIEWER` | tách hay gộp (tách = thêm enum + gate lại toàn bộ controller) |
| Q-08 | Kênh thông báo bắt buộc | email đồng bộ khi có hành động; không inbox, không push | có làm Notification entity + màn inbox trong baseline hay không |
| Q-09 | AI Interview vs phỏng vấn với người thật | hai đường rời rạc: `InterviewSession` (AI) và `Application.status=INTERVIEWED` (người thật), không liên kết | AI là bài luyện độc lập hay attached vào slot đã đặt |
| Q-10 | NFR chi tiết | chỉ có mật khẩu bcrypt, JWT 24h/7d, rate-limit cửa sổ, soft delete | ngưỡng chấp nhận được cho access token TTL (xem mục 9 doc này) |
| Q-11 | CV scanning vs Cover Letter | `POST /cv/analyze` (text) + `POST /cover-letters/generate` đều có code | đặt tên module; upload file thật có vào baseline không |
| Q-12 | Staff có phải role riêng | có `ROLE_STAFF`, đang được vào `/admin/job-approvals|rubrics|analytics` (`App.tsx:102-104`) | có thật sự dùng Staff trong demo, hay gộp về ADMIN |

## 9. Khoảng cách Core Baseline, xếp theo phụ thuộc

Không ghi ngày/ước lượng; thứ tự chỉ là phụ thuộc kỹ thuật.

1. **Cổng kiểm duyệt JD** (FR-JD-03, FR-ADM-02, bước 2-3 luồng 5.1, tiêu chí 2 và 9): thêm trạng thái `PENDING/APPROVED/REJECTED` vào `JobStatus` hoặc tách trường kiểm duyệt riêng trên `JobDescription` + `PATCH /api/v1/admin/jobs/{id}/approve|reject` (kèm lý do) + lọc `GET /jobs` theo JD đã duyệt + đổi `JobApprovalPage` từ `STATIC` sang `DATA`. Chặn mọi thứ phía sau: candidate chỉ được thấy JD đã duyệt. Lưu ý `ddl-auto: update` không đổi được enum đang có dữ liệu -> cần migration (xem `known-gaps.md` mục 1).
2. **Dashboard Admin** (FR-ADM-03, tiêu chí 7): cần endpoint aggregate đếm user/JD/lượt phỏng vấn. `HiringAnalyticsPage` đang hiển thị số bịa -> theo luật "chỉ hiện dữ liệu thật" thì hoặc làm API, hoặc để nguyên `STATIC` và nói rõ khi demo.
3. **Upload CV thật** (FR-AUTH-04, tiêu chí không nêu nhưng là chức năng nền): `MultipartFile` + storage + parse; hiện `cvFileName` chỉ là text nên màn `CvAnalyzerPage` không chứng minh được "tải CV".
4. **Đổi lịch / hủy slot** (FR-SCH-02): thêm endpoint hoặc mở rộng `PATCH /applications/{id}/cancel`; chốt quy tắc xung đột lịch (Q-06).
5. **Múi giờ slot** (rủi ro đã ghi ở `known-gaps.md`): backend đang so "quá khứ" bằng UTC trong khi user nhập giờ VN — phải đặt `TZ` container hoặc bỏ check phía server.
6. **Thu hồi phiên tức thời** (NFR-02): xem `features/password-reset.md` mục 9.
7. **SMTP thật** (nền cho FR-SCH-03 và luồng verify): chỉ là cấu hình `MAIL_*` ở môi trường, nhưng khi bật thì phải quyết lại "chặn đăng nhập khi chưa verify email" (`features/sign-up.md` mục 9).

## 10. Liên kết

- `INDEX.md`: 29 màn Stitch -> route -> file -> `DATA`/`STATIC`, kèm màn nào thiếu endpoint.
- `known-gaps.md`: lỗ hổng đã đo + các quyết định đã chốt (đọc trước khi hỏi lại).
- `features/sign-up.md`, `features/password-reset.md`: hai FR đã triển khai và đo xong.
- `conventions/frontend-ui.md`, `verification.md`: luật UI và cách verify bắt buộc.
