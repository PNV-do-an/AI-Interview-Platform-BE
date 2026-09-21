-- V7: Study Program tables (Phase 2: study_program + study_topic + question + ai_question_cache)

CREATE TABLE IF NOT EXISTS study_program (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    cv_id BIGINT,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    program_type ENUM('STUDY', 'INTERVIEW_PREP') NOT NULL DEFAULT 'STUDY',
    target_position VARCHAR(100),
    difficulty_level ENUM('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'MIXED') DEFAULT 'MIXED',
    status ENUM('ACTIVE', 'COMPLETED', 'ARCHIVED') DEFAULT 'ACTIVE',
    total_topics INT DEFAULT 0,
    completed_topics INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    CONSTRAINT fk_program_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_program_cv FOREIGN KEY (cv_id) REFERENCES user_cv(id) ON DELETE SET NULL,
    INDEX idx_program_user_id (user_id),
    INDEX idx_program_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS study_topic (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    program_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    topic_order INT NOT NULL,
    difficulty ENUM('BEGINNER', 'INTERMEDIATE', 'ADVANCED') NOT NULL,
    skill_tag VARCHAR(100),
    total_questions INT DEFAULT 0,
    completed_questions INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_topic_program FOREIGN KEY (program_id) REFERENCES study_program(id) ON DELETE CASCADE,
    INDEX idx_topic_program_id (program_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS question (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    topic_id BIGINT,
    skill_tag VARCHAR(100) NOT NULL,
    difficulty ENUM('BEGINNER', 'INTERMEDIATE', 'ADVANCED') NOT NULL,
    question_type ENUM('MCQ', 'SHORT_ANSWER', 'CODE', 'AUDIO') NOT NULL,
    question_text TEXT NOT NULL,
    options JSON,
    correct_answer TEXT,
    explanation TEXT,
    ai_generated BOOLEAN DEFAULT TRUE,
    source VARCHAR(50) DEFAULT 'AI',
    cache_key VARCHAR(255),
    times_used INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_question_topic FOREIGN KEY (topic_id) REFERENCES study_topic(id) ON DELETE SET NULL,
    INDEX idx_question_skill (skill_tag),
    INDEX idx_question_difficulty (difficulty),
    INDEX idx_question_topic (topic_id),
    INDEX idx_question_cache_key (cache_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_question_cache (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    cache_key VARCHAR(255) NOT NULL UNIQUE,
    position VARCHAR(100),
    skill_tag VARCHAR(100),
    difficulty ENUM('BEGINNER', 'INTERMEDIATE', 'ADVANCED'),
    question_type ENUM('MCQ', 'SHORT_ANSWER', 'CODE', 'AUDIO'),
    question_text TEXT NOT NULL,
    options JSON,
    correct_answer TEXT,
    explanation TEXT,
    times_used INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_cache_position (position),
    INDEX idx_cache_skill (skill_tag),
    INDEX idx_cache_difficulty (difficulty),
    INDEX idx_cache_skill_difficulty_type (skill_tag, difficulty, question_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;