-- V12: bộ câu hỏi (question set bank), study log và subtopic.
-- Nguồn: docs/superpowers/specs/2026-11-24-question-set-bank-design.md §4.9
--
-- Vì sao file này vẫn dùng guard INFORMATION_SCHEMA thay vì DDL thuần: DB dev hiện có
-- schema do ddl-auto dựng trước khi bật flyway, nên một phần bảng/cột có thể đã tồn tại.
-- Guard cho phép file chạy đúng trên cả DB đã có sẵn và DB trắng. Từ V13 trở đi dùng DDL thuần.
--
-- Không backfill dữ liệu cũ: question.set_id = NULL nghĩa là câu không còn được phục vụ
-- cho lượt học mới (spec §4.5).

-- 1. skill_subtopic phải tạo trước vì question_set có FK trỏ tới.
CREATE TABLE IF NOT EXISTS skill_subtopic (
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

-- 2. question_set — đơn vị sinh + phục vụ. Không đặt unique trên cell:
--    nhiều bộ trên cùng cell là hợp lệ.
CREATE TABLE IF NOT EXISTS question_set (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    set_name        VARCHAR(120) NOT NULL,
    scope           VARCHAR(16)  NOT NULL,
    skill_key       VARCHAR(100) NOT NULL,
    skill_name      VARCHAR(255) NULL,
    difficulty      VARCHAR(20)  NOT NULL,
    question_type   VARCHAR(20)  NOT NULL,
    language        VARCHAR(8)   NOT NULL,
    theme           VARCHAR(255) NOT NULL,
    subtopic_id     BIGINT       NOT NULL,
    archetype_quota JSON         NOT NULL,
    times_used      INT          NOT NULL DEFAULT 0,
    created_by      BIGINT       NULL,
    created_at      DATETIME     NULL,
    UNIQUE KEY uk_question_set_name (set_name),
    KEY idx_question_set_cell (skill_key, difficulty, question_type, language, times_used),
    KEY idx_question_set_subtopic (subtopic_id),
    CONSTRAINT fk_question_set_subtopic FOREIGN KEY (subtopic_id) REFERENCES skill_subtopic (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. user_set_usage — user đã nhận bộ nào, lúc nào. Nguồn dữ liệu của rotation.
CREATE TABLE IF NOT EXISTS user_set_usage (
    id           BIGINT   NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT   NOT NULL,
    set_id       BIGINT   NOT NULL,
    use_count    INT      NOT NULL DEFAULT 0,
    last_used_at DATETIME NOT NULL,
    available_at DATETIME NULL,
    UNIQUE KEY uk_user_set (user_id, set_id),
    KEY idx_user_set_available (user_id, available_at),
    CONSTRAINT fk_user_set_usage_set FOREIGN KEY (set_id) REFERENCES question_set (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Cột mới trên các bảng đang có (MySQL 8 không có ADD COLUMN IF NOT EXISTS
--    nên phải guard bằng INFORMATION_SCHEMA + prepared statement).
--
--    Guard phải kiểm cả sự tồn tại của bảng: trên DB trắng, V1..V10 không tạo question/
--    study_* nên ALTER sẽ chết. Thiếu bảng thì bỏ qua, V13 baseline tạo đủ bảng + cột.
SET @has_question := EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question');
SET @has_study_topic := EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'study_topic');
SET @has_study_answer := EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'study_answer');
SET @has_study_program := EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'study_program');

SET @sql := IF(@has_question = 0 OR EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question' AND COLUMN_NAME = 'set_id'), 'DO 0', 'ALTER TABLE question ADD COLUMN set_id BIGINT NULL');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(@has_question = 0 OR EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question' AND COLUMN_NAME = 'archetype'), 'DO 0', 'ALTER TABLE question ADD COLUMN archetype VARCHAR(20) NULL');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(@has_question = 0 OR EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question' AND COLUMN_NAME = 'content_key'), 'DO 0', 'ALTER TABLE question ADD COLUMN content_key VARCHAR(64) NULL');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(@has_question = 0 OR EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question' AND COLUMN_NAME = 'review_status'), 'DO 0', 'ALTER TABLE question ADD COLUMN review_status VARCHAR(16) NOT NULL DEFAULT ''AUTO_APPROVED''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Bản copy giao cho topic có content_key = NULL; MySQL cho phép nhiều NULL
-- trong unique index nên bản copy không vướng ràng buộc này.
-- Điều kiện phụ `skill_key` tồn tại: bảng question do V8 tạo ở DB trắng không có cột này,
-- tạo index sẽ chết; trường hợp đó để V13 lấp cột rồi tạo index.
SET @sql := IF(@has_question = 0 OR NOT EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question' AND COLUMN_NAME = 'skill_key') OR EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question' AND INDEX_NAME = 'uk_question_skill_content'), 'DO 0', 'CREATE UNIQUE INDEX uk_question_skill_content ON question (skill_key, content_key)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(@has_study_topic = 0 OR EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'study_topic' AND COLUMN_NAME = 'set_id'), 'DO 0', 'ALTER TABLE study_topic ADD COLUMN set_id BIGINT NULL');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- study_answer: chỉ 2 cột (D20, D21) — KHÔNG đổi UNIQUE (topic_id, question_id)
-- và KHÔNG đụng updateProgress.
SET @sql := IF(@has_study_answer = 0 OR EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'study_answer' AND COLUMN_NAME = 'ai_evaluation'), 'DO 0', 'ALTER TABLE study_answer ADD COLUMN ai_evaluation TEXT NULL, ADD COLUMN graded_at DATETIME NULL');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- study_program: cache nhận xét tổng (D23).
SET @sql := IF(@has_study_program = 0 OR EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'study_program' AND COLUMN_NAME = 'ai_summary'), 'DO 0', 'ALTER TABLE study_program ADD COLUMN ai_summary TEXT NULL, ADD COLUMN ai_summary_at DATETIME NULL');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
