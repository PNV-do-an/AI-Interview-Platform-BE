# Spec 012 — Hợp đồng BE cho luồng phỏng vấn có avatar (mode AVATAR)

## Bối cảnh

Attachment 26/10 ("Implementation Plan — Open-Source Realistic AI Interviewer") vẽ hệ ba lớp:
React + WebRTC, Spring Boot business backend, Python AI service (LLM/STT/TTS) điều khiển
LiveTalking (https://github.com/lipku/LiveTalking) để sinh avatar nói có môi đồng bộ. Mục 9 của attachment
yêu cầu đọc repo trước và **không** viết lại kiến trúc cũ; mục 7 cho phép đổi tên endpoint theo
quy ước hiện hành ("Adapt them to the current project's conventions").

Anh Huy chốt 26/10: wave này **chỉ lấy phần hợp đồng BE**. Python AI service, LiveTalking, màn
avatar React, STT/TTS đều ở ngoài repo này. Nên tài liệu dưới đây là *hợp đồng dữ liệu + quyền*
để nhóm FE/AI song song triển khai, không phải kế hoạch dựng avatar.

Nền hiện có (đã đọc code 26/10, không phỏng đoán):

- `controller/MockInterviewController.java` — `POST /api/v1/mock-interviews`, `POST /{id}/answer`,
  `GET /{id}`, `POST /{id}/finish`, `GET /my`, `DELETE /{id}`. Quyền `hasRole('USER')`, chủ phiên
  kiểm tra thủ công trong service.
- `model/entity/InterviewSession.java` — `candidate`, `job` (nullable), `mode`, `topic`, `status`,
  `questions` là `@ElementCollection` bảng `interview_session_questions` (chuỗi, có `idx`),
  `currentQuestionIndex`, `overallScore`, `createdAt`, `completedAt`.
- `model/entity/InterviewAnswer.java` — một dòng cho mỗi lượt: `questionIndex`, `question` (2000),
  `answerText` (10000), `durationSec`, `score`, `feedback`, `answeredAt`.
- `model/enums/InterviewMode.java` = `{MOCK, KNOWLEDGE}`; `InterviewSessionStatus.java` =
  `{IN_PROGRESS, COMPLETED}`.
- `service/ai/InterviewAiEngine.java` là **rule-based** (đếm từ khóa, độ dài, có ví dụ). `pom.xml`
  và `application.yml` **không** có spring-ai / OpenAI / Anthropic, **không** có
  websocket / SSE / multipart cấu hình — đã grep, không khớp dòng nào.

Vì vậy BE kỳ này **không** gọi LLM, **không** nói chuyện với LiveTalking, **không** nhận audio.
BE chỉ làm đúng điều attachment mục 7 giao: *manage business data and authorization*.

## Phạm vi đã chốt

1. **Không mở đường dẫn mới song song.** Attachment gợi ý `/api/interviews*`; wave này không dùng,
   vì `interview_sessions` đã có chủ là `/api/v1/mock-interviews`. Hai bộ endpoint cùng ghi một bảng
   = hai nguồn sự thật. Luồng avatar đi vào **cùng** controller, phân nhánh theo `mode`.
2. **`InterviewMode` thêm giá trị `AVATAR`.** Cột lưu `EnumType.STRING` nên không migration;
   `ddl-auto: update`, Flyway đang tắt (`application.yml`) — bài học 011, SQL chỉ để tham chiếu.
3. **Luồng riêng, không gắn `applications` / `interview_slots`.** Phiên AVATAR không có cột FK tới
   đơn ứng tuyển, không đổi `ApplicationStatus`, không cần slot. Bối cảnh JD lấy qua `jobId` khi bắt
   đầu, snapshot vào session chứ không đọc ngược từ application.
4. **Chỉ một đường ghi hội thoại: JWT của chính ứng viên.** FE là phía gửi câu hỏi do AI service sinh
   ra và transcript do STT trả; BE không nhận write từ Python service. Lợi: không phải mở một biên
   tin cậy mới (không token dịch vụ, không allowlist IP), `SecurityUtils.getCurrentUser` +
   `ownSession` hiện hành chặn được ngay từ request đầu.
5. **Điểm là decision-support.** BE không tự đổi trạng thái tuyển dụng nào; người tuyển dụng quyết
   bằng `PATCH /api/v1/applications/{id}/evaluate` như cũ.
6. **Báo cáo avatar chỉ chính chủ đọc.** `GET /{id}/report` không mở cho INTERVIEWER/ADMIN/STAFF;
   `/{id}/employer` hiện có chỉ gom điểm phiên MOCK theo `applicationId` và không đụng phiên AVATAR.
7. **Phiên bỏ dở do FE chốt.** BE không có job tự kết thúc, không thêm trạng thái nào vào
   `InterviewSessionStatus`; ứng viên đóng tab thì session vẫn `IN_PROGRESS` với transcript đã chốt,
   mở lại hoặc `finish` muộn đều dùng được dữ liệu đó.

## Hợp đồng endpoint

| Method | Path | Quyền | Kết quả |
|---|---|---|---|
| POST | `/api/v1/mock-interviews` | ROLE_USER | `mode=AVATAR` + `jobId` hợp lệ → session `IN_PROGRESS`, `questions` **rỗng** (chưa sinh trước), `currentQuestionIndex=0` |
| POST | `/api/v1/mock-interviews/{id}/answer` | chủ phiên | thể `AVATAR`: `{questionText, answerText, confirm, durationSec}` — `confirm=false` upsert bản nháp của lượt hiện tại, `confirm=true` chốt lượt và `currentQuestionIndex+1` |
| GET | `/api/v1/mock-interviews/{id}` | chủ phiên | như hiện có, thêm `transcript`: mỗi lượt `{index, questionText, answerText, confirmed, durationSec, answeredAt}` |
| POST | `/api/v1/mock-interviews/{id}/finish` | chủ phiên | như hiện có; phiên AVATAR được finish khi còn đang giữa chừng (không buộc trả lời hết) |
| POST | `/api/v1/mock-interviews/{id}/evaluation` | chủ phiên | nộp rubric có cấu trúc một lần; hợp lệ → lưu báo cáo, BE tự tính điểm tổng |
| GET | `/api/v1/mock-interviews/{id}/report` | chủ phiên | báo cáo đã lưu; chưa có → 404 "Chưa có báo cáo cho phiên phỏng vấn này" |

`StartInterviewRequest` thêm nhánh: `mode=AVATAR` đòi `jobId`, bỏ qua `topic`; `questionCount` không
dùng cho AVATAR (số lượt do hội thoại quyết định, không cố định trước).

## Ràng buộc

- **Chặn ngay lúc ghi, không lưu rồi mới lộ khi đọc** (Hiến pháp III):
  - `questionText` 1..2000 ký tự, `answerText` 1..10000 ký tự — đúng biên cột `InterviewAnswer`;
    vượt → 400 nêu tên trường.
  - Gửi `questionText` cho phiên `MOCK`/`KNOWLEDGE` → 400 "Chế độ này không nhận câu hỏi từ client"
    — FE không được quyền đổi đề bài của luồng đã sinh câu hỏi sẵn.
  - `confirm=true` trên lượt đã chốt → 400 "Lượt này đã được chốt".
  - Lượt thứ 41 của một phiên → 400 "Phiên đã đạt số lượt tối đa, hãy kết thúc" — chốt trần chống
    nhồi hội thoại, vì mỗi lượt ghi thêm một dòng `interview_answers`.
  - `evaluation` chỉ nộp khi session `COMPLETED` → ngược lại 400 "Phiên chưa kết thúc".
  - Rubric: đúng 5 chiều `{chuyenMon, duAn, tuDuy, truyenDat, mucKhop}`, mỗi chiều integer 0..100;
    thiếu / thừa / ngoài khoảng / không nguyên → 400 kèm danh sách chiều sai. `rubricVersion` bắt buộc
    khác rỗng, để sau này đổi thang không đọc nhầm dữ liệu cũ.
  - **`overallScore` trong body bị bỏ qua**: BE tính trung bình cộng 5 chiều rồi làm tròn. FE/AI gửi
    tổng sai cũng không ghi được.
- **Nháp STT sửa được, không tạo bản sao:** upsert theo `(session, questionIndex)` — FE dán lại transcript
  sau khi ứng viên sửa lời thì ghi đè đúng lượt đó; `currentQuestionIndex` chỉ nhích khi `confirm=true`.
  `InterviewAnswer` thêm đúng một cột `confirmedAt` (nullable) để phân biệt nháp và chốt; không thêm cột nào khác.
- **Sở hữu phiên:** mọi path `{id}` đi qua `ownSession` hiện có (`MockInterviewServiceImpl:152`); id của ứng viên khác → 403
  với thông điệp tiếng Việt như mock đang dùng, không để lộ sự tồn tại của phiên bằng 404.
- **Không có audio/video trong BE:** không endpoint multipart, không cột binary; DTO không khai báo
  trường audio nên payload có nhét thêm cũng bị bỏ qua. Ranh giới này là một phần của mục 8 attachment
  (không giữ bản ghi hình nếu không cần và không công bố).
- **Không log nội dung nhạy cảm:** log `sessionId` + kết quả; không log `questionText`, `answerText`,
  nội dung CV.
- **Authorization theo nền cũ:** token dùng cho luồng này vẫn mang claim `tv` + `sid` (010 + 011);
  đăng xuất / đổi mật khẩu / admin khóa tài khoản thu hồi phiên đăng nhập thì các request avatar
  đang mở của người đó chết ngay ở request kế tiếp. Không thêm cơ chế phiên riêng.
- **Báo cáo là dữ liệu mới, không phải cột mới trên session:** entity `InterviewReport`
  (`interview_report`), quan hệ 1-1 với session, lưu 5 chiều + tổng do BE tính + `strengths` /
  `improvements` (TEXT) + `rubricVersion` + `createdAt`.

## Kiểm chứng (chưa đo — hàng đợi cho bước build)

`AvatarInterviewApiIntegrationTest` theo pattern `UserSessionAndAvatarIntegrationTest` của 011
(Testcontainers `mysql:8.0`, stack DB thật, không mock repository):

1. start AVATAR thiếu `jobId` → 400; hợp lệ → `questions` rỗng, `status=IN_PROGRESS`.
2. ba lần `confirm=false` cùng lượt → một dòng answer, `currentQuestionIndex` vẫn 0.
3. `confirm=true` → index sang 1; confirm lại lượt 0 → 400.
4. `questionText` 2001 ký tự → 400; `answerText` vượt trần → 400; cột không đổi.
5. `questionText` gửi vào phiên MOCK → 400.
6. phiên của ứng viên khác (GET/answer/evaluation) → 403.
7. evaluation đúng rubric → report có, tổng = trung bình 5 chiều; body gửi `overallScore=999` vẫn ra
   tổng đúng như BE tính.
8. rubric thiếu một chiều / một chiều = 101 / nộp khi chưa finish → 400, `interview_report` không có dòng.
9. lượt thứ 41 → 400 "Phiên đã đạt số lượt tối đa", `interview_answers` vẫn 40 dòng.
10. token INTERVIEWER và ADMIN gọi `GET /{id}/report` → 403; ứng viên khác cũng 403.
11. hết luồng: đơn ứng tuyển và `interview_slots` của cùng candidate **không đổi** bản ghi.

Số đo thật (tests run / failures / errors) ghi vào `specs/012-avatar-interview-api/quickstart-measure.md`
như 010; chưa có số thì chưa báo "xong" (Hiến pháp II).

## Bốn điểm anh chốt 26/10 (hỏi-đáp, không còn câu mở)

1. **Nguồn ghi = FE relay bằng JWT của ứng viên.** Python service không gọi thẳng BE; mỗi lượt đi
   hai nhịp (FE↔Python để lấy lời thoại + avatar, FE↔BE để ghi dữ liệu). Hệ quả: BE không cấp
   token dịch vụ, không có allowlist IP, mọi write vẫn qua `ownSession`.
2. **Trần 40 lượt + rubric 5 chiều `{chuyenMon, duAn, tuDuy, truyenDat, mucKhop}` là do BE đề xuất
   và anh duyệt ngay trên spec**, không lấy từ mockup hay tài liệu tuyển dụng nào. Vì vậy
   `rubricVersion` là bắt buộc: đổi thang sau này phải ghi version mới, dữ liệu cũ đọc lại vẫn đúng.
3. **Employer/ADMIN không đọc được báo cáo avatar** — chỉ chính chủ (Phạm vi 6).
4. **Phiên bỏ dở: FE gọi `finish`, BE không tự chốt** (Phạm vi 7). Không có `ABANDONED`, không có
   scheduler quét session treo.

## Out of scope

- Python AI service, LiveTalking, avatar model, lip-sync, WebRTC/STT/TTS: repo khác; kể cả PoC P0
  của attachment — blocker GPU/VRAM thuộc môi trường đó, không ghi vào spec này.
- Màn avatar React, xin quyền micro, chỉ báo đang ghi âm, caption: FE.
- Phiên avatar gắn với đơn ứng tuyển thật / slot đã hẹn (luồng có chủ), score tự động đẩy
  `ApplicationStatus`, weighted score theo JD.
- Geo-IP, danh sách thiết bị, dọn dòng hết hạn: thuộc wave 011, không đụng.
- Object storage cho ảnh/audio: BE vẫn không có `spring.servlet.multipart` (đúng như 011 chốt).
- Service token cho Python ghi thẳng, `ABANDONED` + scheduler tự chốt phiên treo, màn report avatar
  cho employer: cả ba là lựa chọn có chủ đích tại mục "Bốn điểm anh chốt 26/10", không phải thiếu sót.
