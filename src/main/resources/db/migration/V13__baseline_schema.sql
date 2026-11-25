-- V13: lấp phần schema mà V1..V12 còn thiếu trên database trắng.
--
-- Sinh tự động từ information_schema của DB dev đang chạy, bằng công cụ
-- target/schema-dump/CheckMigrations.java. Mọi câu lệnh đều có guard nên trên DB
-- đã đủ schema file này là no-op.
--
-- Đây là migration "ensure" cuối cùng; V14 trở đi viết DDL thuần. Khi có baseline
-- thật (reset DB rồi gộp V1..V13) thì xoá được file này.

SET FOREIGN_KEY_CHECKS = 0;

-- 1. Bảng còn thiếu: lấy nguyên DDL từ DB thật.
CREATE TABLE IF NOT EXISTS `application_status_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `acted_at` datetime(6) DEFAULT NULL,
  `from_status` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `note` text COLLATE utf8mb4_unicode_ci,
  `to_status` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `actor_user_id` bigint DEFAULT NULL,
  `application_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `IDXrm6mpns2hjdhttvke2fcd1ghj` (`application_id`,`acted_at`),
  KEY `FKs9oq6j94mp3sfo0utx6jwrnuu` (`actor_user_id`),
  CONSTRAINT `FKfdvnrm195jdlti0syxo6qah6h` FOREIGN KEY (`application_id`) REFERENCES `applications` (`id`),
  CONSTRAINT `FKs9oq6j94mp3sfo0utx6jwrnuu` FOREIGN KEY (`actor_user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `applications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `cover_letter` text COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(6) DEFAULT NULL,
  `feedback` text COLLATE utf8mb4_unicode_ci,
  `score` int DEFAULT NULL,
  `status` enum('PENDING','SHORTLISTED','INTERVIEWED','ACCEPTED','REJECTED','CANCELLED','SCHEDULED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `candidate_id` bigint NOT NULL,
  `job_id` bigint NOT NULL,
  `slot_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_b7rtl5dgagcow2n7cyklerqmn` (`slot_id`),
  KEY `FK32iahkg1fqci0pt76uql80nj7` (`candidate_id`),
  KEY `FKcnotu5pv0ovnaildnl6js5ooe` (`job_id`),
  CONSTRAINT `FK32iahkg1fqci0pt76uql80nj7` FOREIGN KEY (`candidate_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKcnotu5pv0ovnaildnl6js5ooe` FOREIGN KEY (`job_id`) REFERENCES `job_descriptions` (`id`),
  CONSTRAINT `FKpxho5jbrat7uq43gutv7p8t55` FOREIGN KEY (`slot_id`) REFERENCES `interview_slots` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `bug_reports` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category` enum('BUG','FEATURE','OTHER') COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `description` varchar(5000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `replied_at` datetime(6) DEFAULT NULL,
  `reply` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` enum('OPEN','IN_PROGRESS','RESOLVED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `replied_by` bigint DEFAULT NULL,
  `reporter_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKhn1htso7mfspf3f46l07o0vy5` (`replied_by`),
  KEY `FKkh0ybgasqj65g7pu64gj8x2w1` (`reporter_id`),
  CONSTRAINT `FKhn1htso7mfspf3f46l07o0vy5` FOREIGN KEY (`replied_by`) REFERENCES `users` (`id`),
  CONSTRAINT `FKkh0ybgasqj65g7pu64gj8x2w1` FOREIGN KEY (`reporter_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `candidate_profiles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `career_goal` text COLLATE utf8mb4_unicode_ci,
  `cv_file_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `experience_years` int DEFAULT NULL,
  `skills` text COLLATE utf8mb4_unicode_ci,
  `summary` text COLLATE utf8mb4_unicode_ci,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_m7asead9kaplln9cdupjat1cq` (`user_id`),
  CONSTRAINT `FKn7b2se0y378uox9e3aw2bjg13` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cover_letters` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` text COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(6) DEFAULT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `candidate_id` bigint NOT NULL,
  `job_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK5rwdil49up7u4sgx1pgbeqjyg` (`candidate_id`),
  KEY `FK6196w0ex2wdii5i6u6qvkda8k` (`job_id`),
  CONSTRAINT `FK5rwdil49up7u4sgx1pgbeqjyg` FOREIGN KEY (`candidate_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FK6196w0ex2wdii5i6u6qvkda8k` FOREIGN KEY (`job_id`) REFERENCES `job_descriptions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `script` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `interview_answers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `answer_text` varchar(10000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `answered_at` datetime(6) DEFAULT NULL,
  `confirmed_at` datetime(6) DEFAULT NULL,
  `correct_answer` text COLLATE utf8mb4_unicode_ci,
  `duration_sec` int DEFAULT NULL,
  `feedback` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `options_json` text COLLATE utf8mb4_unicode_ci,
  `question` varchar(2000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `question_index` int NOT NULL,
  `score` int DEFAULT NULL,
  `selected_option_index` int DEFAULT NULL,
  `session_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKjeqvvamvdarrcbswn6kkiuym9` (`session_id`),
  CONSTRAINT `FKjeqvvamvdarrcbswn6kkiuym9` FOREIGN KEY (`session_id`) REFERENCES `interview_sessions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `interview_report` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `chuyen_mon` int NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `du_an` int NOT NULL,
  `improvements` text COLLATE utf8mb4_unicode_ci,
  `muc_khop` int NOT NULL,
  `overall_score` int NOT NULL,
  `rubric_version` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `strengths` text COLLATE utf8mb4_unicode_ci,
  `truyen_dat` int NOT NULL,
  `tu_duy` int NOT NULL,
  `session_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_pevyr8qm2arws90erkjmiupji` (`session_id`),
  CONSTRAINT `FK81trh10275wjkacq2l9pyyt5m` FOREIGN KEY (`session_id`) REFERENCES `interview_sessions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `interview_session_questions` (
  `session_id` bigint NOT NULL,
  `correct_option_index` int NOT NULL,
  `options` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `question` varchar(2000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `idx` int NOT NULL,
  PRIMARY KEY (`session_id`,`idx`),
  CONSTRAINT `FKbox2xjm70mrtgg0biwwy8a1og` FOREIGN KEY (`session_id`) REFERENCES `interview_sessions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `interview_sessions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `current_question_index` int NOT NULL,
  `mode` enum('MOCK','KNOWLEDGE','AVATAR') COLLATE utf8mb4_unicode_ci NOT NULL,
  `overall_score` int DEFAULT NULL,
  `question_count` int NOT NULL DEFAULT '5',
  `status` enum('IN_PROGRESS','COMPLETED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `topic` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `candidate_id` bigint NOT NULL,
  `job_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKdgvxiy3lscety5o48nl8tt4o1` (`candidate_id`),
  KEY `FKbftxb58g69ngiu23mp4nt8f3j` (`job_id`),
  CONSTRAINT `FKbftxb58g69ngiu23mp4nt8f3j` FOREIGN KEY (`job_id`) REFERENCES `job_descriptions` (`id`),
  CONSTRAINT `FKdgvxiy3lscety5o48nl8tt4o1` FOREIGN KEY (`candidate_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `interview_slots` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `booked_at` datetime(6) DEFAULT NULL,
  `cancelled` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `end_time` datetime(6) NOT NULL,
  `note` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `start_time` datetime(6) NOT NULL,
  `booked_by` bigint DEFAULT NULL,
  `employer_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKmjtm3hilo15hcmlkgnr6e6swr` (`booked_by`),
  KEY `FKf7wqlk90hr63y665xhgpet09v` (`employer_id`),
  CONSTRAINT `FKf7wqlk90hr63y665xhgpet09v` FOREIGN KEY (`employer_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKmjtm3hilo15hcmlkgnr6e6swr` FOREIGN KEY (`booked_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `job_descriptions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ai_question_focus_areas` text COLLATE utf8mb4_unicode_ci,
  `ai_safety_score` int DEFAULT NULL,
  `ai_screening_enabled` bit(1) DEFAULT NULL,
  `ai_screening_pass_score` int DEFAULT NULL,
  `ai_screening_track` enum('LIVE_CODING','SYSTEM_DESIGN','ASYNC_TECHNICAL','CULTURAL_FIT') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `annual_bonus` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `company_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `decided_at` datetime(6) DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `department` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `employment_type` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `equity_band` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `experience_level` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `location` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `position` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `required_skills` text COLLATE utf8mb4_unicode_ci,
  `requirements` text COLLATE utf8mb4_unicode_ci,
  `review_note` text COLLATE utf8mb4_unicode_ci,
  `salary_currency` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `salary_max` bigint DEFAULT NULL,
  `salary_min` bigint DEFAULT NULL,
  `salary_range` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` enum('DRAFT','PENDING_APPROVAL','OPEN','CLOSED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `submitted_at` datetime(6) DEFAULT NULL,
  `timezone_requirement` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `workplace_model` enum('ONSITE','HYBRID','REMOTE') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `employer_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKeiy4rdptt1x2k9vuptt9uq934` (`employer_id`),
  CONSTRAINT `FKeiy4rdptt1x2k9vuptt9uq934` FOREIGN KEY (`employer_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `job_moderation_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `action` enum('SUBMITTED','RESUBMITTED','WITHDRAWN','APPROVED','REJECTED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `actor_role` enum('EMPLOYER','ADMIN','STAFF') COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `note` text COLLATE utf8mb4_unicode_ci,
  `processing_seconds` bigint DEFAULT NULL,
  `actor_id` bigint NOT NULL,
  `job_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_jmr_job_created` (`job_id`,`created_at`),
  KEY `FKex8g2i76lm48cjmm84ob8u11t` (`actor_id`),
  CONSTRAINT `FKex8g2i76lm48cjmm84ob8u11t` FOREIGN KEY (`actor_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKtcmqq911fijkbyhgymg9159cl` FOREIGN KEY (`job_id`) REFERENCES `job_descriptions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `orders` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` bigint NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `order_code` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `package_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `paid_at` datetime(6) DEFAULT NULL,
  `payment_method` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` enum('PENDING','PAID','CANCELLED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `package_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_dhk2umg8ijjkg4njg6891trit` (`order_code`),
  KEY `FKn9o3nfwqdryrsu7esapxyw3of` (`package_id`),
  KEY `FK32ql8ubntj5uh44ph9659tiih` (`user_id`),
  CONSTRAINT `FK32ql8ubntj5uh44ph9659tiih` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKn9o3nfwqdryrsu7esapxyw3of` FOREIGN KEY (`package_id`) REFERENCES `service_packages` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `password_reset_attempts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `account` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_reset_account_created` (`account`,`created_at`),
  KEY `idx_reset_address_created` (`address`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `recovery_codes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code_hash` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `used_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_recovery_user_used` (`user_id`,`used_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `reviews` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `comment` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `rating` int NOT NULL,
  `replied_at` datetime(6) DEFAULT NULL,
  `reply` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `job_id` bigint DEFAULT NULL,
  `replied_by` bigint DEFAULT NULL,
  `reviewer_id` bigint NOT NULL,
  `target_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK3rj9gf7swaf4pwqn7p1o1mnv4` (`job_id`),
  KEY `FK3hsuekf5143pu67heuqb311d8` (`replied_by`),
  KEY `FKd1isgfajhtdl8mgg29up6mofi` (`reviewer_id`),
  KEY `FK7nxdogqtq60bypxrtk2rhvq56` (`target_id`),
  CONSTRAINT `FK3hsuekf5143pu67heuqb311d8` FOREIGN KEY (`replied_by`) REFERENCES `users` (`id`),
  CONSTRAINT `FK3rj9gf7swaf4pwqn7p1o1mnv4` FOREIGN KEY (`job_id`) REFERENCES `job_descriptions` (`id`),
  CONSTRAINT `FK7nxdogqtq60bypxrtk2rhvq56` FOREIGN KEY (`target_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKd1isgfajhtdl8mgg29up6mofi` FOREIGN KEY (`reviewer_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `service_package_features` (
  `package_id` bigint NOT NULL,
  `feature` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `idx` int NOT NULL,
  PRIMARY KEY (`package_id`,`idx`),
  CONSTRAINT `FKaftnwktxdbu05f7tm40epsiju` FOREIGN KEY (`package_id`) REFERENCES `service_packages` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `service_packages` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `description` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `duration_days` int NOT NULL,
  `job_post_limit` int NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `price` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_chi9s9s0lxqdmla9ebqhlp34x` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `study_answer` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `answer` text COLLATE utf8mb4_unicode_ci,
  `attempt_count` int DEFAULT NULL,
  `is_correct` bit(1) DEFAULT NULL,
  `pool_question_id` bigint DEFAULT NULL,
  `score` double DEFAULT NULL,
  `submitted_at` datetime(6) NOT NULL,
  `question_id` bigint NOT NULL,
  `topic_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `ai_evaluation` text COLLATE utf8mb4_unicode_ci,
  `graded_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_study_answer_topic_question` (`topic_id`,`question_id`),
  KEY `idx_study_answer_user_pool` (`user_id`,`pool_question_id`),
  KEY `FKaok5f2it221dnuxy7vtfs96lh` (`question_id`),
  CONSTRAINT `FK60wt1t0jnfhftstj5m40x5rm1` FOREIGN KEY (`topic_id`) REFERENCES `study_topic` (`id`),
  CONSTRAINT `FK92q0fgtim8e8qth00umij7xdb` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKaok5f2it221dnuxy7vtfs96lh` FOREIGN KEY (`question_id`) REFERENCES `question` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `system_settings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `setting_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `setting_value` text COLLATE utf8mb4_unicode_ci,
  `updated_at` datetime(6) DEFAULT NULL,
  `updated_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_nm18l4pyovtvd8y3b3x0l2y64` (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Cột còn thiếu trên bảng đã tồn tại.
SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin_audit_log' AND COLUMN_NAME = 'reason'), 'DO 0', 'ALTER TABLE `admin_audit_log` ADD COLUMN `reason` varchar(500) NULL');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin_audit_log' AND COLUMN_NAME = 'succeeded'), 'DO 0', 'ALTER TABLE `admin_audit_log` ADD COLUMN `succeeded` tinyint(1) NOT NULL DEFAULT ''1''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question' AND COLUMN_NAME = 'fingerprint'), 'DO 0', 'ALTER TABLE `question` ADD COLUMN `fingerprint` varchar(64) NULL');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question' AND COLUMN_NAME = 'language'), 'DO 0', 'ALTER TABLE `question` ADD COLUMN `language` varchar(8) NULL');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question' AND COLUMN_NAME = 'pool_question_id'), 'DO 0', 'ALTER TABLE `question` ADD COLUMN `pool_question_id` bigint NULL');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question' AND COLUMN_NAME = 'skill_key'), 'DO 0', 'ALTER TABLE `question` ADD COLUMN `skill_key` varchar(100) NULL');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'register_attempts' AND COLUMN_NAME = 'id_user'), 'DO 0', 'ALTER TABLE `register_attempts` ADD COLUMN `id_user` bigint NULL');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'locked_at'), 'DO 0', 'ALTER TABLE `users` ADD COLUMN `locked_at` datetime(6) NULL');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'totp_enabled'), 'DO 0', 'ALTER TABLE `users` ADD COLUMN `totp_enabled` bit(1) NOT NULL');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'totp_secret'), 'DO 0', 'ALTER TABLE `users` ADD COLUMN `totp_secret` varchar(64) NULL');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. Index còn thiếu.
SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_question_cache' AND INDEX_NAME = 'UK_4cx1w6uw1ci2qvrxqr2jji1bb'), 'DO 0', 'CREATE UNIQUE INDEX `UK_4cx1w6uw1ci2qvrxqr2jji1bb` ON `ai_question_cache` (`cache_key`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'application_status_history' AND INDEX_NAME = 'FKs9oq6j94mp3sfo0utx6jwrnuu'), 'DO 0', 'CREATE INDEX `FKs9oq6j94mp3sfo0utx6jwrnuu` ON `application_status_history` (`actor_user_id`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'application_status_history' AND INDEX_NAME = 'IDXrm6mpns2hjdhttvke2fcd1ghj'), 'DO 0', 'CREATE INDEX `IDXrm6mpns2hjdhttvke2fcd1ghj` ON `application_status_history` (`application_id`, `acted_at`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'applications' AND INDEX_NAME = 'FK32iahkg1fqci0pt76uql80nj7'), 'DO 0', 'CREATE INDEX `FK32iahkg1fqci0pt76uql80nj7` ON `applications` (`candidate_id`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'applications' AND INDEX_NAME = 'FKcnotu5pv0ovnaildnl6js5ooe'), 'DO 0', 'CREATE INDEX `FKcnotu5pv0ovnaildnl6js5ooe` ON `applications` (`job_id`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'applications' AND INDEX_NAME = 'UK_b7rtl5dgagcow2n7cyklerqmn'), 'DO 0', 'CREATE UNIQUE INDEX `UK_b7rtl5dgagcow2n7cyklerqmn` ON `applications` (`slot_id`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bug_reports' AND INDEX_NAME = 'FKhn1htso7mfspf3f46l07o0vy5'), 'DO 0', 'CREATE INDEX `FKhn1htso7mfspf3f46l07o0vy5` ON `bug_reports` (`replied_by`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bug_reports' AND INDEX_NAME = 'FKkh0ybgasqj65g7pu64gj8x2w1'), 'DO 0', 'CREATE INDEX `FKkh0ybgasqj65g7pu64gj8x2w1` ON `bug_reports` (`reporter_id`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'candidate_profiles' AND INDEX_NAME = 'UK_m7asead9kaplln9cdupjat1cq'), 'DO 0', 'CREATE UNIQUE INDEX `UK_m7asead9kaplln9cdupjat1cq` ON `candidate_profiles` (`user_id`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cover_letters' AND INDEX_NAME = 'FK5rwdil49up7u4sgx1pgbeqjyg'), 'DO 0', 'CREATE INDEX `FK5rwdil49up7u4sgx1pgbeqjyg` ON `cover_letters` (`candidate_id`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cover_letters' AND INDEX_NAME = 'FK6196w0ex2wdii5i6u6qvkda8k'), 'DO 0', 'CREATE INDEX `FK6196w0ex2wdii5i6u6qvkda8k` ON `cover_letters` (`job_id`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flyway_schema_history' AND INDEX_NAME = 'flyway_schema_history_s_idx'), 'DO 0', 'CREATE INDEX `flyway_schema_history_s_idx` ON `flyway_schema_history` (`success`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_answers' AND INDEX_NAME = 'FKjeqvvamvdarrcbswn6kkiuym9'), 'DO 0', 'CREATE INDEX `FKjeqvvamvdarrcbswn6kkiuym9` ON `interview_answers` (`session_id`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_report' AND INDEX_NAME = 'UK_pevyr8qm2arws90erkjmiupji'), 'DO 0', 'CREATE UNIQUE INDEX `UK_pevyr8qm2arws90erkjmiupji` ON `interview_report` (`session_id`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_sessions' AND INDEX_NAME = 'FKbftxb58g69ngiu23mp4nt8f3j'), 'DO 0', 'CREATE INDEX `FKbftxb58g69ngiu23mp4nt8f3j` ON `interview_sessions` (`job_id`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_sessions' AND INDEX_NAME = 'FKdgvxiy3lscety5o48nl8tt4o1'), 'DO 0', 'CREATE INDEX `FKdgvxiy3lscety5o48nl8tt4o1` ON `interview_sessions` (`candidate_id`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_slots' AND INDEX_NAME = 'FKf7wqlk90hr63y665xhgpet09v'), 'DO 0', 'CREATE INDEX `FKf7wqlk90hr63y665xhgpet09v` ON `interview_slots` (`employer_id`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_slots' AND INDEX_NAME = 'FKmjtm3hilo15hcmlkgnr6e6swr'), 'DO 0', 'CREATE INDEX `FKmjtm3hilo15hcmlkgnr6e6swr` ON `interview_slots` (`booked_by`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'job_descriptions' AND INDEX_NAME = 'FKeiy4rdptt1x2k9vuptt9uq934'), 'DO 0', 'CREATE INDEX `FKeiy4rdptt1x2k9vuptt9uq934` ON `job_descriptions` (`employer_id`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'job_moderation_records' AND INDEX_NAME = 'FKex8g2i76lm48cjmm84ob8u11t'), 'DO 0', 'CREATE INDEX `FKex8g2i76lm48cjmm84ob8u11t` ON `job_moderation_records` (`actor_id`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'job_moderation_records' AND INDEX_NAME = 'idx_jmr_job_created'), 'DO 0', 'CREATE INDEX `idx_jmr_job_created` ON `job_moderation_records` (`job_id`, `created_at`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'login_attempts' AND INDEX_NAME = 'idx_login_email_timestamp'), 'DO 0', 'CREATE INDEX `idx_login_email_timestamp` ON `login_attempts` (`email`, `created_at`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'login_attempts' AND INDEX_NAME = 'idx_login_ip_timestamp'), 'DO 0', 'CREATE INDEX `idx_login_ip_timestamp` ON `login_attempts` (`ip_address`, `created_at`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND INDEX_NAME = 'FK32ql8ubntj5uh44ph9659tiih'), 'DO 0', 'CREATE INDEX `FK32ql8ubntj5uh44ph9659tiih` ON `orders` (`user_id`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND INDEX_NAME = 'FKn9o3nfwqdryrsu7esapxyw3of'), 'DO 0', 'CREATE INDEX `FKn9o3nfwqdryrsu7esapxyw3of` ON `orders` (`package_id`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND INDEX_NAME = 'UK_dhk2umg8ijjkg4njg6891trit'), 'DO 0', 'CREATE UNIQUE INDEX `UK_dhk2umg8ijjkg4njg6891trit` ON `orders` (`order_code`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'password_reset_attempts' AND INDEX_NAME = 'idx_reset_account_created'), 'DO 0', 'CREATE INDEX `idx_reset_account_created` ON `password_reset_attempts` (`account`, `created_at`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'password_reset_attempts' AND INDEX_NAME = 'idx_reset_address_created'), 'DO 0', 'CREATE INDEX `idx_reset_address_created` ON `password_reset_attempts` (`address`, `created_at`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'position_catalog' AND INDEX_NAME = 'UK_cnxk2qwbkn0qndeagbhcw8d3c'), 'DO 0', 'CREATE UNIQUE INDEX `UK_cnxk2qwbkn0qndeagbhcw8d3c` ON `position_catalog` (`slug`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question' AND INDEX_NAME = 'idx_question_pool_cell'), 'DO 0', 'CREATE INDEX `idx_question_pool_cell` ON `question` (`topic_id`, `skill_key`, `difficulty`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question' AND INDEX_NAME = 'uk_question_fingerprint'), 'DO 0', 'CREATE UNIQUE INDEX `uk_question_fingerprint` ON `question` (`fingerprint`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question' AND INDEX_NAME = 'uk_question_skill_content'), 'DO 0', 'CREATE UNIQUE INDEX `uk_question_skill_content` ON `question` (`skill_key`, `content_key`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'recovery_codes' AND INDEX_NAME = 'idx_recovery_user_used'), 'DO 0', 'CREATE INDEX `idx_recovery_user_used` ON `recovery_codes` (`user_id`, `used_at`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'register_attempts' AND INDEX_NAME = 'idx_register_account'), 'DO 0', 'CREATE INDEX `idx_register_account` ON `register_attempts` (`account`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'register_attempts' AND INDEX_NAME = 'idx_register_address_timestamp'), 'DO 0', 'CREATE INDEX `idx_register_address_timestamp` ON `register_attempts` (`address`, `timestamp`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reviews' AND INDEX_NAME = 'FK3hsuekf5143pu67heuqb311d8'), 'DO 0', 'CREATE INDEX `FK3hsuekf5143pu67heuqb311d8` ON `reviews` (`replied_by`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reviews' AND INDEX_NAME = 'FK3rj9gf7swaf4pwqn7p1o1mnv4'), 'DO 0', 'CREATE INDEX `FK3rj9gf7swaf4pwqn7p1o1mnv4` ON `reviews` (`job_id`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reviews' AND INDEX_NAME = 'FK7nxdogqtq60bypxrtk2rhvq56'), 'DO 0', 'CREATE INDEX `FK7nxdogqtq60bypxrtk2rhvq56` ON `reviews` (`target_id`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reviews' AND INDEX_NAME = 'FKd1isgfajhtdl8mgg29up6mofi'), 'DO 0', 'CREATE INDEX `FKd1isgfajhtdl8mgg29up6mofi` ON `reviews` (`reviewer_id`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'service_packages' AND INDEX_NAME = 'UK_chi9s9s0lxqdmla9ebqhlp34x'), 'DO 0', 'CREATE UNIQUE INDEX `UK_chi9s9s0lxqdmla9ebqhlp34x` ON `service_packages` (`name`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'study_answer' AND INDEX_NAME = 'FKaok5f2it221dnuxy7vtfs96lh'), 'DO 0', 'CREATE INDEX `FKaok5f2it221dnuxy7vtfs96lh` ON `study_answer` (`question_id`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'study_answer' AND INDEX_NAME = 'idx_study_answer_user_pool'), 'DO 0', 'CREATE INDEX `idx_study_answer_user_pool` ON `study_answer` (`user_id`, `pool_question_id`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'study_answer' AND INDEX_NAME = 'uk_study_answer_topic_question'), 'DO 0', 'CREATE UNIQUE INDEX `uk_study_answer_topic_question` ON `study_answer` (`topic_id`, `question_id`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'study_program' AND INDEX_NAME = 'FK8uyxesolxqrw6l0fnuoymj8nn'), 'DO 0', 'CREATE INDEX `FK8uyxesolxqrw6l0fnuoymj8nn` ON `study_program` (`cv_id`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'study_program' AND INDEX_NAME = 'FKcunsmskccbwelqvuache79ujs'), 'DO 0', 'CREATE INDEX `FKcunsmskccbwelqvuache79ujs` ON `study_program` (`user_id`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'study_topic' AND INDEX_NAME = 'FKjf2m8jjiv6agcdcwkrnyednhd'), 'DO 0', 'CREATE INDEX `FKjf2m8jjiv6agcdcwkrnyednhd` ON `study_topic` (`program_id`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'system_settings' AND INDEX_NAME = 'UK_nm18l4pyovtvd8y3b3x0l2y64'), 'DO 0', 'CREATE UNIQUE INDEX `UK_nm18l4pyovtvd8y3b3x0l2y64` ON `system_settings` (`setting_key`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_cv' AND INDEX_NAME = 'FKpk3nnejjvnpe345aaq2uo3lrj'), 'DO 0', 'CREATE INDEX `FKpk3nnejjvnpe345aaq2uo3lrj` ON `user_cv` (`user_id`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_session' AND INDEX_NAME = 'UK_4b83gqotsn40t2wbaf6ywu271'), 'DO 0', 'CREATE UNIQUE INDEX `UK_4b83gqotsn40t2wbaf6ywu271` ON `user_session` (`session_id`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_session' AND INDEX_NAME = 'idx_session_session_id'), 'DO 0', 'CREATE INDEX `idx_session_session_id` ON `user_session` (`session_id`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_skill' AND INDEX_NAME = 'FK7etr7172w5gsaakaisro3luqy'), 'DO 0', 'CREATE INDEX `FK7etr7172w5gsaakaisro3luqy` ON `user_skill` (`cv_id`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND INDEX_NAME = 'UK_6dotkott2kjsp8vw4d0m25fb7'), 'DO 0', 'CREATE UNIQUE INDEX `UK_6dotkott2kjsp8vw4d0m25fb7` ON `users` (`email`)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. Khoá ngoại còn thiếu.
SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'application_status_history' AND CONSTRAINT_NAME = 'FKfdvnrm195jdlti0syxo6qah6h'), 'DO 0', 'ALTER TABLE `application_status_history` ADD CONSTRAINT `FKfdvnrm195jdlti0syxo6qah6h` FOREIGN KEY (`application_id`) REFERENCES `applications` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'application_status_history' AND CONSTRAINT_NAME = 'FKs9oq6j94mp3sfo0utx6jwrnuu'), 'DO 0', 'ALTER TABLE `application_status_history` ADD CONSTRAINT `FKs9oq6j94mp3sfo0utx6jwrnuu` FOREIGN KEY (`actor_user_id`) REFERENCES `users` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'applications' AND CONSTRAINT_NAME = 'FK32iahkg1fqci0pt76uql80nj7'), 'DO 0', 'ALTER TABLE `applications` ADD CONSTRAINT `FK32iahkg1fqci0pt76uql80nj7` FOREIGN KEY (`candidate_id`) REFERENCES `users` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'applications' AND CONSTRAINT_NAME = 'FKcnotu5pv0ovnaildnl6js5ooe'), 'DO 0', 'ALTER TABLE `applications` ADD CONSTRAINT `FKcnotu5pv0ovnaildnl6js5ooe` FOREIGN KEY (`job_id`) REFERENCES `job_descriptions` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'applications' AND CONSTRAINT_NAME = 'FKpxho5jbrat7uq43gutv7p8t55'), 'DO 0', 'ALTER TABLE `applications` ADD CONSTRAINT `FKpxho5jbrat7uq43gutv7p8t55` FOREIGN KEY (`slot_id`) REFERENCES `interview_slots` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bug_reports' AND CONSTRAINT_NAME = 'FKhn1htso7mfspf3f46l07o0vy5'), 'DO 0', 'ALTER TABLE `bug_reports` ADD CONSTRAINT `FKhn1htso7mfspf3f46l07o0vy5` FOREIGN KEY (`replied_by`) REFERENCES `users` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bug_reports' AND CONSTRAINT_NAME = 'FKkh0ybgasqj65g7pu64gj8x2w1'), 'DO 0', 'ALTER TABLE `bug_reports` ADD CONSTRAINT `FKkh0ybgasqj65g7pu64gj8x2w1` FOREIGN KEY (`reporter_id`) REFERENCES `users` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'candidate_profiles' AND CONSTRAINT_NAME = 'FKn7b2se0y378uox9e3aw2bjg13'), 'DO 0', 'ALTER TABLE `candidate_profiles` ADD CONSTRAINT `FKn7b2se0y378uox9e3aw2bjg13` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cover_letters' AND CONSTRAINT_NAME = 'FK5rwdil49up7u4sgx1pgbeqjyg'), 'DO 0', 'ALTER TABLE `cover_letters` ADD CONSTRAINT `FK5rwdil49up7u4sgx1pgbeqjyg` FOREIGN KEY (`candidate_id`) REFERENCES `users` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cover_letters' AND CONSTRAINT_NAME = 'FK6196w0ex2wdii5i6u6qvkda8k'), 'DO 0', 'ALTER TABLE `cover_letters` ADD CONSTRAINT `FK6196w0ex2wdii5i6u6qvkda8k` FOREIGN KEY (`job_id`) REFERENCES `job_descriptions` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_answers' AND CONSTRAINT_NAME = 'FKjeqvvamvdarrcbswn6kkiuym9'), 'DO 0', 'ALTER TABLE `interview_answers` ADD CONSTRAINT `FKjeqvvamvdarrcbswn6kkiuym9` FOREIGN KEY (`session_id`) REFERENCES `interview_sessions` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_report' AND CONSTRAINT_NAME = 'FK81trh10275wjkacq2l9pyyt5m'), 'DO 0', 'ALTER TABLE `interview_report` ADD CONSTRAINT `FK81trh10275wjkacq2l9pyyt5m` FOREIGN KEY (`session_id`) REFERENCES `interview_sessions` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_session_questions' AND CONSTRAINT_NAME = 'FKbox2xjm70mrtgg0biwwy8a1og'), 'DO 0', 'ALTER TABLE `interview_session_questions` ADD CONSTRAINT `FKbox2xjm70mrtgg0biwwy8a1og` FOREIGN KEY (`session_id`) REFERENCES `interview_sessions` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_sessions' AND CONSTRAINT_NAME = 'FKbftxb58g69ngiu23mp4nt8f3j'), 'DO 0', 'ALTER TABLE `interview_sessions` ADD CONSTRAINT `FKbftxb58g69ngiu23mp4nt8f3j` FOREIGN KEY (`job_id`) REFERENCES `job_descriptions` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_sessions' AND CONSTRAINT_NAME = 'FKdgvxiy3lscety5o48nl8tt4o1'), 'DO 0', 'ALTER TABLE `interview_sessions` ADD CONSTRAINT `FKdgvxiy3lscety5o48nl8tt4o1` FOREIGN KEY (`candidate_id`) REFERENCES `users` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_slots' AND CONSTRAINT_NAME = 'FKf7wqlk90hr63y665xhgpet09v'), 'DO 0', 'ALTER TABLE `interview_slots` ADD CONSTRAINT `FKf7wqlk90hr63y665xhgpet09v` FOREIGN KEY (`employer_id`) REFERENCES `users` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_slots' AND CONSTRAINT_NAME = 'FKmjtm3hilo15hcmlkgnr6e6swr'), 'DO 0', 'ALTER TABLE `interview_slots` ADD CONSTRAINT `FKmjtm3hilo15hcmlkgnr6e6swr` FOREIGN KEY (`booked_by`) REFERENCES `users` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'job_descriptions' AND CONSTRAINT_NAME = 'FKeiy4rdptt1x2k9vuptt9uq934'), 'DO 0', 'ALTER TABLE `job_descriptions` ADD CONSTRAINT `FKeiy4rdptt1x2k9vuptt9uq934` FOREIGN KEY (`employer_id`) REFERENCES `users` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'job_moderation_records' AND CONSTRAINT_NAME = 'FKex8g2i76lm48cjmm84ob8u11t'), 'DO 0', 'ALTER TABLE `job_moderation_records` ADD CONSTRAINT `FKex8g2i76lm48cjmm84ob8u11t` FOREIGN KEY (`actor_id`) REFERENCES `users` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'job_moderation_records' AND CONSTRAINT_NAME = 'FKtcmqq911fijkbyhgymg9159cl'), 'DO 0', 'ALTER TABLE `job_moderation_records` ADD CONSTRAINT `FKtcmqq911fijkbyhgymg9159cl` FOREIGN KEY (`job_id`) REFERENCES `job_descriptions` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND CONSTRAINT_NAME = 'FK32ql8ubntj5uh44ph9659tiih'), 'DO 0', 'ALTER TABLE `orders` ADD CONSTRAINT `FK32ql8ubntj5uh44ph9659tiih` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND CONSTRAINT_NAME = 'FKn9o3nfwqdryrsu7esapxyw3of'), 'DO 0', 'ALTER TABLE `orders` ADD CONSTRAINT `FKn9o3nfwqdryrsu7esapxyw3of` FOREIGN KEY (`package_id`) REFERENCES `service_packages` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'position_required_skill' AND CONSTRAINT_NAME = 'FKoxlofvr35y8inub80rp6jo9gw'), 'DO 0', 'ALTER TABLE `position_required_skill` ADD CONSTRAINT `FKoxlofvr35y8inub80rp6jo9gw` FOREIGN KEY (`position_id`) REFERENCES `position_catalog` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question' AND CONSTRAINT_NAME = 'FK80d9l9l1rq9o23njpa8j0cqyo'), 'DO 0', 'ALTER TABLE `question` ADD CONSTRAINT `FK80d9l9l1rq9o23njpa8j0cqyo` FOREIGN KEY (`topic_id`) REFERENCES `study_topic` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reviews' AND CONSTRAINT_NAME = 'FK3hsuekf5143pu67heuqb311d8'), 'DO 0', 'ALTER TABLE `reviews` ADD CONSTRAINT `FK3hsuekf5143pu67heuqb311d8` FOREIGN KEY (`replied_by`) REFERENCES `users` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reviews' AND CONSTRAINT_NAME = 'FK3rj9gf7swaf4pwqn7p1o1mnv4'), 'DO 0', 'ALTER TABLE `reviews` ADD CONSTRAINT `FK3rj9gf7swaf4pwqn7p1o1mnv4` FOREIGN KEY (`job_id`) REFERENCES `job_descriptions` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reviews' AND CONSTRAINT_NAME = 'FK7nxdogqtq60bypxrtk2rhvq56'), 'DO 0', 'ALTER TABLE `reviews` ADD CONSTRAINT `FK7nxdogqtq60bypxrtk2rhvq56` FOREIGN KEY (`target_id`) REFERENCES `users` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reviews' AND CONSTRAINT_NAME = 'FKd1isgfajhtdl8mgg29up6mofi'), 'DO 0', 'ALTER TABLE `reviews` ADD CONSTRAINT `FKd1isgfajhtdl8mgg29up6mofi` FOREIGN KEY (`reviewer_id`) REFERENCES `users` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'service_package_features' AND CONSTRAINT_NAME = 'FKaftnwktxdbu05f7tm40epsiju'), 'DO 0', 'ALTER TABLE `service_package_features` ADD CONSTRAINT `FKaftnwktxdbu05f7tm40epsiju` FOREIGN KEY (`package_id`) REFERENCES `service_packages` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'study_answer' AND CONSTRAINT_NAME = 'FK60wt1t0jnfhftstj5m40x5rm1'), 'DO 0', 'ALTER TABLE `study_answer` ADD CONSTRAINT `FK60wt1t0jnfhftstj5m40x5rm1` FOREIGN KEY (`topic_id`) REFERENCES `study_topic` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'study_answer' AND CONSTRAINT_NAME = 'FK92q0fgtim8e8qth00umij7xdb'), 'DO 0', 'ALTER TABLE `study_answer` ADD CONSTRAINT `FK92q0fgtim8e8qth00umij7xdb` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'study_answer' AND CONSTRAINT_NAME = 'FKaok5f2it221dnuxy7vtfs96lh'), 'DO 0', 'ALTER TABLE `study_answer` ADD CONSTRAINT `FKaok5f2it221dnuxy7vtfs96lh` FOREIGN KEY (`question_id`) REFERENCES `question` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'study_program' AND CONSTRAINT_NAME = 'FK8uyxesolxqrw6l0fnuoymj8nn'), 'DO 0', 'ALTER TABLE `study_program` ADD CONSTRAINT `FK8uyxesolxqrw6l0fnuoymj8nn` FOREIGN KEY (`cv_id`) REFERENCES `user_cv` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'study_program' AND CONSTRAINT_NAME = 'FKcunsmskccbwelqvuache79ujs'), 'DO 0', 'ALTER TABLE `study_program` ADD CONSTRAINT `FKcunsmskccbwelqvuache79ujs` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'study_topic' AND CONSTRAINT_NAME = 'FKjf2m8jjiv6agcdcwkrnyednhd'), 'DO 0', 'ALTER TABLE `study_topic` ADD CONSTRAINT `FKjf2m8jjiv6agcdcwkrnyednhd` FOREIGN KEY (`program_id`) REFERENCES `study_program` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_cv' AND CONSTRAINT_NAME = 'FKpk3nnejjvnpe345aaq2uo3lrj'), 'DO 0', 'ALTER TABLE `user_cv` ADD CONSTRAINT `FKpk3nnejjvnpe345aaq2uo3lrj` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_skill' AND CONSTRAINT_NAME = 'FK7etr7172w5gsaakaisro3luqy'), 'DO 0', 'ALTER TABLE `user_skill` ADD CONSTRAINT `FK7etr7172w5gsaakaisro3luqy` FOREIGN KEY (`cv_id`) REFERENCES `user_cv` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;
