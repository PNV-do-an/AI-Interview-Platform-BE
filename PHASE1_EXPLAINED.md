 # Phase 1 – Foundation: Giải thích chi tiết từng file

Tài liệu này giải thích **mục đích và logic của từng file** đã tạo/sửa trong Phase 1 (CV analysis + AI foundation). Trạng thái hiện tại: hoàn thành code + smoke test, **chưa commit**.

---

## 1. Tổng quan luồng hoạt động

```
Người dùng upload CV (PDF/TXT) hoặc paste text
        │
        ▼
CvController  (/api/v1/cv/*)  — nhận request, lấy User từ JWT
        │
        ▼
CvService / CvServiceImpl  — validate file, trích text (PDFBox), lưu file, lưu DB
        │
        ▼
OpenAiClient  — gọi OpenAI GPT-4o-mini (JSON mode), validate, retry, map dữ liệu
        │
        ▼
Lưu: user_cv (JSON columns) + user_skill (từng skill là 1 row)
        │
        ▼
Trả về CvParseResponse (skills / experience / education / suggested_positions / knowledge_gaps)
```

---

## 2. Các file SỬA (đã có sẵn từ trước, bổ sung hàm)

### `backend/src/main/resources/application.yml`
Thêm 4 block cấu hình mới:
- **`spring.servlet.multipart`** — cho phép upload file tối đa **10MB** (bắt buộc để nhận `MultipartFile` CV).
- **`ai`** — cấu hình OpenAI:
  - `api-key: ${OPENAI_API_KEY:}` → đọc từ biến môi trường, mặc định rỗng (chưa có key thì app vẫn chạy).
  - `model: ${AI_MODEL:gpt-4o-mini}` → model tạo câu hỏi/parse CV, đổi qua `.env` không cần sửa code.
  - `eval-model: ${AI_EVAL_MODEL:gpt-4o-mini}` → model đánh giá câu trả lời (dự trữ cho Phase 3, có thể nâng cấp riêng).
  - `whisper-model: whisper-1`, `tts-model: tts-1`, `tts-voice: alloy` → dự trữ cho Phase 3 (voice → text, text → voice).
  - `temperature: 0.3` → thấp để output ổn định, ít "bay bổng".
- **`storage`** — thư mục lưu file: `cv-upload-dir` (mặc định `./uploads/cv`), `audio-upload-dir`, `max-file-size`, `allowed-file-types` (chỉ PDF + text).
- **`interview` + `study`** — các giá trị mặc định (số câu hỏi, phân bố độ khó 20/50/30, số topic...) dự trữ cho Phase 2/3.

Lưu ý: `spring.flyway.enabled: false` + `jpa.hibernate.ddl-auto: update` → **bảng DB do Hibernate tự tạo**; file `V6__interview_tables.sql` giữ làm tài liệu/tái lập DB chuẩn.

### `backend/pom.xml`
- Nâng **jjwt `0.11.5` → `0.12.6`**: sửa lỗi build `Jwts.parserBuilder()` không tồn tại (IDE resolve nhầm version 0.12.6 trong `.m2`). API mới: `Jwts.parser().verifyWith(...).build().parseSignedClaims(...)`.
- Thêm **`org.apache.pdfbox:pdfbox:3.0.1`**: trích văn bản từ file PDF.

### `docker-compose.yml`
Truyền thêm biến môi trường vào container backend, mặc định an toàn:
- `MAIL_TEST_CONNECTION: ${MAIL_TEST_CONNECTION:-false}` — **quan trọng**: bỏ qua kiểm tra SMTP khi khởi động (trước đây default `true` làm backend fail khi mail sai).
- `OPENAI_API_KEY`, `CV_UPLOAD_DIR`, `AUDIO_UPLOAD_DIR` — đồng bộ từ `.env`.

### `.env.example`
Cập nhật template chuẩn: khớp credential DB của compose (`aiuser`/`aipassword`), `MAIL_TEST_CONNECTION=false`, placeholder `OPENAI_API_KEY=sk-your-api-key-here`, các thư mục upload. (`.env` thật của bạn nằm ở root, đã được gitignore — **không commit**.)

### `.gitignore`
Thêm `backend/uploads/` — không commit file CV upload của user.

---

## 3. Các file MỚI — giải thích từng file

### `config/AIConfig.java`
Class cấu hình AI, đọc từ block `ai` trong `application.yml` (qua `@ConfigurationProperties(prefix = "ai")`).
- Giữ các field: `provider`, `apiKey`, `baseUrl`, `model`, `evalModel`, `whisperModel`, `ttsModel`, `ttsVoice`, `maxTokens`, `temperature`.
- Vai trò: **một nguồn cấu hình tập trung** — mọi service AI (parse CV, generate câu hỏi, đánh giá) đều đọc model/API key từ đây. Đổi model chỉ sửa `.env`.

### `common/exception/AIServiceException.java`
- Là RuntimeException riêng cho lỗi AI. Nếu không có thì mọi fail của OpenAI sẽ trở thành 500 chung chung.
- Kết hợp với `GlobalExceptionHandler.java` (sửa): thêm `@ExceptionHandler(AIServiceException.class)` trả về **HTTP 502** (bad gateway — lỗi từ hệ thống bên ngoài), kèm message rõ ràng, không lộ stack trace cho client.

### `model/enums/` (5 file)
- **`DifficultyLevel`**: `BEGINNER / INTERMEDIATE / ADVANCED` — độ khó câu hỏi (Phase 2 dùng).
- **`QuestionType`**: `MCQ / SHORT_ANSWER / CODE / AUDIO` — loại câu hỏi (đã có Câu 1 MCQ sẵn; câu tự luận + code + audio cho Phase 2/3).
- **`ProgramType`**: `STUDY / INTERVIEW_PREP` — loại chương trình luyện tập (Phase 2).
- **`SessionStatus`**: `NOT_STARTED / IN_PROGRESS / COMPLETED / CANCELLED` — trạng thái phiên phỏng vấn (Phase 3).
- **`ProficiencyLevel`**: `BEGINNER / INTERMEDIATE / ADVANCED / EXPERT` — mức thành thạo kỹ năng, lưu ở `user_skill`. Đây là enum DUY NHẤT được dùng ngay trong Phase 1.

### `model/entity/UserCv.java`
Entity 1-N với `User` (một user có thể có 1 CV). Các cột:
- `fileName`, `filePath`, `rawText` — thông tin & nội dung gốc của file CV.
- `parsedSkills`, `parsedExperience`, `parsedEducation`, `suggestedPositions`, `knowledgeGaps` — kiểu **JSON string** (lưu nguyên đống JSON từ AI để trả về nhanh, không cần join bảng).
- `targetPosition`, `targetLevel` — vị trí + level mục tiêu user muốn đạt (người dùng tự chọn qua `PUT /position`).
- `@PrePersist`/`@PreUpdate` tự set `createdAt`/`updatedAt`.

Lý do để JSON columns thay vì bảng quan hệ phức tạp: parse CV chỉ đọc lại, không cần truy vấn theo trường bên trong (Phase 2 sẽ truy vấn skill → tách bảng `user_skill`).

### `model/entity/UserSkill.java`
Entity 1-N với `UserCv` — **từng kỹ năng là 1 row**:
- `skillName`, `proficiencyLevel` (enum, mặc định `INTERMEDIATE`), `yearsExperience`.
- Mục đích: Phase 2 cần **query/tìm** kỹ năng để sinh câu hỏi phù hợp → phải để cột riêng (không thể query JSON string).

### `model/repository/UserCvRepository.java`
Spring Data JPA, thêm các method truy vấn tự động:
- `findByUserId` — CV của một user.
- `findTopByUserIdOrderByUpdatedAtDesc` — CV mới nhất.
- `existsByUserId` — kiểm tra đã có CV chưa.

### `model/repository/UserSkillRepository.java`
- `findByCvId` — lấy hết skill của một CV.
- `deleteByCvId` — xoá skill của CV (dùng khi parse lại CV, để "thay" toàn bộ danh sách cũ).

### `model/dto/request/CvTextRequest.java`
Body của `POST /api/v1/cv/parse-text`: trường `cvText` + `@NotBlank` validate không bỏ trống.

### `model/dto/request/SetPositionRequest.java`
Body của `PUT /api/v1/cv/position`: `position` + `level`, bắt buộc không trống.

### `model/dto/response/CvParseResponse.java`
Class trả về chính của các API CV — cấu trúc chuẩn hoá:
- Các **nested class**: `SkillData` (name/proficiency/years), `ExperienceData` (company/role/duration/achievements), `EducationData` (institution/degree/year).
- `suggestedPositions` (3 vị trí phù hợp), `knowledgeGaps` (lỗ hổng kiến thức để gợi ý luyện tập), `targetPosition`/`targetLevel`.
- `@Builder.Default ... = new ArrayList<>()` → luôn trả mảng rỗng thay vì null khi chưa có CV (client không phải check null).

### `service/OpenAiClient.java`
**Lõi AI** — gọi OpenAI Chat Completions:
- Dùng `RestTemplate` (không thêm webflux): connect timeout 15s, read timeout 60s.
- `chat(userPrompt)`: tạo body `{model, messages, max_tokens, temperature, response_format: {type: "json_object"}}` → **bắt buộc JSON**; đính kèm `Authorization: Bearer <key>`. Nếu key trống → ném `AIServiceException` với message rõ ràng.
- `parseCv(cvText)`: gọi lần 1 với prompt phân tích → nếu JSON không hợp lệ thì **retry tối đa 2 lần** với prompt nhắc nhẹ (`buildCvRetryPrompt`) → vẫn fail thì ném `AIServiceException` kèm log nội dung thô để debug.
- `extractContent`: đọc `choices[0].message.content`; nếu response có field `error` (VD sai key) → bọc thành lỗi AI thân thiện.
- `parseAndMap` / `mapCvNode`: 
  - `stripCodeFences` — gỡ ```markdown``` và chỉ giữ phần trong `{ }` (AI hay bọc JSON trong code block).
  - `validateCvNode` — kiểm tra `skills` là mảng, giới hạn skills≤30, experience≤20, education≤10 (chống AI "bốc phét" quá tay).
  - `parseProficiency` — chuẩn hoá enum, giá trị lạ → mặc định `INTERMEDIATE`.
- `buildCvAnalysisPrompt`: tiếng Anh, định nghĩa schema mẫu đầy đủ (dùng `.replace()` thay `.formatted()` — tránh crash vì ký tự `%` trong sample, đã từng gặp bug `UnknownFormatConversionException`).

### `service/CvService.java`
Interface — định nghĩa 5 phương thức: `uploadCv`, `parseCvText`, `getCv`, `setPosition`, `getCvSkills`.

### `service/impl/CvServiceImpl.java`
Triển khai logic nghiệp vụ CV:
- **`uploadCv`**: 
  - `resolveContentType` — chấp nhận MIME `application/pdf`/`text/plain` hoặc đuôi `.pdf`/`.txt` (chống file giả dạng).
  - Kiểm tra kích thước ≤10MB.
  - `extractText` — PDF thì `Loader.loadPDF` + `PDFTextStripper`; txt thì đọc UTF-8.
  - `persistCvFile` — lưu file vào thư mục `cvUploadDir` với tên `cv_<timestamp>_<tên gốc>` (tên gốc được vệ sinh, bỏ ký tự đặc biệt).
  - Gọi `openAiClient.parseCv(rawText)` → `persistParsed`.
- **`parseCvText`**: nhận text paste trực tiếp, bỏ qua bước file.
- **`persistParsed`**: upsert (tạo mới hoặc cập nhật CV theo `userId`) → lưu các JSON columns → `replaceSkills` (xoá skills cũ của CV rồi chèn danh sách mới — đảm bảo luôn khớp với CV) → gán `cvId` vào response.
- **`getCv`**: đọc CV mới nhất của user, chuyển JSON columns về object (bảng rỗng nếu chưa có CV).
- **`setPosition`**: cập nhật `targetPosition`/`targetLevel`; báo lỗi `ResourceNotFoundException` nếu user chưa parse CV (phải có CV trước khi chọn vị trí).
- **`getCvSkills`**: trả danh sách `UserSkill` của CV mới nhất (dữ liệu nguồn cho Phase 2).
- `toJson`/`readList`: serialize/deserialize an toàn, sai thì trả danh sách rỗng + log thay vì crash.

### `controller/CvController.java`
Endpoint chuẩn REST, toàn bộ yêu cầu **phải đăng nhập** (`@PreAuthorize("isAuthenticated()")` — giống quy tắc bảo mật sẵn của admin):
- `POST /api/v1/cv/upload` — upload file (`@RequestParam("file")`), AI phân tích.
- `POST /api/v1/cv/parse-text` — dán văn bản CV (`@Valid`), AI phân tích.
- `GET /api/v1/cv` — lấy CV (structure rỗng nếu chưa có).
- `PUT /api/v1/cv/position` — chọn vị trí + level mục tiêu.
- `GET /api/v1/cv/skills` — danh sách kỹ năng.
- `currentUser()` — lấy `User` hiện tại từ JWT: `userService.findByEmail(authentication.getName())` (đảm bảo ai parse CV cũng là chủ CV).

### `db/migration/V6__interview_tables.sql`
SQL tạo 2 bảng `user_cv` + `user_skill` (kèm index/FK/CASCADE). Mặc dù Hibernate đang tự tạo bảng (`ddl-auto: update`), file này là **bản ghi chuẩn** để áp dụng thủ công hoặc kích hoạt lại Flyway, và là tham chiếu cho team.

---

## 4. Tóm tắt luồng dữ liệu

```
Client ──POST file/text──▶ CvController ──▶ CvServiceImpl
                                               │  PDFBox trích text
                                               │  lưu file → storage
                                               ▼
                                          OpenAiClient
                                               │  GPT-4o-mini (JSON mode, retry 2×, validate)
                                               ▼
                                       CvParseResponse (đã map+validate)
                                               │
                                               ▼
                        user_cv (JSON columns) + user_skill (1 skill / 1 row)
                                               │
                                               ▼
                                     ApiResponse.success(...) ──▶ Client
```

## 5. Trạng thái & việc cần làm tiếp
- ✅ Build + package thành công; `docker compose up` OK; smoke test: login / `GET /api/v1/cv` / `parse-text` (502 khi thiếu key).
- ⏳ Test AI thật: điền `OPENAI_API_KEY` vào `.env` → `docker compose up -d --build backend`.
- ⏳ **Chưa commit** (18 file, nhánh `interview_program`).
- 🔜 Phase 2 dựa trên: `user_skill` + enum `DifficultyLevel`/`QuestionType`/`ProgramType` để sinh câu hỏi (GPT-4o-mini), `CvParseResponse.suggestedPositions`/`knowledgeGaps` để cá nhân hoá.

---
*Tài liệu tạo 2026-09-19 · kèm theo `INTERVIEW_PREPARATION_PLAN.md` (Appendix D: 5W1H).*