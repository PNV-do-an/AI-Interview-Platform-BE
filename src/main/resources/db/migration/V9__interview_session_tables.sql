-- V8: Interview Session tables (Phase 3: interview_session + interview_answer + interview_evaluation)

CREATE TABLE IF NOT EXISTS interview_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    program_id BIGINT,
    cv_id BIGINT,
    session_type ENUM('PRACTICE', 'FULL_SIMULATION') NOT NULL,
    target_position VARCHAR(100),
    total_questions INT NOT NULL,
    question_distribution JSON,
    time_limit_minutes INT,
    status ENUM('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED') DEFAULT 'NOT_STARTED',
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    current_question_index INT DEFAULT 0,
    overall_score DECIMAL(5,2),
    overall_feedback TEXT,
    question_pool JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_session_program FOREIGN KEY (program_id) REFERENCES study_program(id) ON DELETE SET NULL,
    CONSTRAINT fk_session_cv FOREIGN KEY (cv_id) REFERENCES user_cv(id) ON DELETE SET NULL,
    INDEX idx_session_user_id (user_id),
    INDEX idx_session_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS interview_answer (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    answer_order INT NOT NULL,
    user_text_answer TEXT,
    audio_file_path VARCHAR(500),
    audio_duration_seconds INT,
    audio_transcription TEXT,
    is_correct BOOLEAN,
    score DECIMAL(5,2),
    ai_feedback TEXT,
    eloquence_score DECIMAL(5,2),
    coherence_score DECIMAL(5,2),
    answer_submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_answer_session FOREIGN KEY (session_id) REFERENCES interview_session(id) ON DELETE CASCADE,
    CONSTRAINT fk_answer_question FOREIGN KEY (question_id) REFERENCES question(id) ON DELETE CASCADE,
    INDEX idx_answer_session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS interview_evaluation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL UNIQUE,
    total_score DECIMAL(5,2),
    accuracy_score DECIMAL(5,2),
    eloquence_score DECIMAL(5,2),
    technical_depth_score DECIMAL(5,2),
    communication_score DECIMAL(5,2),
    weak_skills JSON,
    strong_skills JSON,
    improvement_suggestions TEXT,
    detailed_feedback TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_evaluation_session FOREIGN KEY (session_id) REFERENCES interview_session(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;