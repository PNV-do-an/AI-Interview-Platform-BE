# Question Set Bank — Thiết kế

**Ngày:** 2026-11-24
**Phạm vi:** study program (interview = phase 2)
**Thay thế:** cách `QuestionPoolServiceImpl.acquire` đang trộn câu theo cell

---

## 1. Mục tiêu

1. Câu hỏi được sinh và phục vụ theo **bộ (set) 10–20 câu**, không phải chọn lẻ từ một bảng phẳng.
2. Trong một bộ: **cân bằng dạng câu** (archetype quota) và **có mạch liên kết** quanh một `theme`.
3. Chống trùng ở **cấp cell**, không phải cấp bộ — để bộ thứ N không lặp lại bộ thứ N-1.
4. Admin tạo trước được bộ đề, và **review hậu kiểm** (không chặn user).

## 2. Vấn đề gốc

`acquire` hiện tại: chọn 10 câu rời rạc từ toàn bộ câu của một cell, sắp theo `timesUsed`. Hệ quả:

- 10 câu không có mạch liên kết, dễ trùng ý vì AI sinh ở các thời điểm khác nhau với cùng prompt.
- Không kiểm soát được tỉ lệ dạng câu (toàn định nghĩa, không có câu áp dụng).
- Trùng ý chỉ chặn được ở mức exact (`fingerprint`), không chặn ở mức ngữ nghĩa.

Giới hạn đã biết và chấp nhận: **không thể chặn 100% trùng ngữ nghĩa**. Trần thực tế 95–98%; 2–5% còn lại admin xử lý sau.

## 3. Quyết định đã chốt

| # | Quyết định | Ghi chú |
|---|---|---|
| D1 | Khóa dedup = **cell** `(skill_key, difficulty, question_type, language)` | `question_set` chỉ là đơn vị lô + phục vụ |
| D2 | Dedup exact = `fingerprint` (đã có, unique toàn cục) | giữ nguyên |
| D3 | Dedup ngữ nghĩa = `content_key` + **synonym map** viết tay | `UNIQUE (skill_key, content_key)` |
| D4 | Đa dạng = **archetype quota** + **theme** | theme do AI chọn theo skill + difficulty + scope |
| D5 | **Không có gate duyệt**: AI sinh → `AUTO_APPROVED` → phục vụ ngay | tránh cold-start chết |
| D6 | Review **hậu kiểm**: admin duyệt hoặc `REJECTED` sau | `REJECTED` = ngừng cấp, không xoá |
| D7 | Sinh set **đồng bộ** trong request | chấp nhận 3–10s ở lần đầu/skill |
| D8 | Hạn mức lượt dùng = tính năng riêng, **ngoài phạm vi spec này** | không đặt cap ở đây |
| D9 | `acquire` cấp **nguyên bộ**, không trộn câu giữa các bộ | giữ mạch liên kết |

### Điều chỉnh so với yêu cầu "bỏ archived"

Không bỏ hoàn toàn được: câu đã phục vụ có `study_answer.question_id` trỏ tới, **không xoá được**. Nếu không có trạng thái "ngừng cấp" thì câu dở sẽ phục vụ mãi.

Cách gọn hơn: **bỏ `question_set.status` hoàn toàn**, chỉ giữ `question.review_status`.

| Trạng thái câu | Ý nghĩa |
|---|---|
| `AUTO_APPROVED` | AI sinh, đang phục vụ |
| `APPROVED` | admin đã xác nhận |
| `REJECTED` | ngừng cấp cho bộ mới; câu đã giao vẫn còn trong lịch sử |

Một set bị reject toàn bộ → tự động không còn được chọn (không cần trạng thái riêng ở set).

## 4. Schema

### 4.1 Bảng mới `question_set`

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `set_name` | VARCHAR(120), **UNIQUE** | `Basic Docker 1`, `Interview Backend Intern` |
| `scope` | VARCHAR(16) NOT NULL | `STUDY` / `INTERVIEW` |
| `skill_key` | VARCHAR(100) NOT NULL | |
| `skill_name` | VARCHAR(255) | tên hiển thị cho admin |
| `difficulty` | VARCHAR(20) NOT NULL | `DifficultyLevel` |
| `question_type` | VARCHAR(20) NOT NULL | `QuestionType` |
| `language` | VARCHAR(8) NOT NULL | |
| `theme` | VARCHAR(255) NOT NULL | bối cảnh chung của bộ |
| `archetype_quota` | JSON | snapshot quota dùng lúc sinh |
| `times_used` | INT NOT NULL DEFAULT 0 | rotation ở cấp bộ |
| `created_by` | BIGINT NULL | admin tạo tay; NULL = hệ thống tự sinh |
| `created_at` | DATETIME | |

Index: `(skill_key, difficulty, question_type, language, times_used)`.
**Không** đặt unique trên cell — nhiều bộ/cell là hợp lệ.

### 4.2 Cột mới trên `question`

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `set_id` | BIGINT NULL, FK → `question_set.id` | pool row có giá trị; bản copy giao cho topic = NULL |
| `archetype` | VARCHAR(20) NULL | 6 giá trị, xem §4.3 |
| `content_key` | VARCHAR(64) NULL | hash sau chuẩn hóa; bản copy = NULL |
| `review_status` | VARCHAR(16) NOT NULL DEFAULT `AUTO_APPROVED` | xem §3 |

Ràng buộc: `UNIQUE (skill_key, content_key)`. MySQL cho phép nhiều NULL nên bản copy (content_key = NULL) không vướng.

### 4.3 Enum mới

`QuestionArchetype`: `DEFINITION, COMPARISON, APPLICATION, DEBUGGING, TRADE_OFF, SCENARIO`.

`QuestionSetScope`: `STUDY, INTERVIEW`.

`QuestionReviewStatus`: `AUTO_APPROVED, APPROVED, REJECTED`.

### 4.4 Cột mới trên `study_topic`

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `set_id` | BIGINT NULL | bộ đã dùng cho topic này — nguồn dữ liệu cho rotation |

### 4.5 Dữ liệu cũ

Các row `question` đang tồn tại có `set_id = NULL` và **không được backfill** → chúng không còn được phục vụ cho lượt học mới (vẫn giữ cho `study_answer` lịch sử). Lý do: chúng thiếu `archetype`, `theme`, `content_key` nên không thoả prompt contract và quota. Nếu DB đang có pool đáng kể thì đây là chi phí phải chấp nhận của lần triển khai đầu.

### 4.6 Sinh `set_name` không trùng

Tự sinh theo `"<Level> <Skill> <n>"` với `n` = số set hiện có của cell + 1. Có race condition khi 2 request đồng thời → dựa vào `UNIQUE (set_name)`, bắt lỗi và thử lại 1 lần với `n+1`.

### 4.7 Migration

Flyway đang tắt (`flyway.enabled=false`, bảng do `ddl-auto` tạo) nên **không thêm file `.sql` vào `db/migration`**. Cần chạy tay script idempotent trên DB thật:

```sql
CREATE TABLE question_set (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    set_name        VARCHAR(120) NOT NULL,
    scope           VARCHAR(16)  NOT NULL,
    skill_key       VARCHAR(100) NOT NULL,
    skill_name      VARCHAR(255) NULL,
    difficulty      VARCHAR(20)  NOT NULL,
    question_type   VARCHAR(20)  NOT NULL,
    language        VARCHAR(8)   NOT NULL,
    theme           VARCHAR(255) NOT NULL,
    archetype_quota JSON         NOT NULL,
    times_used      INT          NOT NULL DEFAULT 0,
    created_by      BIGINT       NULL,
    created_at      DATETIME     NULL,
    UNIQUE KEY uk_question_set_name (set_name),
    KEY idx_question_set_cell (skill_key, difficulty, question_type, language, times_used)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE question ADD COLUMN set_id BIGINT NULL;
ALTER TABLE question ADD COLUMN archetype VARCHAR(20) NULL;
ALTER TABLE question ADD COLUMN content_key VARCHAR(64) NULL;
ALTER TABLE question ADD COLUMN review_status VARCHAR(16) NOT NULL DEFAULT 'AUTO_APPROVED';
CREATE UNIQUE INDEX uk_question_skill_content ON question (skill_key, content_key);

ALTER TABLE study_topic ADD COLUMN set_id BIGINT NULL;
```

## 5. Chống trùng: 3 lớp

| Lớp | Cơ chế | Recall | Cứng/ mềm |
|---|---|---|---|
| 1 | `fingerprint` unique | exact | **cứng** (DB) |
| 2 | `content_key` unique `(skill_key, content_key)` | ~95% | **cứng** (DB) |
| 3 | Admin review hậu kiểm | phần còn lại | mềm |

### 5.1 Thuật toán `content_key`

```
text
 → lowercase, bỏ dấu câu
 → tokenize theo khoảng trắng
 → bỏ stopword            (KHÔNG bỏ: phủ định, so sánh, polarity)
 → thay synonym
 → sort token
 → join " " → sha256 → 64 hex
```

**Synonym map** (khởi tạo, tiếng Anh vì `study.question-language: en`):

| Nhóm | Ánh xạ |
|---|---|
| động từ dẫn | `define/explain/describe/state/what is` → `describe` |
| ưu điểm | `advantage/benefit/strength/pro` → `pro` |
| nhược điểm | `disadvantage/drawback/limitation/weakness/con` → `con` |
| so sánh | `compare/comparison/difference/differ` → `compare` |
| đánh đổi | `tradeoff/trade-off/compromise` → `tradeoff` |

**Từ bảo vệ — tuyệt đối không bỏ làm stopword:**
`not, no, never, without, cannot, can't, shouldn't, versus, vs, more, less, before, after, best, worst`

Lý do: "When should you use X?" và "When should you NOT use X?" chỉ khác 1 token; bỏ `not` sẽ gộp nhầm hai câu trái nghĩa. Tương tự `pro` / `con`.

### 5.2 Giới hạn đã biết

- Cùng ý nhưng khác cấu trúc hoàn toàn ("Compare A and B" vs "What is the difference between A and B") vẫn có thể lọt nếu synonym map chưa phủ.
- Embedding không dùng: ngưỡng cosine cho cặp trái nghĩa (`pro`/`con`) cao hơn cặp đồng nghĩa khác cấu trúc, nên không tồn tại ngưỡng chung.

## 6. Prompt contract

`buildQuestionPrompt` mở rộng, bắt buộc 4 khối:

| Khối | Nội dung |
|---|---|
| `theme` | bối cảnh bắt buộc, mọi câu phải nằm trong bối cảnh này |
| `archetype_quota` | số câu mỗi dạng + định nghĩa từng dạng |
| `coherence` | "questions revolve around the theme, depth increases progressively" |
| `exclusions` | tối đa 30 `question_text` đã có trong cell + "do not repeat these concepts" |

Output JSON thêm trường `archetype` cho mỗi câu. Server validate:

- `archetype` không thuộc enum → log WARN, mặc định `DEFINITION`.
- Tổng theo archetype lệch quota → log WARN, **vẫn lưu** (không regenerate, không chặn user).

### 6.1 Theme do AI chọn

Theme sinh **trong cùng 1 call** với câu hỏi (không gọi 2 lần) và trả ở root của JSON output. Prompt truyền vào `skill`, `difficulty`, `scope` và **danh sách theme đã dùng trong cell**.

Nếu AI trả theme trùng theme đã có → log WARN và vẫn lưu (không regenerate).

| scope | Ví dụ theme |
|---|---|
| `STUDY` | "local dev setup", "production image optimization", "CI pipeline" |
| `INTERVIEW` | "system design for a payment service", "debugging a production incident" |

### 6.2 Quota mặc định (10 câu)

`DEFINITION` 2, `COMPARISON` 2, `APPLICATION` 2, `DEBUGGING` 1, `TRADE_OFF` 1, `SCENARIO` 2.

## 7. Luồng

### 7.1 Admin tạo trước

```
POST /api/v1/admin/question-sets
  { scope, skillName, difficulty, questionType, language, targetSize, setNames? }
→ resolve skillKey = SkillKeys.normalize(skillName)      ← PHẢI trùng hàm với acquire
→ AI chọn theme
→ AI sinh targetSize câu theo prompt contract §6
→ persist:
     question_set    1 row
     question        N row: set_id, archetype, content_key, review_status=AUTO_APPROVED,
                            topic_id=NULL, skill_key, language, fingerprint
→ trả danh sách câu + cảnh báo trùng (nếu có)
```

### 7.2 Sinh đồng bộ khi user cần

```
POST /api/v1/study/generate  (endpoint không đổi)
→ buildTopics(cv, position) như hiện tại
→ mỗi topic: acquire(position, skillTag, difficulty, ESSAY, language, 10, userId)
```

Bên trong `acquire`:

1. Tìm set chưa dùng cho user (§7.3). Có → dùng luôn, không gọi AI.
2. Không có → **tạo set đồng bộ** với `created_by = NULL`, `set_name` tự sinh
   (`"<Level> <Skill> <n>"` với `n` = số set hiện có của cell + 1, đảm bảo UNIQUE).
3. AI lỗi → ném `AIServiceException` (giữ hành vi hiện tại của `generateQuestions`).

### 7.3 Chọn bộ (rotation)

```sql
SELECT qs.id FROM question_set qs
WHERE qs.skill_key = ? AND qs.difficulty = ? AND qs.question_type = ? AND qs.language = ?
  AND qs.id NOT IN (
      SELECT st.set_id FROM study_topic st
      JOIN study_program sp ON sp.id = st.program_id
      WHERE sp.user_id = ? AND st.set_id IS NOT NULL)
ORDER BY qs.times_used ASC, qs.id ASC
LIMIT 1
```

Sau khi chọn:

```
question WHERE set_id = ? AND review_status <> 'REJECTED' ORDER BY archetype, id
question_set.times_used += 1
```

Nếu set còn < `needed` câu do bị `REJECTED` → **trả ít hơn**, không trộn set khác (giữ mạch liên kết). Response trả `requested` / `served`.

### 7.4 Ghi khi giao cho topic

Như hiện tại, thêm 2 điểm:

```
question (bản copy): topic_id = topic.id, pool_question_id = pool row id,
                     set_id = NULL, content_key = NULL, fingerprint = NULL,
                     archetype giữ nguyên (để đo diversity)
study_topic.set_id  = set_id
```

### 7.5 Review hậu kiểm

```
GET   /api/v1/admin/question-sets?skillKey=&difficulty=&scope=&page=
GET   /api/v1/admin/question-sets/{id}            → câu + cảnh báo trùng chéo
PATCH /api/v1/admin/question-sets/{id}/questions/{qid}  { reviewStatus }
GET   /api/v1/admin/question-sets/duplicates?skillKey=
```

Cảnh báo trùng chéo (chỉ đọc, không ghi cờ):

```sql
SELECT skill_key, content_key, COUNT(DISTINCT set_id)
FROM question
WHERE skill_key = ? AND set_id IS NOT NULL AND content_key IS NOT NULL
GROUP BY skill_key, content_key
HAVING COUNT(DISTINCT set_id) > 1
```

## 8. Cấu hình

```yaml
study:
  question-set:
    target-size: 10
    max-target-size: 20
    existing-text-limit: 30
    auto-create: true
    archetype-quota:
      DEFINITION: 2
      COMPARISON: 2
      APPLICATION: 2
      DEBUGGING: 1
      TRADE_OFF: 1
      SCENARIO: 2
```

## 9. Ngoài phạm vi

| Hạng mục | Lý do |
|---|---|
| Nối interview vào question bank | `MockInterviewServiceImpl` hiện không đọc bảng `question`; cần bảng `interview_session_question` (phase 2) |
| Hạn mức lượt dùng của user | D8 — chức năng riêng |
| Subtopic taxonomy | Đã thay bằng theme + archetype |
| Embedding dedup | Không có ngưỡng dùng chung cho cặp đồng nghĩa/trái nghĩa |
| Admin UI | Chỉ làm API |

## 10. Test plan

Unit test offline (harness hiện tại, không cần Docker/MySQL), **phải đăng ký trong `scripts/testcheck/run-extra-tests.ps1`**:

| Test | Phạm vi |
|---|---|
| `QuestionContentKeyTest` | bỏ stopword, giữ phủ định, synonym, đảo trật tự token ra cùng key |
| `QuestionSetServiceImplTest` | tạo set, quota lệch, archetype lạ, set_name unique |
| `QuestionSetRotationTest` | chọn set chưa dùng, `times_used` tăng, hết set → tạo mới |
| `QuestionPoolServiceImplTest` (sửa) | `acquire` trả nguyên bộ; set thiếu câu → `served < requested` |
| `StudyProgramServiceImplTest` (mới) | ghi `study_topic.set_id`, bản copy có `content_key = NULL` |

## 11. Rủi ro đã chấp nhận

| Rủi ro | Mức | Giảm thiểu |
|---|---|---|
| 2–5% trùng ý ở lần đầu của skill mới | trung bình | admin review hậu kiểm + `REJECTED` |
| Lần đầu/skill chậm 3–10s (sinh đồng bộ) | trung bình | admin tạo trước cho skill phổ biến |
| Set bị `REJECTED` nhiều → user nhận < 10 câu | thấp | response trả `requested`/`served`, admin bổ sung |
| AI không tuân quota archetype | trung bình | log WARN, không chặn; admin thấy khi review |
| 1 user kích hoạt nhiều lần sinh đồng bộ | trung bình | không xử lý trong spec này (D8) |
