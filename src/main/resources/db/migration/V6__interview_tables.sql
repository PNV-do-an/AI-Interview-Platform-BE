-- V6: Interview Preparation tables (Phase 1: user_cv + user_skill)

CREATE TABLE IF NOT EXISTS user_cv (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    file_name VARCHAR(255),
    file_path VARCHAR(500),
    raw_text TEXT,
    parsed_skills JSON,
    parsed_experience JSON,
    parsed_education JSON,
    suggested_positions JSON,
    knowledge_gaps JSON,
    target_position VARCHAR(100),
    target_level VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_cv_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_cv_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_skill (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    cv_id BIGINT NOT NULL,
    skill_name VARCHAR(100) NOT NULL,
    proficiency_level ENUM('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT') NOT NULL DEFAULT 'INTERMEDIATE',
    years_experience INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_skill_cv FOREIGN KEY (cv_id) REFERENCES user_cv(id) ON DELETE CASCADE,
    INDEX idx_user_skill_cv_id (cv_id),
    INDEX idx_user_skill_name (skill_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;