# Bộ đo 004 — Console kiểm duyệt JD (đo 2026-10-20)

## Môi trường đo

- Container maven + Testcontainers MySQL 8.0 thật, 4 endpoint console qua MockMvc
  (`GET /api/v1/admin/jobs/moderation` queue, `GET /{id}` detail,
  `GET /history`, `GET /history/stats`) + `PATCH /api/v1/jobs/{id}/submit|review`.
- Mỗi test đối chiếu cả HTTP lẫn SQL qua `JdbcTemplate`; thứ tự queue (`created_at DESC`) làm
  cho deterministic bằng cách già hóa `created_at` của từng job bằng raw SQL.
- Seed 3 actor thật: admin (`ROLE_ADMIN`), employer (`ROLE_INTERVIEWER`), staff (`ROLE_STAFF`)
  và gọi bằng `user(email).roles(...)` để actor ghi xuống `job_moderation_records` là người thật.

## Kết quả

Class `JobModerationConsoleIntegrationTest` — **9/9 pass**, 103.9s (lần chạy chốt 2026-10-20).

Suite đầy đủ `mvn -B -o test` (lần chạy gộp 21h45 2026-10-20): **Tests run: 154, Failures: 0, Errors: 15**.
Cả 15 error là của `JobControllerIntegrationTest.setUp` — xem mục "Sai lệch" vì chính bảng 004
gây ra. Sau khi sửa thứ tự xóa, chạy lại riêng class đó (22h53 cùng ngày) ⇒ **16/16 BUILD SUCCESS**,
tức **154/154**.

| AC | Test | Số đo được |
|---|---|---|
| AC-1 | `testQueue_MatchesDbStatuses` | Queue mặc định chỉ 2 job `PENDING_APPROVAL`, tin mới nhất trên đầu; lọc lại `status=OPEN` / `DRAFT` trả đúng 1 / 2 dòng. Số mỗi trạng thái khớp từng dòng của `countLiveByStatus()` (GROUP BY trên job chưa xóa mềm) |
| AC-1 | `testQueue_SearchFilters` | `search` khớp tiêu đề, khớp email employer, từ khóa lạ ⇒ 0 dòng; `size=1` ⇒ `totalPages=2` |
| AC-2 | `testLifecycle_RecordsAndTimeline` | employer submit ⇒ record `SUBMITTED` với `actorRole=EMPLOYER`; admin reject ⇒ `REJECTED` + `reviewNote` + `decidedAt`; employer nộp lại ⇒ `RESUBMITTED`. Timeline `GET /{id}` trả newest-first, đủ 3 bản ghi |
| AC-2 | `testLifecycle_Approve` | Duyệt ⇒ job `OPEN`, `reviewNote` biến mất khỏi response, timeline có `APPROVED` |
| AC-3 | `testHistory_AndStats` | History (chỉ bản ghi quyết định) không lọc ⇒ 2 dòng APPROVED/REJECTED; `decision=APPROVED` ⇒ 1 dòng có `moderatorEmail=admin@example.com`, `jobTitle`, `processingSeconds` là số; `moderatorId=employer` ⇒ 0 (người nộp không phải người duyệt), `moderatorId=admin` ⇒ 2; `search=Backend A` ⇒ 1; `from=to=hôm nay` ⇒ 2, `from=ngày mai` ⇒ 0. Stats: `totalDecisions=2, approved=1, rejected=1, avgProcessingSeconds` là số |
| AC-4 | `testHistory_InvalidInput` | `decision=SUBMITTED` ⇒ 400 "Lịch sử chỉ có hai loại quyết định: APPROVED hoặc REJECTED"; `decision=FOO` ⇒ 400; `from>to` ⇒ 400 |
| AC-6 | `testConsole_InputMatrix` | id lạ ⇒ 404; job đã xóa mềm ⇒ 404 và queue không còn nó; `status=FOO` ⇒ 400; `search=%` ⇒ 0 dòng (LIKE đã escape); `size=101` ⇒ 400 |
| AC-5 | `testConsole_Anonymous401_Staff403` | Cả 4 endpoint: anonymous ⇒ 401 "Yêu cầu đăng nhập", `ROLE_STAFF` ⇒ 403 "Bạn không có quyền truy cập chức năng quản trị" |
| AC-5 | `testReview_StaffStillAllowed` | Hồi quy 003: `PATCH /{id}/review` vẫn 200 cho STAFF, record ghi `actorRole=STAFF` |

## Sai lệch so với plan (có chủ đích)

- **Bảng mới có FK ⇒ test cũ của 003 sập.** `job_moderation_records.job_id` references
  `job_descriptions.id`, nên `JobControllerIntegrationTest.setUp` gọi `jobRepository.deleteAll()`
  là ăn `Cannot delete or update a parent row` (15/16 test error ở lần chạy gộp). Cách sửa: xóa
  `job_moderation_records` trước `job_descriptions` — production không ảnh hưởng vì job chỉ xóa mềm.
- **`processingSeconds` chụp lúc ghi, không tính lúc đọc.** Plan để tính khi đọc, nhưng nhánh từ
  chối xóa `submittedAt` trước khi kịp tính ⇒ dòng REJECTED luôn thiếu thời gian chờ.
  `JobServiceImpl.decide` nay lấy `submittedAt` vào biến *trước* khi rẽ nhánh rồi mới ghi xuống cột
  `processing_seconds`. Test `testHistory_AndStats` assert `isNumber()` cho cả dòng APPROVED lẫn
  REJECTED — đây là bug production do bộ test này phát hiện và sửa.
- **History là trang "bản ghi quyết định"** (FR-006): chỉ APPROVED/REJECTED. Các bản ghi
  SUBMITTED/RESUBMITTED của employer phục vụ timeline từng job (E2), không lẫn vào bảng lịch sử —
  nếu trộn thì `totalDecisions` và số liệu duyệt trung bình sẽ sai bản chất.
- `search` hỗ trợ cả `LIKE` trên `CAST(j.id AS string)` để admin dán mã tin (số id) vào ô tìm.

## Chưa đo (nằm ngoài kỳ này)

- `aiSafetyScore` chỉ là cột lưu, chưa có giá trị vì chưa có AI engine ghi — test không assert số
  này, và FE phải ẩn ô thay vì hiện 0.
- Chưa đo trên Render staging — deploy theo superrepo, sau merge `dev`.
