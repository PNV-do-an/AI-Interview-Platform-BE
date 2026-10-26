# REQUEST FLOW — Luồng chạy của request (file → file)

> Mô tả đường đi của một request từ khi user bấm nút đến khi nhận response,
> qua từng file (**kèm chú ý quan trọng mỗi file**). Hãy mở từng file khi đọc.

```
CẤU TRÚC TỔNG QUAN
────────────────────────────────────────────────────────────────────────────
  Browser (React FE)                    Docker (BE :8080)
  ┌──────────────────────────┐         ┌──────────────────────────────┐
  │ Page  →  hook (use*Ctrl) │         │ AuthController (JWT filter)  │
  │       →  service         │  HTTP   │   → Service(+Impl)           │
  │       →  http.service    │ ──────► │   → Scheduler/OpenAi/File    │
  │  (axios, gắn JWT)        │         │   → Repository → DATABASE    │
  └──────────────────────────┘         └──────────────────────────────┘
```

**2 quy tắc vàng** (áp dụng cho MỌI luồng):

1. **Response luôn được bọc trong `ApiResponse{success, message, data}`** →
   FE đọc kết quả ở `res.data.data` (axios đã lấy response, `.data` = body HTTP,
   `.data` (thứ 2) = trường `data` trong body).
2. **FE không gọi axios trực tiếp ở trang** — trang chỉ "view", gọi qua hook,
   hook gọi qua service, service gọi `http.service`. Theo thứ tự đó từ trên xuống.

---

## LUỒNG 0 — Đăng nhập (nền tảng, hiểu cái này thì hiểu JWT)

```
LoginPage.tsx (form) → useAuth (AuthContext) → auth.service.ts → http.service(axios)
   → [BE] AuthController.login → LoginInterfaceImpl (kiểm tra mật khẩu) 
   → UserRepository (DB) → [trả JWT] → AuthContext lưu token → AppLayout hiện menu
```

| # | File (đường dẫn tương đối) | Chú ý quan trọng khi đọc |
|---|---|---|
| 1 | `frontend/src/views/pages/LoginPage.tsx` | Thuần UI. Bấm Login → gọi `login(form)` từ `useAuth()`. Trang KHÔNG gọi API. |
| 2 | `frontend/src/context/AuthContext.tsx` | 🔑 Trái tim JWT phía FE. `login()` gọi API, rồi **lưu `accessToken` + `refreshToken` vào localStorage/sessionStorage** (tuỳ checkbox "remember me"). `isAuthenticated` điều khiển `ProtectedRoute`. |
| 3 | `frontend/src/services/auth.service.ts` | Định nghĩa endpoint `POST /api/v1/auth/login`, body = `{ email, password }`. |
| 4 | `frontend/src/services/http.service.ts` | 🔑 **Interceptor (axios)**. Request: tự gắn `Authorization: Bearer <token>` (dòng 27-37). Response: nhận **401** → tự gọi `/auth/refresh` đổi token mới rồi **replay lại request cũ** (dòng 39-93, có `failedQueue` để không gửi song song). Token hết hạn mà refresh lỗi → nhảy về `/login`. |
| 5 | `frontend/src/views/components/ProtectedRoute.tsx` | Chặn route: chưa đăng nhập → nhảy `/login`; sai role → `/unauthorized`. |
| 6 | `backend/.../controller/auth/AuthController.java` | `@PostMapping("/login")` (dòng 46). Phục vụ cả auth + refresh (dòng 69). |
| 7 | `backend/.../service/impl/LoginInterfaceImpl.java` | Xác thực email/password (dùng `PasswordEncoder`), tạo **JWT access/refresh**, trả về cùng user. |

> 💡 **Hệ quả học được**: chỉ cần đăng nhập 1 lần, mọi request sau tự có token nhờ interceptor.
> Token quá hạn thì tự đổi — user không thấy gì.

---

## LUỒNG 1 — Upload & phân tích CV  (POST `/api/v1/cv/upload`)

```
CVUploadPage (drop file) → useCvController.uploadCv → cv.service.upload → http.service (kèm JWT)
   → CvController.uploadCv → CvServiceImpl: validate→PDFBox trích text→lưu file
   → OpenAiClient.parseCv (GPT) → UserCv/UserSkill repository (DB)
   → CvParseResponse → ApiResponse → hook setState → preview render
```

| # | File | Chú ý |
|---|---|---|
| 1 | `frontend/src/views/pages/CVUploadPage.tsx` | Drop zone for → gọi `uploadCv(file)`; preview gọi `CvView` component ngay trong file. Có select vị trí mục tiêu → `setPosition()`. |
| 2 | `frontend/src/controllers/useCvController.ts` | 🔑 Pattern hook chuẩn: state (`cv/loading/actionLoading/error/successMsg`) + các hàm gọi service. Caller nhận **`res.data.data`** (mở `ApiResponse`). |
| 3 | `frontend/src/services/cv.service.ts` | Gửi **multipart**: `new FormData()` + `append('file', file)` — phải match tên param của BE (`@RequestParam("file")`). |
| 4 | `backend/.../controller/CvController.java` | `@PostMapping("/upload")` (dòng 31). `@PreAuthorize("isAuthenticated()")`; lấy user từ `Authentication` (JWT đã được backend filter giải mã). |
| 5 | `backend/.../service/impl/CvServiceImpl.java` | Check loại file & kích thước (≤10MB) → **PDFBox** trích text từ PDF → `persistCvFile` lưu vào `uploads/cv` → gọi AI. |
| 6 | `backend/.../service/OpenAiClient.java` | 🔑 Chỗ duy nhất gọi OpenAI. `chat()`: ném nếu thiếu `OPENAI_API_KEY`; `parseCv()` dùng prompt + `response_format: json_object` → **strip code fence** → Jackson parse. AI lỗi → **502** (do `GlobalExceptionHandler`). |
| 7 | `backend/.../model/repository/UserCvRepository`+`UserSkillRepository` | `save()` + `replaceSkills()` (xóa kỹ năng cũ, chèn kỹ năng mới). |
| 8 | `backend/.../model/dto/response/CvParseResponse.java` | DTO trả về: `skills, experience, education, suggestedPositions, knowledgeGaps...` → map đúng với `frontend/src/models/Cv.model.ts`. |

> 💡 **Lỗi hay gặp**: file sai định dạng/size → FE tự chặn trước; AI chưa có key → message 502 hiển thị to trên trang.

---

## LUỒNG 2 — Tạo lộ trình ôn tập  (POST `/api/v1/study/generate`)

```
StudyProgramPage → useStudyController.generateProgram → study.service.generate
   → StudyProgramController.generateProgram → StudyProgramServiceImpl
   → UserCvRepository (chưa CV → 400)
   → buildTopics (skills + knowledge_gaps, max 10)
   → AIQuestionService.generateQuestions — cache-first (SHA-256) hoặc chatJson (GPT)
   → StudyProgram/StudyTopic/Question repository (DB) → StudyProgramResponse
```

| # | File | Chú ý |
|---|---|---|
| 1 | `frontend/src/views/pages/StudyProgramPage.tsx` | Danh sách chương trình + form generate (chọn type & targetPosition, gợi ý từ CV qua `<datalist>`). |
| 2 | `frontend/src/controllers/useStudyController.ts` | 🔑 Lưu ý: `generateProgram` lại tự gọi `fetchPrograms()` để làm mới danh sách — pattern **"lệnh xong thì refresh list"**. |
| 3 | `backend/.../controller/StudyProgramController.java` | 6 endpoint (dòng 34-82); `/questions` là **paged** (`page`, `size`) — FE truyền `params`. |
| 4 | `backend/.../service/impl/StudyProgramServiceImpl.java` | `buildTopics` từ CV: mỗi skill + knowledge_gap thành 1 topic. |
| 5 | `backend/.../service/impl/AIQuestionServiceImpl.java` | 🔑 **Cache-first**: SHA-256 của (position+skill) làm key → có trong `AiQuestionCacheRepository` thì trả lại, không thì gọi `OpenAiClient.chatJson` rồi **lưu cache + lưu câu hỏi** → tiết kiệm token, lần sau gần như tức thì. |
| 6 | `backend/.../model/repository` `StudyProgramRepository`, `StudyTopicRepository`, `QuestionRepository` | Ghi toàn bộ lộ trình + câu hỏi sinh ra vào DB. |
| 7 | `frontend/src/models/Study.model.ts` | Map DTO: `StudyProgram`, `Topic`, `Question`, `AnswerEvaluation`, `DifficultyLevel`, `QuestionType`. |

> 💡 **Điểm học cache**: 2 request giống nhau → request 2 không tốn tiền API. Prompt trả `{"questions":[...]}` (bắt buộc OBJECT vì `json_object`).

---

## LUỒNG 3 — Xem câu hỏi & trả lời  (GET questions + POST answer)

```
StudyTopicPage → useStudyTopicController (fetchTopics, fetchQuestions, submitAnswer)
   → study.service → GET /study/topics/{topicId}/questions?page=0&size=10
   → [nộp bài] POST /study/topics/{topicId}/answer {questionId, answer}
   → StudyProgramServiceImpl.answerQuestion → AIQuestionServiceImpl.evaluateAnswer (GPT)
   → AnswerEvaluation → QuestionCard hiện điểm + feedback + đáp án mẫu
```

| # | File | Chú ý |
|---|---|---|
| 1 | `frontend/src/views/pages/StudyTopicPage.tsx` | 🔑 Trang hợp nhất: tiêu đề chủ đề (nút step) + danh sách câu hỏi (phân trang) + card đánh giá ngay dưới câu vừa nộp. |
| 2 | `frontend/src/controllers/useStudyTopicController.ts` | Tách riêng vì logic nhiều: state `questions` kiểu `PagedResponse<Question>`, `evaluation` lưu bài mới chấm. |
| 3 | `frontend/src/services/study.service.ts` | `getQuestions` truyền `{ params: { page, size } }`; `submitAnswer` POST JSON. |
| 4 | `backend/.../controller/StudyProgramController.java` | `/questions` có tham số `page/size`; `/answer` body `{questionId, answer}`. |
| 5 | `backend/.../service/impl/AIQuestionServiceImpl.java` | `evaluateAnswer`: cho GPT điểm 0-10 + `isCorrect` + feedback + `modelAnswer`. AI fail → 502 (UI giữ nguyên). |
| 6 | `frontend/src/views/pages/StudyTopicPage.tsx` → `Pagination` | Nhảy trang → gọi lại `fetchQuestions(page)` — dữ liệu backend trả **0-based** page. |

> 💡 **Điểm học phản hồi**: sau khi nộp bài, `evaluation` ngăn sửa lại textarea (`disabled`) và hiện `Điểm mạnh / Cần cải thiện / Đáp án mẫu`.

---

## LUỒNG 4 — Phỏng vấn: start → next → answer → finish → report

```
InterviewSetupPage → useInterviewController.startSession → interview.service
   → InterviewController /start → InterviewServiceImpl: build pool (AI cache/chatJson) + session
   → InterviewSessionPage (use location.state.session)
        /next → question tiếp theo (tăng currentQuestionIndex)
        /answer → text (GPT) + audio tùy chọn (base64 → file → Whisper → eloquence/coherence)
        /finish → GPT tổng hợp overall → lưu evaluation + COMPLETED
   → InterviewResultsPage → /report → Recharts (Radar + Bar)
```

| # | File | Chú ý |
|---|---|---|
| 1 | `frontend/src/views/pages/InterviewSetupPage.tsx` | Form cấu hình: `sessionType`, `totalQuestions`, `timeLimit`, `questionTypes`, `focusSkills` (lấy từ CV). Có lịch sử → xem báo cáo cũ. |
| 2 | `frontend/src/controllers/useInterviewController.ts` | 🔑 6 state + 7 hàm cho cả 3 màn hình (setup/session/results). `getNextQuestion` tự cộng `currentQuestionIndex`. |
| 3 | `frontend/src/views/pages/InterviewSessionPage.tsx` | Nhận session qua **`location.state`** (không gọi lại API); khi `answeredCount >= totalQuestions` → hiển thị nút **Hoàn tất** → `/finish`. |
| 4 | `frontend/src/views/components/AudioRecorder.tsx` | 🔑 `MediaRecorder` thu mic → blob → **base64** + filename `.webm`. Chỉ chạy trên **localhost/https**. Audio là TÙY CHỌN, text bắt buộc. |
| 5 | `backend/.../controller/InterviewController.java` | 6 endpoint (dòng 32-79). Tất cả check **sở hữu** session qua `findByIdAndUserId` → của người khác = **404**. |
| 6 | `backend/.../service/impl/InterviewServiceImpl.java` | 🔑 `startSession`: cần CV (không CV → 400), pool = mỗi skill call `generateQuestions` (cache-first). `submitAnswer`: verify câu hỏi thuộc pool → `evaluateAnswer`; nếu có audio → `AudioAnalysisService`. `finishSession`: gom Q&A → GPT tổng hợp → session `COMPLETED`. **AI fail → 502, rollback giữ `IN_PROGRESS`** (câu đã trả lời vẫn còn). |
| 7 | `backend/.../service/impl/AudioAnalysisServiceImpl.java` | Lưu file audio → gọi `OpenAiClient.transcribeAudio` (**Whisper**, multipart) → lấy text → **`chatJsonEval`** (model `evalModel`) chấm `eloquenceScore` + `coherenceScore`. |
| 8 | `backend/.../service/OpenAiClient.java` | `transcribeAudio(byte[], fileName)`: endpoint `/audio/transcriptions`, model `whisper-1`; thiếu key → 502 sớm, KHÔNG lưu file rác. Có 2 nhóm model: `chat/chatJson` dùng `model` (sinh nội dung), `chatEval/chatJsonEval` dùng `evalModel` (đánh giá) — tách qua `AI_MODEL`/`AI_EVAL_MODEL`. |
| 9 | `frontend/src/views/pages/InterviewResultsPage.tsx` | 🔑 Recharts: `RadarChart` (4 khía cạnh) + `BarChart` (điểm từng câu). Nhận report qua `location.state` hoặc gọi `/report` khi mở trực tiếp. |
| 10 | `backend/.../model/repository` `InterviewSessionRepository`/`InterviewAnswerRepository`/`InterviewEvaluationRepository` | Session + từng answer + evaluation tổng. |

> 💡 **Điểm học chính sách an toàn**: mọi endpoint đọc dữ liệu theo **user hiện tại** → cross-user trả 404 chứ không 403 (không lộ thông tin). Cỡ audio ≤ 8MB, whitelist đuôi file, lưu ngoài web root.

---

## LƯU Ý CHUNG (đọc trước khi sửa code)

| Vấn đề | Chỗ nào | Hành vi |
|---|---|---|
| Thiếu `OPENAI_API_KEY` | `.env` (root) → `OpenAiClient.chat/chatJson/transcribeAudio` | Ném lỗi → `GlobalExceptionHandler` → **HTTP 502** message rõ, app không crash |
| AI lỗi/validation fail | `CvServiceImpl`, `StudyProgramServiceImpl`, `InterviewServiceImpl` | 400 (thiếu CV/đầu vào) hoặc 502 (AI); FE hiện `e.response.data.message` |
| JWT hết hạn | `http.service.ts` response interceptor | Tự refresh + replay request; FAIL hẳn → về `/login` |
| `json_object` | `OpenAiClient.chatJson` + prompt | Phải ép AI trả **OBJECT** (`{"questions":[...]}`), nếu không JSON parse lỗi |
| Cache câu hỏi | `AIQuestionServiceImpl` + `AiQuestionCacheRepository` | SHA-256 của (position + skill) → lần 2 không tốn API |
| Tách model AI | `OpenAiClient.chatEval/chatJsonEval` + `AIConfig.evalModel` | Sinh (CV/câu hỏi) dùng `AI_MODEL`; đánh giá (evaluate/audio/overall) dùng `AI_EVAL_MODEL` |
| Provider Gemini | `AI_PROVIDER=gemini` + `AI_BASE_URL` | Gọi `:generateContent` với `responseMimeType: application/json`, auth bằng header `x-goog-api-key`; retry 3 lần khi 503. Provider mặc định `openai` giữ nguyên (Bearer + `/chat/completions`) |
| Audio transcribe | OpenAi: Whisper `/audio/transcriptions` · Gemini: `inline_data` | **Gemini chỉ nhận WAV/PCM (`audio/pcm`)**; webm/ogg/mp4 từ ghi âm browser bị từ chối → 502 kèm gợi ý dùng provider openai (whisper-1) cho ghi âm trình duyệt |
| Trộn loại câu hỏi | `InterviewServiceImpl.buildQuestionPool` | Pool phân theo round-robin (skill × questionType) → mỗi nhóm gọi AI 1 lần; số câu khớp `total` (leftover % được phân bổ); MCQ bắt buộc đủ 4 options `{key,text}`, MCQ cache thiếu options bị bỏ qua |
| Sở hữu dữ liệu | `InterviewServiceImpl`, repos `findByIdAndUserId` | Query theo đúng user hiện tại → 404 nếu không phải của mình |
| Port/kết nối | `http.service.ts` baseURL mặc định `:8080`; `docker-compose.yml` nginx :3000 proxy `/api` | Dev dùng CRA proxy (package.json `"proxy"`); Docker dùng nginx straight về backend |
| Bảng DB | Hibernate `ddl-auto: update` (flyway tắt) | Các file `db/migration/V*.sql` chỉ là tham chiếu; Hibernate tự tạo bảng |

## Bản đồ file nhanh (FE ↔ BE)

| Chức năng | FE | BE |
|---|---|---|
| Login/auth | `AuthContext` · `auth.service` | `AuthController` · `LoginInterfaceImpl` |
| CV | `CVUploadPage` · `useCvController` · `cv.service` · `Cv.model` | `CvController` · `CvServiceImpl` · `OpenAiClient` |
| Study | `StudyProgramPage` · `useStudyController` · `study.service` | `StudyProgramController` · `StudyProgramServiceImpl` · `AIQuestionServiceImpl` |
| Topic/Q&A | `StudyTopicPage` · `useStudyTopicController` | `StudyProgramController` · `AIQuestionServiceImpl` |
| Interview | `InterviewSetup/Session/ResultsPage` · `AudioRecorder` · `useInterviewController` · `interview.service` · `Interview.model` | `InterviewController` · `InterviewServiceImpl` · `AudioAnalysisServiceImpl` · `OpenAiClient` |

**Thứ tự học đề xuất**: LUỒNG 0 → LUỒNG 1 → LUỒNG 2 → LUỒNG 3 → LUỒNG 4,
mỗi luồng đọc từ **dưới BE lên trên FE** (hiểu BE trước, FE là "người gọi").