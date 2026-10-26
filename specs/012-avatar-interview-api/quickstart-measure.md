# Quickstart đo — spec 012: hợp đồng BE cho luồng avatar (mode AVATAR)

## Cách đo

```bash
# Integration full-loop trên MySQL thật (Testcontainers mysql:8.0) — 12 test
mvn -B -o test -Dtest='AvatarInterviewApiIntegrationTest'
```

Lệnh thực tế đã dùng (Windows/Git Bash, cùng khuôn 011):

```bash
MSYS_NO_PATHCONV=1 docker run --rm \
  --add-host=host.docker.internal:host-gateway \
  -v "$(pwd -W)":/app -v m2-ai-be:/root/.m2 -v /var/run/docker.sock:/var/run/docker.sock \
  -e DOCKER_HOST=unix:///var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -e TESTCONTAINERS_RYUK_DISABLED=true \
  -e MAVEN_OPTS="-Xmx768m -XX:MaxMetaspaceSize=384m" \
  -w /app maven:3.9-eclipse-temurin-17 \
  mvn -B -o test -Dtest='AvatarInterviewApiIntegrationTest'
```

## Kết quả đo 2026-10-26

- `AvatarInterviewApiIntegrationTest`: **12/12** xanh, 0 fail, 0 error — `93.65 s` ở lượt đầu,
  `67.20 s` ở lượt chạy lại sau khi thêm case trần `strengths`. Hai lượt đều `BUILD SUCCESS` (exit 0).
- Hồi quy toàn repo: **236/236** xanh, 0 fail 0 error, 22/22 class. VM Docker chỉ 4GB và stack
  compose (backend + mysql) đang chạy liên tục 3 giờ, nên suite tách 4 lô thay vì một lượt
  `clean test` — hai lần trước chạy trọn lượt cạnh stack là chết Docker Desktop (exit 137):

  | Lô | Class | Tests |
  |---|---|---|
  | 012 | `AvatarInterviewApiIntegrationTest` | 12 |
  | A | `LogoutWithdrawDelete` 6, `GlobalExceptionHandler` 3, `PersistenceTest` 13, `ChangePassword` 3, `UserSessionAndAvatar` 8, `RegisterRequestValidation` 2, `TwoFactorIntegration` 8 | 43 |
  | B | `AdminDashboard` 4, `AdminDataCompletion` 14, `AdminPermissionMatrix` 5, `AdminSettings` 7, `ApplicationSchedule` 7, `ApplicationWorkflow` 9, `JobController` 16, `JobModerationConsole` 9, + 6 class unit (29) | 110 |
  | C | `AdminUserControllerIntegrationTest` | 71 |

  `target/classes` và `target/test-classes` được kiểm tra không có class ma (mỗi `.class` đều có
  source tương ứng) trước khi chạy, nên không cần `clean` mà vẫn không dính fail giả bài học 010.

## 11 kịch bản của spec + 1 kịch bản thêm, số đo thật

| # | Kịch bản | Kết quả |
|---|---|---|
| 1 | `start` AVATAR thiếu `jobId` → 400; hợp lệ → `IN_PROGRESS`, `questions` rỗng, `currentQuestionIndex=0` | xanh — kèm đo `interview_session_questions` có **0 dòng** (không phải `[null]`) và 400 thì không lưu session |
| 2 | ba lần `confirm=false` cùng lượt → một dòng answer, index vẫn 0 | xanh — `answer_text` lần đọc cuối là bản nháp 2, `confirmed_at is null` |
| 3 | `confirm=true` → index sang 1; ghi lại lượt 0 → 400 "Lượt này đã được chốt" | xanh — đo thêm: nháp lại lượt đã chốt cũng 400, request bị từ chối không dịch con trỏ và không thêm dòng |
| 4 | `questionText` 2001 ký tự → 400; `answerText` 10001 → 400; cột không đổi | xanh — kèm chiều ngược lại: đúng trần 2000/10000 thì 200, chứng minh 400 ở trên là do trần |
| 5 | `questionText` gửi vào phiên MOCK/KNOWLEDGE → 400 "Chế độ này không nhận câu hỏi từ client" | xanh — và đường cũ của KNOWLEDGE (chỉ `answerText`) vẫn 200, index vẫn nhích |
| 6 | phiên của ứng viên khác: GET / answer / evaluation / finish → 403 | xanh — 403 chứ không 404, transcript vẫn 0 dòng, session vẫn `IN_PROGRESS` |
| 7 | rubric đúng → report có, tổng = trung bình 5 chiều; body gửi `overallScore=999` vẫn ra 74 | xanh — `(80+70+75+60+85)/5 = 74`; kiểm tra cả dòng `interview_report` bằng SQL; body nhét thêm `audioBase64` bị bỏ qua vô điều kiện; nộp lần hai → 400, vẫn 1 dòng report |
| 8 | rubric thiếu chiều / 101 / 75.5 / thừa `ngoaiHinh` / -1 / thiếu `rubricVersion` / `strengths` 4001 ký tự / nộp khi chưa finish → 400 | xanh — message nêu đích danh chiều sai (`thiếu chiều [duAn]`, `không phải số nguyên 0..100 [tuDuy]`, `chiều không có trong thang chấm [ngoaiHinh]`); `interview_report` kết thúc ở **0 dòng**; đúng trần 4000 thì 200 và có đúng 1 dòng |
| 9 | lượt thứ 41 → 400 "Phiên đã đạt số lượt tối đa, hãy kết thúc" | xanh — 40 lượt đầu đều 200, `interview_answers` vẫn **40 dòng**, index vẫn 40; **nháp** cũng bị trần chặn; finish giữa chừng vẫn được và trả đủ 40 lượt transcript |
| 10 | INTERVIEWER / ADMIN / ứng viên khác gọi `GET /{id}/report` → 403 | xanh — chính chủ đọc được 200 với `overallScore=74` |
| 11 | cả luồng avatar không đổi bản ghi `applications` / `interview_slots` | xanh — so **nguyên hàng** `select *` trước/sau bằng `queryForMap`, kể cả `updated_at`; `overallScore` của session vẫn `null` (không phải 0 giả) |
| 12 | (thêm) `DELETE /{id}` phiên đã có report | xanh — `interview_report` là bảng mới có FK `session_id`, không xóa trước thì endpoint chết 500; sau khi xóa: 0 answer, 0 report, GET → 404 |

## Hợp đồng đã đổi so với nền cũ (báo FE/AI)

1. **`ownSession` trả 403 thay vì 400.** mọi path `/api/v1/mock-interviews/{id}*` khi id không phải
   của mình giờ về 403 + thông điệp tiếng Việt cũ ("Bạn không có quyền truy cập phiên phỏng vấn này").
   Lý do: spec 012 ràng buộc "id của ứng viên khác → 403, không để lộ sự tồn tại của phiên bằng 404".
   `LogoutWithdrawDeleteIntegrationTest` MOCK-2 đã đổi assertion 400 → 403 theo.
   FE đọc lỗi bằng `e.response?.data?.message` nên không vỡ màn nào.
2. **`POST /{id}/answer` nhận thêm `questionIndex` (optional).** spec chỉ ghi bốn field
   `{questionText, answerText, confirm, durationSec}`, nhưng nếu không có ô chỉ lượt thì test 3
   ("confirm lại lượt 0 → 400") không tới được code, và một request confirm bị timeout sẽ bị FE
   retry thành **lượt mới**. Quy ước: `questionIndex` null = lượt hiện tại; nhỏ hơn hiện tại = 400
   "Lượt này đã được chốt"; lớn hơn hiện tại = 400 "Không ghi được vào lượt chưa tới".
   `confirm` null hiểu là `true` để MOCK/KNOWLEDGE cũ không đổi hành vi.
3. **Rubric đặt trong object `rubric`**, không trải thẳng lên body:
   `{"rubric": {"chuyenMon":80,...,"mucKhop":85}, "rubricVersion":"2026-10-v1", "strengths":"...", "improvements":"..."}`.
   Để là map thay vì 5 field để BE phân biệt thiếu / thừa / không nguyên và nêu tên chiều trong message 400.
4. **`overallScore` client gửi bị bỏ qua thật sự**: DTO không khai báo field đó, Jackson của Spring Boot
   mặc định bỏ property lạ (`Map` rubric vẫn đọc được vì cần giữ key động). Đã đo bằng cách nhét
   `overallScore=999` và `audioBase64` vào body.
5. **Phiên AVATAR sau `finish` có `overallScore = null`**, không phải 0. Điểm nằm ở `interview_report`.
   Đây là lựa chọn có chủ đích: `InterviewSessionRepository.avgOverallScore()` và `avgOverallScoreOf()`
   chỉ tính dòng có điểm, nên phiên avatar không kéo trung bình admin console xuống như một con số 0 giả.
6. **`GET /{id}` thêm mảng `transcript`** (additive): `[{index, questionText, answerText, confirmed, durationSec, answeredAt}]`.
   `answers` giữ nguyên cho MOCK/KNOWLEDGE.
7. **Trần độ dài chặn ngay ở DTO** cho cả bốn ô text: `questionText` 2000, `answerText` 10000
   (đúng biên cột), `strengths` / `improvements` 4000. Hai ô cuối là cột TEXT nên không đặt trần thì
   payload quá dài chết ở DB và trả 500 thay vì 400.

## Chưa đo / còn mở

- **FE chưa đổi theo hợp đồng.** `models/Interview.model.ts` vẫn chỉ có `'MOCK' | 'KNOWLEDGE'`,
  `interview.service.ts` chưa có call-site cho `AVATAR`, `/evaluation`, `/report`; chưa có WebRTC,
  chưa có màn ghi âm. BE đã sẵn hợp đồng, FE dựng màn avatar là wave kế.
- **Chưa có LLM provider.** `pom.xml`/`application.yml` BE không có khóa nào; Python service cũng
  chưa chốt nhà cung cấp. `InterviewAiEngine` vẫn rule-based, tức câu hỏi avatar trong luồng thật
  do phía Python sinh — spec 012 không đổi điều đó.
- Chưa đo trên Render staging (deploy theo superrepo sau khi merge `dev`).
- Chưa đo hiệu năng khi relay 40 lượt qua mạng thật; test chạy MockMvc in-process.
- `POST /{id}/evaluation` hiện **chỉ chính chủ** nộp (Phạm vi 6). Nếu sau này muốn INTERVIEWER nộp
  rubric cho phiên avatar của ứng viên, phải mở quyền và đo lại test 10.
