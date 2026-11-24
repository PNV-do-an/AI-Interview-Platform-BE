-- Chạy tay nếu cần (spring.flyway.enabled=false). App cũng tự ALTER qua
-- WalletTransactionTypeColumnMigration khi phát hiện cột type vẫn là ENUM.
--
-- DB cũ: enum('TOPUP',...,'ZALOPAY_TOPUP','MOMO_TOPUP') -> insert PAYOS_TOPUP bị
-- "Data truncated for column 'type'" -> rollback -> không cộng ví, /payos/return 500.
ALTER TABLE wallet_transactions MODIFY COLUMN type VARCHAR(40) NOT NULL;
