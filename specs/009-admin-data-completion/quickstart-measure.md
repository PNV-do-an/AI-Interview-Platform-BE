# Bộ đo 009 — Hoàn thiện dữ liệu cho trang admin (đo 2026-10-21)

## Môi trường đo

- Container `maven:3.9-eclipse-temurin-17` + Testcontainers `mysql:8.0` thật; gọi HTTP qua MockMvc,
  đối chiếu lại bằng `JdbcTemplate`/repository đếm trên đúng DB đó. Không test nào tin con số
  service tự tính mà không có câu đếm tương ứng.
- Seed 4 actor thật: admin (`ROLE_ADMIN`), employer (`ROLE_INTERVIEWER`), staff (`ROLE_STAFF`),
  candidate (`ROLE_USER`) — `user(email).roles(...)` để người ghi xuống bảng audit/moderation là
  người thật, không phải principal giả.
- Mốc thời gian trong test dùng số relative (−3h, −1d, +3d, `YearMonth.now().minusMonths(13)`)
  nên kết quả không phụ thuộc hôm nay là ngày nào.

## Kết quả

- Suite đầy đủ `mvn -B -o test` (2026-10-21, 13:32 min): **Tests run: 167, Failures: 0, Errors: 0** —
  BUILD SUCCESS. 167 = 154 của wave 001–008 (không đổi một assert nào) + 13 test mới của 009.
- Trong suite đó: `AdminDataCompletionIntegrationTest` **13/13** (80.56 s),
  `JobModerationConsoleIntegrationTest` **9/9** (44.50 s), `JobControllerIntegrationTest` **16/16**
  (47.43 s).
- Sửa `updateJob` (ghi lại điểm khi employer đổi nội dung) được viết sau khi suite gộp kịp
  compile ⇒ chạy lại ba class liên quan trong một lần `mvn -o test -Dtest=...`:
  **Tests run: 39, Failures: 0, Errors: 0** — BUILD SUCCESS (`AdminDataCompletionIntegrationTest`
  lên **14/14**, 98.99 s; `JobControllerIntegrationTest` 16/16, 53.24 s;
  `JobModerationConsoleIntegrationTest` 9/9, 52.64 s).

## AC → test

| AC | Test | Số đo được |
|---|---|---|
| AC-1 | `testTrends_TwelveMonths_MatchHandCounts` | `trends.months` đủ 12 mốc, phần tử cuối = tháng hiện tại, phần tử đầu = tháng thứ 12 ngược về trước. Tháng này: 2 mock / 2 formal; tháng trước: 1 / 1; tháng trống giữa chuỗi: 0 / 0 (FE vẽ liên tục). Phiên tạo cách đây 13 tháng vẫn còn trong DB nhưng không rơi vào bucket nào. Câu `COUNT(*) … DATE_FORMAT` đối khớp từng mốc |
| AC-2 | `testDurationAndLiveSessions` | Hai session hoàn thành 600 s và 1200 s ⇒ `avgDurationSeconds = 900`, khớp `AVG(TIMESTAMPDIFF(SECOND, created_at, completed_at))`; session `IN_PROGRESS` (không có `completed_at`) bị loại khỏi trung bình nhưng được `liveNow = 1` |
| AC-3 | `testUserDetail_LastLoginReadinessProfile` | 3 lần đăng nhập của candidate (2 thành công, 1 thất bại mới nhất) ⇒ `lastIp = 10.0.0.9`, tức dòng **thành công** gần nhất thắng dòng thất bại; đăng nhập của employer không lẫn vào. Điểm 80 và 90 của riêng candidate ⇒ `aiReadinessScore = 85` dù người khác có session điểm 10. Hồ sơ: `skills` tách 3 phần tử từ `"Java, Spring Boot,, React "` (rỗng bị bỏ), `experienceYears = 4`, `cvFileName = cv-huy.pdf`. User chưa đăng nhập/chưa có hồ sơ ⇒ cả ba field null |
| AC-3 | `testReadiness_NullWhenNoScoredSession` | Mọi session của user chưa chấm điểm ⇒ `aiReadinessScore = null`, không phải 0 |
| AC-4 | `testUserActivity_MergedAndPaged` | Ba nguồn (slot +3 ngày, mock session −1 ngày, đơn ứng tuyển −4 ngày) gộp thành `INTERVIEW_SLOT → MOCK_SESSION → APPLICATION`, `totalElements = 3`; `size=2` cắt còn 2 dòng rồi 1 dòng mà `totalElements` vẫn 3, `totalPages = 2` |
| AC-4 | `testUserActivity_InputGuards` | `size=101` ⇒ 400; `page=60&size=20` (vượt cửa sổ 1000 dòng) ⇒ 400 kèm message nêu "1000"; id lạ ⇒ 404; user xóa mềm vẫn 200 (đúng quy tắc tab Deleted của 002) |
| AC-5 | `testModerationQueueStats` | 4 job live (1 bản nháp thuần, 1 đang mở, 1 vừa duyệt, 1 bị trả về) ⇒ `pending=0, draft=2, approved=2, rejected=1, total=4`; đối chiếu `countLiveByStatus` + `countDistinctJobsByAction(REJECTED)` |
| AC-6 | `testEmployerSnapshot_TrustScore` | 2 approved + 1 rejected ⇒ `trustScore = 67` (= 100 × 2/3, làm tròn), kèm `jobs.total/approved/rejected/pending = 3/2/1/0` và email + ngày mở tài khoản NTD; NTD chưa từng bị duyệt/từ chối ⇒ `trustScore = null` chứ không mặc định 100 |
| AC-7 | `testPolicyScreener_ScoreStoredAndMatchesDetail` | Tin đủ chuẩn ⇒ ghi `ai_safety_score = 100` lúc nộp và `aiSafety.score` khi đọc **bằng đúng** giá trị lưu; tin "Bán hàng thị trường" mô tả 55 ký tự rò email ⇒ 4 thẻ đều fail, `score = 0`, note tiếng Việt nêu rõ "từ khóa" và "email" |
| AC-7 | `testPolicyScreener_LegacyJobWithoutStoredScore` | Tin nộp trước khi có 009 (cột null) ⇒ vẫn 200 với đủ 4 thẻ, `aiSafetyScore` trả null, không 500 |
| AC-7 | `testPolicyScreener_RescoreAfterEdit` | Tin từng bị từ chối (điểm lưu 0) → employer PUT nội dung mới ⇒ cột lưu viết lại thành 100, hàng chờ đọc lên 100/100. Không có test này thì admin duyệt theo điểm của bản nội dung cũ |
| AC-8 | `testAdminOnlyEndpoints_StaffForbidden` | `dashboard`, `categories`, `users/{id}/activity`: anonymous ⇒ 401, STAFF ⇒ 403 |
| AC-8 | `testConsole_Anonymous401_StaffReadsQueue` (class 004) | 5 endpoint hàng chờ (`queue`, `/{id}`, `/history`, `/history/stats`, `/stats`): STAFF ⇒ 200 thay vì 403 như trước 009; anonymous ⇒ 401; các endpoint admin khác vẫn 403 |
| AC-9 | `testCategories_MatchGroupBy` | 4 nhóm `department / experienceLevel / employmentType / workplaceModel`; `department` dẫn đầu là Engineering (2 job), Sales chỉ 1 vì job xóa mềm không đếm; chuỗi khoảng trắng và null bị loại (`countGroupedByDepartment()` trả đúng 2 nhóm); sắp theo số job giảm dần |
| AC-10 | `testEmptyDatabase_ReturnsZerosNotNulls` | DB trống ⇒ 12 tháng toàn 0, `avgDurationSeconds = null`, `liveNow = 0`, stats toàn 0, categories đủ 4 nhóm rỗng, không 500. Các số 005 giữ nguyên: `users.total`, `users.byRole.ROLE_ADMIN`, `jobs.byStatus.OPEN` |

## Sai lệch so với plan (có chủ đích)

- **005 từng chốt "dashboard không có chuỗi thời gian" — 009 đảo quyết định.** `trends` group
  trực tiếp trên `interview_sessions` và `interview_slots` bằng `DATE_FORMAT(col, '%Y-%m')`,
  không lập bảng aggregate và không job chạy nền. Đổi lại mỗi lần mở dashboard là hai câu
  GROUP BY trên toàn bảng; acceptable ở kích thước hiện tại, và là lý do biết trước nếu sau này
  cần bảng tổng hợp.
- **`ai_safety_score` không còn là cột trống.** 004 để ngỏ cột này vì chưa có AI engine. 009 đổ
  giá trị bằng `JdPolicyScreener`: 4 kiểm tra tất định, mỗi kiểm tra fail trừ 25 điểm,
  **không gọi mô hình nào** — tên field giữ `aiSafety*` theo hợp đồng FE. Vì mọi kiểm tra chỉ đọc
  dữ liệu đã lưu, điểm ghi lúc nộp và điểm tính lúc đọc luôn khớp (test assert equality), nên
  không có cảnh báo ma.
- **Sửa `updateJob` tái lập điểm được viết sau khi suite gộp kịp compile**, nên nó nằm ngoài 167;
  ba class liên quan chạy lại thành một lần riêng (39/39) để phủ.
- **Dòng thời gian hoạt động gộp in-memory**, cap `(page+1)*size ≤ 1000` ⇒ 400 khi vượt. Đây là
  giới hạn có chủ đích để một user có hàng nghìn sự kiện không kéo cả bảng vào bộ nhớ khi admin
  bấm sang trang cuối.
- **`rejected` trong `/stats` chồng lấn `draft`**: tin bị từ chối quay về bản nháp, nên nó vừa
  được tính ở `draft` (theo trạng thái thật) vừa được tính ở `rejected` (theo bản ghi quyết định).
  `total` vẫn là tổng 4 trạng thái live, không phải tổng 4 chip. Ghi rõ trong Javadoc DTO.
- **Mở quyền STAFF cho `/api/v1/admin/jobs/moderation/**`** là sửa một chỗ không nhất quán có
  thật: STAFF đã duyệt được tin (`PATCH /jobs/{id}/review`) nhưng bị 403 khi đọc chính hàng chờ.
  Test 004 từng assert 403 cho STAFF phải đổi thành 200 — đổi expectation, không nới assertion.
  Bảng quyền 006 không sai theo: module `jobs` đã khai STAFF có VIEW/APPROVE, còn
  `admin-console` vẫn ADMIN-only (đo lại bằng `testAdminOnlyEndpoints_StaffForbidden`).

## Chưa đo (nằm ngoài kỳ này)

- Chưa đo trên Render staging; deploy theo superrepo sau khi merge `dev`.
- Các khối FE vẫn đúng nguyên trạng "Khối demo" vì DB không có nguồn: uptime/latency/tiêu thụ
  token/sức khỏe phòng phỏng vấn, chip phiên bản, ngày sinh & nơi ở, avatar, 2FA, danh sách phiên
  đăng nhập đang mở, CRUD danh mục, và điểm an toàn do mô hình AI thật chấm.
- `exportUsers`/CSV và các hành động ghi (008) không đổi trong kỳ này, nên không đo lại.
