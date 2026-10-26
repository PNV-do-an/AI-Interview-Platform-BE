-- Spec 011: ảnh đại diện + phiên đăng nhập.
-- Chạy tay khi cần (spring.flyway.enabled=false, ddl-auto=update tự sinh từ entity);
-- file này là bản tham chiếu cho DB đã có dữ liệu và để review.

ALTER TABLE users
    ADD COLUMN avatar LONGTEXT NULL;

CREATE TABLE IF NOT EXISTS user_session (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id   VARCHAR(36)  NOT NULL,
    user_id      BIGINT       NOT NULL,
    device_label VARCHAR(120) NULL,
    ip_address   VARCHAR(64)  NULL,
    user_agent   VARCHAR(512) NULL,
    created_at   DATETIME     NOT NULL,
    last_seen_at DATETIME     NOT NULL,
    expires_at   DATETIME     NOT NULL,
    revoked_at   DATETIME     NULL,
    UNIQUE KEY uk_session_session_id (session_id),
    KEY idx_session_user_id (user_id),
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES users (id)
);
