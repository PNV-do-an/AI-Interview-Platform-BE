-- V11: vị trí đích (position_catalog) user chọn trước khi upload CV.
--
-- Cột nullable: CV cũ và client cũ không gửi targetPositionId vẫn hợp lệ. Không đặt FK constraint
-- ở đây vì giá trị được validate trong CvServiceImpl#resolveTargetPosition (404 nếu vị trí không
-- tồn tại hoặc đã bị ẩn).
--
-- Guard INFORMATION_SCHEMA: trước khi bật flyway, cột này có thể đã được ddl-auto: update tạo
-- từ UserCv.targetPositionId, nên migration phải chạy được trên cả hai trạng thái.

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_cv' AND COLUMN_NAME = 'target_position_id'), 'DO 0', 'ALTER TABLE user_cv ADD COLUMN target_position_id BIGINT NULL');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_cv' AND INDEX_NAME = 'idx_user_cv_target_position'), 'DO 0', 'CREATE INDEX idx_user_cv_target_position ON user_cv (target_position_id)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
