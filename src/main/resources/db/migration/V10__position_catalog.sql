-- V10: Position catalog for CV-driven recommendation (position_catalog + position_required_skill)
-- Chỉ tạo bảng; seed do PositionCatalogSeeder đảm nhận vì cần idempotent theo dữ liệu hiện có.

CREATE TABLE IF NOT EXISTS position_catalog (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(100) NOT NULL,
    slug VARCHAR(120) NOT NULL,
    level VARCHAR(50),
    description TEXT,
    jd_text TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_position_slug (slug),
    INDEX idx_position_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS position_required_skill (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    position_id BIGINT NOT NULL,
    skill_name VARCHAR(100) NOT NULL,
    skill_key VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_prs_position FOREIGN KEY (position_id) REFERENCES position_catalog(id) ON DELETE CASCADE,
    UNIQUE KEY uk_position_skill (position_id, skill_key),
    INDEX idx_prs_skill_key (skill_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
