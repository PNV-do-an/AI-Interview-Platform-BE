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
| D10 | Rotation **3 tầng + cooldown** thay vì loại trừ vĩnh viễn | mặc định 4 ngày |
| D11 | Cooldown **thích ứng theo điểm** đánh giá của user | điểm thấp → làm lại sớm; điểm cao → giãn ra |
| D12 | User **chủ động làm lại** bộ bất kỳ, bỏ qua cooldown | chức năng riêng của user, không cần admin |
| D13 | Admin **thêm câu thủ công** vào bộ | bù khi bộ thiếu câu do `REJECTED` |
| D14 | Set khoá theo **skill**, không gắn `position_id` | cùng skill dùng lại được cho nhiều position |
| D15 | Interview **ghép nhiều set** theo `position_skill`, không sinh set riêng | tận dụng bộ đã có, chỉ lấy subset |
| D16 | Thêm module `question-sets` vào permission matrix | matrix là code + test gọi HTTP thật |
| D17 | **Mọi bộ** đều có subtopic, kể cả bộ AI tự đặt nhãn | đo coverage được cho toàn bộ bank |
| D18 | `skill_subtopic` là **một** bảng, phân biệt bằng `source` | một identity, không phải hai thước đo |
| D19 | Trần hiệu dụng = `max(max-sets-per-cell, subtopicCount × sets-per-subtopic)` | tránh trần chặn coverage |

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
| `subtopic_id` | BIGINT NOT NULL, FK → `skill_subtopic.id` | mọi bộ đều thuộc đúng 1 subtopic, kể cả bộ AI tự đặt nhãn |
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

`SubtopicSource`: `ADMIN, AI_SUGGESTED`.

`SubtopicStatus`: `ACTIVE, MERGED`.

### 4.4 Cột mới trên `study_topic`

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `set_id` | BIGINT NULL | bộ đã dùng cho topic này — chỉ để truy vết; rotation đọc `user_set_usage` |

### 4.5 Dữ liệu cũ

Các row `question` đang tồn tại có `set_id = NULL` và **không được backfill** → chúng không còn được phục vụ cho lượt học mới (vẫn giữ cho `study_answer` lịch sử). Lý do: chúng thiếu `archetype`, `theme`, `content_key` nên không thoả prompt contract và quota.

**Đã xác nhận:** pool hiện tại chưa đáng kể, bỏ và sinh lại chấp nhận được → chi phí triển khai ≈ 0.

### 4.6 Sinh `set_name` không trùng

Tự sinh theo `"<Level> <Skill> <n>"` với `n` = số set hiện có của cell + 1. Có race condition khi 2 request đồng thời → dựa vào `UNIQUE (set_name)`, bắt lỗi và thử lại 1 lần với `n+1`.

### 4.7 Bảng mới `user_set_usage`

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `user_id` | BIGINT NOT NULL | |
| `set_id` | BIGINT NOT NULL, FK → `question_set.id` | |
| `use_count` | INT NOT NULL DEFAULT 0 | số lần user đã nhận bộ này |
| `last_used_at` | DATETIME NOT NULL | lần cuối nhận bộ |
| `available_at` | DATETIME NULL | mốc được cấp lại; `NULL` = đang làm dở, không cấp lại |

`UNIQUE (user_id, set_id)`. Index `(user_id, available_at)`.

`available_at` được tính **một lần khi topic hoàn thành** (trong `updateProgress`) từ điểm trung bình của các câu thuộc bộ đó:

| Điểm TB của bộ | Cooldown |
|---|---|
| `null` (chưa trả lời) | `redo-after-days` = 4 ngày |
| `< score-low-threshold` (6) | `redo-after-days-low-score` = 1 ngày |
| từ 6 đến < 8 | `redo-after-days` = 4 ngày |
| `>= score-high-threshold` (8) | `redo-after-days-high-score` = 14 ngày |

Lý do dùng cột thay vì tính trong query: nhiều mốc khác nhau cho từng bộ thì không thể viết bằng một `INTERVAL` chung trong JPQL.

Đây là nguồn dữ liệu của rotation. `question_set` là tài sản dùng chung (không có `user_id`), nên quan hệ "user này đã dùng bộ nào, lúc nào" phải nằm ở bảng riêng.

### 4.8 Bảng mới `skill_subtopic`

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `skill_key` | VARCHAR(100) NOT NULL | `SkillKeys.normalize(skillName)` |
| `subtopic_key` | VARCHAR(100) NOT NULL | identity, không đổi sau khi tạo |
| `subtopic_name` | VARCHAR(120) NOT NULL | hiển thị; admin sửa được |
| `source` | VARCHAR(16) NOT NULL | `ADMIN` / `AI_SUGGESTED` |
| `status` | VARCHAR(16) NOT NULL DEFAULT `ACTIVE` | `ACTIVE` / `MERGED` |
| `sort_order` | INT NOT NULL DEFAULT 0 | thứ tự ưu tiên sinh |
| `created_at` | DATETIME | |

`UNIQUE (skill_key, subtopic_key)`. Index `(skill_key, status, sort_order)`.

Đây là **nguồn duy nhất** của khái niệm "mảng kiến thức". Không có cột `subtopic` thứ hai trên `question_set` — nếu có 2 cột (một cho admin, một cho AI đề xuất) thì sẽ có 2 thước đo coverage không so sánh được. Identity là một; **nguồn gốc là thuộc tính** (`source`).

- `ADMIN`: admin khai báo → là ràng buộc, AI phải chọn trong danh sách.
- `AI_SUGGESTED`: AI tự đặt nhãn khi skill chưa có taxonomy → chỉ là đề xuất, admin đổi tên hoặc gộp.

### 4.9 Migration

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

CREATE TABLE user_set_usage (
    id           BIGINT   NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT   NOT NULL,
    set_id       BIGINT   NOT NULL,
    use_count    INT      NOT NULL DEFAULT 0,
    last_used_at DATETIME NOT NULL,
    available_at DATETIME NULL,
    UNIQUE KEY uk_user_set (user_id, set_id),
    KEY idx_user_set_available (user_id, available_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE skill_subtopic (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    skill_key     VARCHAR(100) NOT NULL,
    subtopic_key  VARCHAR(100) NOT NULL,
    subtopic_name VARCHAR(120) NOT NULL,
    source        VARCHAR(16)  NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    sort_order    INT          NOT NULL DEFAULT 0,
    created_at    DATETIME     NULL,
    UNIQUE KEY uk_skill_subtopic (skill_key, subtopic_key),
    KEY idx_skill_subtopic_list (skill_key, status, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE question_set
    ADD COLUMN subtopic_id BIGINT NOT NULL AFTER theme,
    ADD KEY idx_question_set_subtopic (subtopic_id),
    ADD CONSTRAINT fk_question_set_subtopic FOREIGN KEY (subtopic_id) REFERENCES skill_subtopic (id);
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

`buildQuestionPrompt` mở rộng, bắt buộc 5 khối:

| Khối | Nội dung |
|---|---|
| `theme` | bối cảnh bắt buộc, mọi câu phải nằm trong bối cảnh này |
| `archetype_quota` | số câu mỗi dạng + định nghĩa từng dạng |
| `coherence` | "questions revolve around the theme, depth increases progressively" |
| `exclusions` | tối đa 30 `question_text` đã có trong cell + "do not repeat these concepts" |
| `coverage` | `target_subtopic` + `existing_concepts` của skill + "chỉ đề xuất concept mới nếu thật sự khác" |

Output JSON thêm trường `archetype` cho mỗi câu. Server validate:

- `archetype` không thuộc enum → log WARN, mặc định `DEFINITION`.
- Tổng theo archetype lệch quota → log WARN, **vẫn lưu** (không regenerate, không chặn user).

### 6.1 Theme do AI chọn

Theme **và** `concept` (nhãn subtopic ngắn, 1–4 từ) sinh **trong cùng 1 call** với câu hỏi, trả ở root của JSON output. Prompt truyền vào `skill`, `difficulty`, `scope`, danh sách theme đã dùng trong cell và `existing_concepts` của skill.

Quy tắc chọn `concept`:

| Tình huống | Hành vi |
|---|---|
| Skill có subtopic `ADMIN` | Prompt gửi danh sách key, AI **phải chọn 1** trong đó |
| Skill chưa có subtopic | AI tự đặt nhãn; chấm dứt phân mảnh bằng `existing_concepts` + normalize |
| Nhãn normalize trùng key đã có | Tái dùng row đó, không tạo mới |

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
  { scope, skillName, difficulty, questionType, language, targetSize, subtopicKey?, setNames? }
→ resolve skillKey = SkillKeys.normalize(skillName)      ← PHẢI trùng hàm với acquire
→ chọn subtopic đích (§7.3.1): truyền `subtopicKey` hoặc đề xuất theo quy tắc
→ AI chọn theme + concept
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

1. Chọn bộ theo 4 tầng ưu tiên (§7.3). Có → dùng luôn, không gọi AI.
2. Hết bộ + cell chưa đạt trần → **sinh bộ mới** theo subtopic ưu tiên (§7.3.1),
   `created_by = NULL`, `set_name` tự sinh (`"<Level> <Skill> <n>"` với `n` = số set hiện có của cell + 1).
3. AI lỗi → ném `AIServiceException` (giữ hành vi hiện tại của `generateQuestions`).

**Gọi AI phải nằm ngoài transaction** — đây là lỗi hiện có cần sửa trong task này:

- `@Transactional` **không** giữ phiên AI. Nó chỉ giữ **1 JDBC connection** của pool (mặc định 10) + row lock. Gọi Gemini là HTTP ra ngoài, không liên quan DB.
- Giữ connection suốt 3–10s chờ AI là lãng phí: 10 request đồng thời là mọi API khác cũng bị chặn.
- Transaction **vẫn cần** cho bước ghi (rollback topic + bản copy khi lỗi) — chỉ không được bọc cả đoạn chờ AI.

Thứ tự mới (không tạo set rỗng, nên **không cần bù trừ**):

1. TX ngắn: rotation (§7.3). Có set → đọc câu, cập nhật usage, xong. **Không gọi AI.**
2. Chưa có set → **không ghi gì** → gọi AI ngoài TX. AI lỗi ⇒ DB sạch, không cần rollback gì.
3. TX ngắn: tạo `question_set` + `question` + `study_topic` + bản copy trong **một** lần ghi → rollback vẫn nguyên vẹn như hiện tại.

Cài đặt: `TransactionTemplate` cho bước 1 và 3; bước 2 nằm ngoài mọi transaction.

**Race (2 user cùng cell, cùng lúc chưa có set):** cả hai gọi AI → 2 set. `UNIQUE (skill_key, content_key)` làm request sau fail ở bước 3 → bắt `DataIntegrityViolationException`, chạy lại rotation để dùng set của request thắng. Hệ quả: tốn 1 lần AI dư, dữ liệu vẫn đúng.

### 7.3 Chọn bộ (rotation)

**Vì sao cần subquery:** `question_set` là tài sản **dùng chung**, không có cột `user_id`. Quan hệ "user này đã dùng bộ nào" không nằm trên bộ mà nằm ở bảng `user_set_usage`. Nên phải: lấy danh sách bộ của user từ bảng đó → **loại trừ** khỏi tập bộ của cell.

- `qs.id NOT IN (subquery)` = "bộ nào không nằm trong danh sách bộ user đã dùng".
- Subquery đọc `user_set_usage` và lọc `user_id = ?` — trả lời "bộ nào là của user này".
- Với cách cố định ban đầu, `st.set_id IS NOT NULL` là bắt buộc: nếu subquery trả về `NULL`, `NOT IN` sẽ trả **rỗng toàn bộ** (bẫy kinh điển của SQL). Nay subquery đọc `user_set_usage` (cột NOT NULL) nên bẫy này không còn, nhưng vẫn phải nhớ khi sửa query.

**Loại trừ vĩnh viễn là sai nghiệp vụ** — user phải làm lại được. Nhưng **sinh mới trước khi cấp lại**:

| Tầng | Điều kiện | Hành động |
|---|---|---|
| 1 | có set user chưa từng dùng | cấp (0 chi phí AI) |
| 2 | hết set, `setCountHiệuDụng < trần` | **sinh bộ mới** (§7.3.1) |
| 3 | hết set, đạt trần, có set `available_at <= now` | cấp bộ `available_at` cũ nhất |
| 4 | hết set, đạt trần, tất cả còn cooldown | cấp bộ `available_at` sớm nhất |

Set bị `REJECTED` toàn bộ **không tính** vào `setCountHiệuDụng` — tránh cảnh cell "đầy" nhưng không cấp được câu.

```sql
SELECT qs.id
FROM question_set qs
LEFT JOIN user_set_usage u ON u.set_id = qs.id AND u.user_id = :userId
WHERE qs.skill_key = :skillKey
  AND qs.difficulty = :difficulty
  AND qs.question_type = :type
  AND qs.language = :language
ORDER BY
  CASE WHEN u.id IS NULL THEN 0
       WHEN u.available_at IS NOT NULL AND u.available_at <= :now THEN 1
       ELSE 2 END,
  COALESCE(u.available_at, '1970-01-01') ASC,
  qs.times_used ASC,
  qs.id ASC
LIMIT 1
```

Cài đặt bằng JPQL/Criteria trong repository, không dùng native query.

Query trên chỉ chạy cho tầng 1, 3, 4 — tầng 2 được quyết định **trước** khi query (§7.3.1).

### 7.3.1 Sinh bộ mới — chọn subtopic

```
setCountHiệuDụng = số set của cell có ≥1 câu chưa bị REJECTED
trần              = max(max-sets-per-cell, subtopicCount(ACTIVE) × sets-per-subtopic)

1. Skill có subtopic ACTIVE nguồn ADMIN
   → chọn subtopic chưa đủ sets-per-subtopic bộ (sort_order ASC)
   → AI KHÔNG được tự đặt nhãn mới
2. Skill chưa có subtopic ADMIN
   → AI đề xuất concept + theme trong cùng 1 call
   → normalize concept bằng SkillKeys.normalize; trùng key đã có → tái dùng row
3. Đã cover hết subtopic và setCountHiệuDụng < trần
   → sinh bộ theme tự do, gắn vào subtopic ít bộ nhất (tăng độ sâu)
4. setCountHiệuDụng >= trần
   → không sinh, rơi xuống tầng 3/4
```

Nhãn AI đặt có `source = AI_SUGGESTED`; admin đổi tên hoặc gộp về subtopic chính thức (§7.9).

Sau khi chọn — ghi **cả 2 nơi**:

```
user_set_usage  upsert (user_id, set_id): use_count += 1, last_used_at = now, available_at = NULL
question_set    times_used += 1
question        WHERE set_id = ? AND review_status <> 'REJECTED' ORDER BY archetype, id
```

`available_at = NULL` khi vừa cấp (đang làm dở); chỉ được tính khi topic hoàn thành (§4.7).

Nếu set còn < `needed` câu do bị `REJECTED` → **trả ít hơn**, không trộn set khác (giữ mạch liên kết). Response trả `requested` / `served`. Admin bù bằng API §7.4.

### 7.4 Admin thêm câu thủ công

```
POST /api/v1/admin/question-sets/{id}/questions
  { questionText, correctAnswer, explanation, archetype, options? }
```

- `content_key` = tính lại từ `questionText`; trùng với câu đã có trong cell → **409**, không lưu.
- `review_status = APPROVED` (admin tạo thì duyệt luôn), `set_id` = bộ đích, `topic_id = NULL`.
- `fingerprint` tính như cũ → vẫn được bảo vệ bởi unique toàn cục.
- Không cập nhật `archetype_quota` — quota chỉ là ảnh chụp lúc AI sinh.

### 7.5 User chủ động làm lại bộ

```
POST /api/v1/study/sets/{setId}/redo
→ tạo study_topic mới trong program hiện tại gắn đúng set đó
```

- Bỏ qua cooldown: đặt `available_at = now` trước khi cấp.
- Không đụng `user_set_usage.use_count` theo chiều tăng bất thường — vẫn +1 mỗi lần cấp.
- Hạn mức số lần gọi API này là chức năng riêng (D8), spec này không chặn.

### 7.6 Ghi khi giao cho topic

Như hiện tại, thêm 2 điểm:

```
question (bản copy): topic_id = topic.id, pool_question_id = pool row id,
                     set_id = NULL, content_key = NULL, fingerprint = NULL,
                     archetype giữ nguyên (để đo diversity)
study_topic.set_id  = set_id
```

### 7.7 Review hậu kiểm

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

### 7.8 Permission matrix

`AdminPermissionServiceImpl.MODULES` là **code**, không phải data (spec 006) — matrix chỉ mô tả các `@PreAuthorize` thật, và `AdminPermissionMatrixIntegrationTest` gọi HTTP thật để phát hiện lệch.

Phải thêm 1 module:

```java
new ModuleDef("question-sets", "Bộ câu hỏi", false, Map.of(
        "VIEW",     roles(Role.ROLE_ADMIN),
        "CREATE",   roles(Role.ROLE_ADMIN),
        "UPDATE",   roles(Role.ROLE_ADMIN),   // PATCH reviewStatus, POST thêm câu
        "DELETE",   roles(),
        "APPROVE",  roles(Role.ROLE_ADMIN)))
```

Và thêm assertion vào integration test theo đúng pattern hiện có: ADMIN × VIEW ⇒ 200, USER × VIEW ⇒ 403.

### 7.9 Subtopic & coverage

```
PUT   /api/v1/admin/skills/{skillKey}/subtopics   { subtopics: [{ key, name }] }
GET   /api/v1/admin/question-sets/coverage?skillKey=
PATCH /api/v1/admin/subtopics/{id}                { name }      // đổi tên hiển thị
POST  /api/v1/admin/subtopics/{id}/merge          { targetId }  // gộp nhãn AI về subtopic chính thức
```

`GET .../coverage` trả:

| subtopic | source | sets | questions | trạng thái |
|---|---|---|---|---|
| networking | ADMIN | 2 | 20 | đủ |
| volumes | ADMIN | 0 | 0 | **MISSING** |
| "CI pipeline" | AI_SUGGESTED | 1 | 10 | cần admin xem lại |

Quy tắc:

- `subtopic_key` là **identity**, không đổi sau khi tạo (đổi sẽ phá coverage đã đo).
- Đổi tên chỉ sửa `subtopic_name`.
- Gộp: repoint `question_set.subtopic_id` sang đích, row nguồn `status = MERGED`.
- `PUT .../subtopics` chỉ ghi `source = ADMIN`; không xoá row đang được `question_set` tham chiếu.

## 8. Cấu hình

```yaml
study:
  question-set:
    target-size: 10
    max-target-size: 20
    existing-text-limit: 30
    auto-create: true
    redo-after-days: 4
    redo-after-days-low-score: 1
    redo-after-days-high-score: 14
    score-low-threshold: 6
    score-high-threshold: 8
    max-sets-per-cell: 8
    sets-per-subtopic: 1
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
| `QuestionSetRotationTest` | 3 tầng ưu tiên, `available_at` tới hạn → cấp lại, hết bộ → tạo mới |
| `SetCooldownTest` | điểm TB < 6 → 1 ngày; 6–8 → 4 ngày; ≥ 8 → 14 ngày; chưa trả lời → 4 ngày |
| `AdminAddQuestionTest` | thêm câu vào bộ, `review_status = APPROVED`, trùng `content_key` → 409 |
| `UserRedoSelfServiceTest` | redo bỏ qua cooldown, `available_at` = now |
| `AdminPermissionMatrixIntegrationTest` (sửa) | module `question-sets`: ADMIN × VIEW ⇒ 200, USER × VIEW ⇒ 403 |
| `SubtopicCoverageTest` | skill có taxonomy → AI không tạo nhãn mới; trần = subtopic × sets-per-subtopic; coverage đếm đúng `MISSING` |
| `QuestionPoolServiceImplTest` (sửa) | `acquire` trả nguyên bộ; set thiếu câu → `served < requested` |
| `StudyProgramServiceImplTest` (mới) | ghi `study_topic.set_id`, bản copy có `content_key = NULL` |

## 11. Rủi ro đã chấp nhận

| Rủi ro | Mức | Giảm thiểu |
|---|---|---|
| Trùng ý 2–5% **từ bộ thứ 2 trở đi** của một cell | trung bình | `content_key` + `exclusions` + admin `REJECTED` |
| Bộ **đầu tiên** của skill mới: trùng = 0 theo cấu trúc | thấp | trong cell chưa có bộ nào khác để trùng |
| Lần đầu/skill chậm 3–10s (sinh đồng bộ) | trung bình | admin tạo trước cho skill phổ biến |
| Set bị `REJECTED` nhiều → user nhận < 10 câu | thấp | response trả `requested`/`served`, admin bổ sung |
| AI không tuân quota archetype | trung bình | log WARN, không chặn; admin thấy khi review |
| 1 user kích hoạt nhiều lần sinh đồng bộ | trung bình | không xử lý trong spec này (D8) |
| Gọi AI giữ connection của pool | **cao** | gọi AI **ngoài transaction** (§7.2) |
| AI tự đặt nhãn subtopic bị phân mảnh ("networking" vs "container networking") | trung bình | normalize + `existing_concepts` trong prompt + admin gộp |
| Admin khai nhiều subtopic hơn trần → chi phí AI tăng | thấp | trần tự nới theo số subtopic (D19), admin thấy ngay trong coverage |

## 12. Chuẩn bị cho interview (phase 2)

### 12.1 Nguyên tắc: set khoá theo skill, không theo position

Hai vị trí cùng cần "Docker mức basic" thì dùng **chung** một bộ. Vì vậy `question_set` **không có** `position_id`. Position chỉ quyết định **tập skill nào** và **trọng số** của từng skill.

Kết quả: mọi thiết kế ở §4 đã đủ cho interview, không phải migrate cột.

### 12.2 Bảng mới `position_skill` (phase 2)

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `position_id` | BIGINT NOT NULL, FK → `position.id` | |
| `skill_key` | VARCHAR(100) NOT NULL | `SkillKeys.normalize(skillName)` |
| `weight` | INT NOT NULL DEFAULT 1 | trọng số phân bổ câu |
| `difficulty_floor` | VARCHAR(20) NULL | mức tối thiểu cho skill này |

`UNIQUE (position_id, skill_key)`.

Nguồn dữ liệu: admin gán qua `PUT /api/v1/admin/positions/{id}/skills` (phase 2), hoặc AI đề xuất rồi admin sửa.

**Không** lấy từ `study_topic.skill_tag`: đó là kết quả AI chọn cho từng user, không tất định, và chỉ có sau khi user đã học.

### 12.3 Ghép một buổi interview

```
input: positionId, difficulty, totalQuestions (vd 25), language
1. position_skill → danh sách (skill_key, weight), lọc difficulty_floor <= difficulty
2. phân bổ: N_i = round(total * w_i / sum(w)); làm tròn dư dồn về skill weight cao nhất
3. mỗi skill: chọn 1 set bằng rotation §7.3 với scope = INTERVIEW, cùng cell
            → không đủ set thì tạo mới (đồng bộ như §7.2)
4. trong set: chọn N_i câu theo archetype quota, ưu tiên câu chưa dùng cho user
5. ghi interview_session_question(session_id, question_id)
```

Thiếu bộ cho một skill → hạ `N_i` và bù sang skill kế tiếp; vẫn thiếu → giảm `totalQuestions` và trả `requested`/`served` như study.

Rotation của interview dùng chung `user_set_usage` — không cần bảng riêng.

### 12.4 Phase 1 phải làm sẵn để phase 2 không phải migrate

| Việc | Vì sao |
|---|---|
| `question_set.scope` có `INTERVIEW` | chốt enum ngay |
| `question.archetype` ghi cho mọi câu thuộc set | interview lọc theo archetype |
| `set_name` cho phép `Interview Backend Intern` | admin review |
| `user_set_usage` không gắn scope | dùng chung cho cả hai |

### 12.5 Không đụng trong phase 1

`MockInterviewServiceImpl`, bảng `interview_session_question`, `position_skill`, API gán skill cho position.
