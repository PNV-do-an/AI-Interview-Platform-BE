# Implementation Plan — Question Set Bank (study program)

Spec nguồn: `docs/superpowers/specs/2026-11-24-question-set-bank-design.md`
Phạm vi: phase 1 (study program). Interview để phase 2.

## Nguyên tắc thực thi

- Làm theo phase, phase sau chỉ bắt đầu khi phase trước compile + test offline xanh.
- Mọi AI call nằm **ngoài transaction**; transaction chỉ bọc bước ghi.
- Harness offline: sửa `src/main` thì phải chạy `scripts\testcheck\rebuild-main.ps1 -SourceList '<file1>,<file2>'` trước khi chạy suite, nếu không test sẽ `NoSuchMethodError`.
- Test mới phải đăng ký vào `scripts\testcheck\run-extra-tests.ps1` (cả `$sources` và `$tests`).
- Không thêm file `.sql` vào `db/migration` (flyway đang tắt) — SQL chạy tay.

## Phát hiện cần dùng lại (không tạo mới)

| Đã có | Dùng cho |
|---|---|
| `model/entity/UserSkill.java` (`cv_id`, `skillName`, `proficiencyLevel`, `yearsExperience`) + `UserSkillRepository` | `GET /study/skill-options` đọc thẳng bảng này của CV mới nhất, **không parse lại CV** |
| `model/entity/PositionRequiredSkill.java` (`position_id`, `skill_name`, `skill_key`) | phase 2 dùng lại, chỉ thêm `weight`, `difficulty_floor` |
| `common/util/SkillKeys.java` | chuẩn hoá `skillKey` cho set/subtopic — PHẢI cùng hàm với `acquire` |
| pattern `@Value("${study.question-pool.refill-batch:20}")` | config mới dùng cùng kiểu, không bắt buộc sửa `application.yml` |

---

## Phase 1 — Schema & entity

### File mới

```
scripts/sql/2026-11-24-question-set-bank.sql          (nội dung §4.9 của spec)
src/main/java/com/aiinterview/platform/model/enums/QuestionArchetype.java
src/main/java/com/aiinterview/platform/model/enums/QuestionSetScope.java
src/main/java/com/aiinterview/platform/model/enums/QuestionReviewStatus.java
src/main/java/com/aiinterview/platform/model/enums/SubtopicSource.java
src/main/java/com/aiinterview/platform/model/enums/SubtopicStatus.java
src/main/java/com/aiinterview/platform/model/entity/QuestionSet.java
src/main/java/com/aiinterview/platform/model/entity/SkillSubtopic.java
src/main/java/com/aiinterview/platform/model/entity/UserSetUsage.java
src/main/java/com/aiinterview/platform/model/repository/QuestionSetRepository.java
src/main/java/com/aiinterview/platform/model/repository/SkillSubtopicRepository.java
src/main/java/com/aiinterview/platform/model/repository/UserSetUsageRepository.java
```

### File sửa

| File | Thay đổi |
|---|---|
| `model/entity/Question.java` | thêm `@ManyToOne set`, `archetype`, `contentKey`, `reviewStatus` |
| `model/entity/StudyTopic.java` | thêm `@ManyToOne set` (nullable) |
| `model/entity/StudyAnswer.java` | thêm `aiEvaluation` (TEXT), `gradedAt` |
| `model/entity/StudyProgram.java` | thêm `aiSummary` (TEXT), `aiSummaryAt` |
| `model/repository/QuestionRepository.java` | `findBySetIdAndReviewStatusNotOrderByArchetypeAscIdAsc(...)`, `countBySetIdAndReviewStatusNot(...)` |
| `model/repository/StudyAnswerRepository.java` | `@Query` aggregate avg theo `question.skillTag` và `question.archetype`; `findByTopicIdOrderBySubmittedAtAsc` |

### Verify

1. Chạy SQL trên DB dev (idempotent: `ADD COLUMN IF NOT EXISTS` không có ở MySQL 8 → dùng `INFORMATION_SCHEMA` guard hoặc chạy 1 lần và ghi chú).
2. App start không lỗi mapping.
3. `rebuild-main.ps1 -SourceList` cho 4 entity + 2 repository đã sửa.

---

## Phase 2 — `content_key`

### File mới

```
src/main/java/com/aiinterview/platform/common/util/QuestionContentKey.java
src/test/java/com/aiinterview/platform/common/util/QuestionContentKeyTest.java
```

`QuestionContentKey.of(String questionText)`:
`lowercase → bỏ dấu câu → tokenize → bỏ stopword (trừ danh sách bảo vệ) → map synonym → sort → join → sha256`.

Danh sách bảo vệ và synonym theo spec §5.1. Không phụ thuộc Spring → test thuần.

### Verify

`run-extra-tests.ps1` + class mới, case: đảo trật tự token ra cùng key; "not" không bị bỏ; "advantage"/"benefit" cùng key.

---

## Phase 3 — Sinh bộ (prompt + service)

### File sửa

| File | Thay đổi |
|---|---|
| `service/interview/AIQuestionService.java` | thêm `SetGeneration generateSet(SetGenerationContext ctx)` |
| `service/interview/impl/AIQuestionServiceImpl.java` | `buildSetPrompt` 5 khối (theme, archetype_quota, coherence, exclusions, coverage); parse root `theme` + `concept`; parse `archetype` từng câu; truyền `questionExplanation` vào prompt chấm (§13.4) |

### File mới

```
service/interview/QuestionSetService.java
service/interview/impl/QuestionSetServiceImpl.java
model/dto/SetGenerationContext.java   (record nội bộ: skillKey, skillName, difficulty, type, language, scope, targetSubtopic, existingThemes, existingTexts, quota)
```

`QuestionSetServiceImpl`:
- `resolveTargetSubtopic(skillKey, cell)`: theo §7.3.1 bước 1–4 (ADMIN trước, AI đề xuất sau).
- `allocateSetName(cell)`: `<Level> <Skill> <n>`, bắt `DataIntegrityViolationException` → thử `n+1` một lần.
- `generate(...)`: **không mở transaction**; trả object chưa persist để caller ghi trong TX ngắn.

### Verify

`QuestionSetServiceImplTest`: quota lệch vẫn lưu, `archetype` lạ → `DEFINITION`, `set_name` trùng → retry, skill có subtopic ADMIN → không tạo nhãn mới.

---

## Phase 4 — Rotation & `acquire`

### File mới

```
service/interview/QuestionSetRotationService.java
service/interview/impl/QuestionSetRotationServiceImpl.java
service/interview/AcquireResult.java        (questions, requested, served, setId)
```

### File sửa

| File | Thay đổi |
|---|---|
| `service/interview/QuestionPoolService.java` | `acquire(...)` trả `AcquireResult` |
| `service/interview/impl/QuestionPoolServiceImpl.java` | bỏ nhánh `computeGenerationCount`/`persistPoolRows`/`markUsed` cũ; gọi rotation → miss thì `QuestionSetService.generate` ngoài TX → ghi set + câu + usage |
| `model/specification/QuestionSpecifications.java` | giữ nguyên file, nhưng `cell(...)` không còn dùng trong `acquire` (dùng cho query khác nếu cần) |

Rotation 4 tầng theo §7.3, query JPQL `LEFT JOIN user_set_usage` với `CASE` 3 nhánh. Ghi usage: `available_at = NULL`, `last_used_at = now`.

### Verify

- `QuestionSetRotationTest` (mới): tầng 1 → 2 → 3 → 4 đúng thứ tự; `setCountHiệuDụng` bỏ set bị REJECTED toàn bộ.
- `QuestionPoolServiceImplTest` (viết lại): trả nguyên bộ, `served < requested` khi thiếu câu.

---

## Phase 5 — Luồng vào study program

### File sửa

| File | Thay đổi |
|---|---|
| `model/dto/request/GenerateProgramRequest.java` | thêm `List<SkillSelection> skills` (skillName + difficulty, 1–5) |
| `controller/interview/StudyProgramController.java` | thêm `GET /skill-options`; `POST /generate` dùng contract mới |
| `service/interview/StudyProgramService.java` + `impl/StudyProgramServiceImpl.java` | bỏ `@Transactional` ở `generateProgram`, dùng `TransactionTemplate` cho 3 mốc; bỏ `buildTopics`; topic title/description lấy từ skill + `set.theme` |

### File mới

```
service/interview/SkillOptionService.java + impl/SkillOptionServiceImpl.java
model/dto/response/SkillOptionResponse.java
```

`skill-options`: đọc `UserSkill` của CV mới nhất → dedupe `SkillKeys.normalize(skillName)` → sort `yearsExperience`/`proficiencyLevel` desc → cap 8 → 3 đầu `recommended = true` → map `suggestedDifficulty` từ `ProficiencyLevel`. Không có CV → mảng rỗng.

### Verify

- `SkillSelectionFlowTest`: cap 8, dedupe, `skills` rỗng → 400.
- `StudyProgramServiceImplTest` (mới): ghi `study_topic.set_id`, bản copy có `content_key = NULL`, `archetype` giữ nguyên.

---

## Phase 6 — Chấm điểm & log

### File sửa

| File | Thay đổi |
|---|---|
| `service/interview/impl/StudyProgramServiceImpl.java` | `submitStudyAnswer` tách AI ra ngoài TX; MCQ chấm local; `recordAnswer` ghi `aiEvaluation` + `gradedAt`; thêm `getHistory`, `getSummary`, `getTopicAnswers` |
| `service/interview/impl/AIQuestionServiceImpl.java` | `buildEvaluationPrompt` nhận thêm `explanation` |

### File mới

```
service/interview/ProgramSummaryService.java + impl
model/dto/response/StudyHistoryItemResponse.java
model/dto/response/ProgramSummaryResponse.java
model/dto/response/StudyTopicAnswersResponse.java
model/dto/response/StudyQuestionAnswerResponse.java
```

Summary: `stats` tính bằng aggregate query; `aiSummary` cache theo §13.6 (`ai_summary_at` vs `max(submitted_at)`); `answered < 3` → không gọi AI.

### Verify

- `AnswerGradingFlowTest`: MCQ không gọi AI (mock verify `never()`), ESSAY ghi `ai_evaluation`.
- `ProgramSummaryTest`: `bySkill`/`byArchetype` đúng; thêm answer mới → cache stale.

---

## Phase 7 — Admin API

### File mới

```
controller/admin/QuestionSetAdminController.java
controller/admin/SkillSubtopicAdminController.java
service/admin/QuestionSetAdminService.java + impl
```

Endpoints theo spec §7.1, §7.4, §7.7, §7.9 (10 route). Tất cả `@PreAuthorize("hasRole('ADMIN')")`.

### File sửa

| File | Thay đổi |
|---|---|
| `service/admin/impl/AdminPermissionServiceImpl.java` | thêm `ModuleDef("question-sets", ...)` như §7.8 |
| `docs/api-inventory.csv` | thêm 11 route mới + đổi contract `POST /study/generate` |

`AdminPermissionMatrixIntegrationTest` cần DB/HTTP thật → **không chạy được offline**. Sửa theo pattern hiện có, ghi rõ trong PR là "chưa verify offline".

### Verify

- Compile + `rebuild-main.ps1`.
- Đọc lại matrix JSON bằng mắt: module mới có đủ 5 action.

---

## Phase 8 — Đóng gói

1. Chạy toàn bộ suite offline: `run-core-tests.ps1`, `run-extra-tests.ps1`, `run-security-tests.ps1`, `run-auth-tests.ps1`.
2. Đối chiếu `api-inventory.csv` với controller thực tế.
3. Rà lại spec: nếu code lệch spec thì sửa spec trước, không để lệch âm thầm.
4. Cập nhật `§12.2` (dùng lại `position_required_skill` thay vì bảng mới) — đã sửa trong spec.

## Thứ tự commit

| Commit | Nội dung |
|---|---|
| 1 | Phase 1 schema + entity + repository |
| 2 | Phase 2 `content_key` + test |
| 3 | Phase 3 prompt + `QuestionSetService` + test |
| 4 | Phase 4 rotation + `acquire` + test |
| 5 | Phase 5 skill-options + generate contract + test |
| 6 | Phase 6 chấm điểm + log + test |
| 7 | Phase 7 admin API + matrix + api-inventory |
| 8 | Phase 8 rà soát |

## Rủi ro theo dõi khi code

| Rủi ro | Xử lý |
|---|---|
| Bỏ `@Transactional` khỏi `generateProgram` làm mất rollback topic | 3 TX ngắn bao đủ: program → (per skill) set+câu+topic+copy → totals |
| `UNIQUE (skill_key, content_key)` ném khi AI sinh trùng | lọc trùng trong batch trước khi save, bắt `DataIntegrityViolationException` → chạy lại rotation |
| `QuestionPoolServiceImplTest` cũ fail toàn bộ | viết lại theo `AcquireResult`, không giữ test cũ |
| `StudyProgramController` đổi contract | báo FE trước khi merge, cập nhật `api-inventory.csv` cùng commit |
