package com.aiinterview.platform.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * DB cũ tạo wallet_transactions.type kiểu ENUM('...','ZALOPAY_TOPUP','MOMO_TOPUP').
 * Khi đổi sang PAYOS_TOPUP, Hibernate ddl-auto=update KHÔNG ALTER được danh sách giá trị
 * của ENUM -> MySQL ném "Data truncated for column 'type'" và rollback toàn bộ giao dịch
 * cộng ví (biểu hiện: thanh toán thành công nhưng /payos/return 500, ví không tăng).
 *
 * Chuyển cột sang VARCHAR(40) đúng với @Enumerated(EnumType.STRING) trong entity.
 * Chạy mỗi lần khởi động nhưng chỉ ALTER khi cột còn là enum, nên an toàn/idempotent.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WalletTransactionTypeColumnMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        String dataType;
        try {
            dataType = jdbcTemplate.queryForObject("""
                    SELECT DATA_TYPE FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'wallet_transactions'
                      AND COLUMN_NAME = 'type'
                    """, String.class);
        } catch (Exception e) {
            log.warn("Cannot inspect wallet_transactions.type column: {}", e.getMessage());
            return;
        }

        if (dataType == null || !"enum".equalsIgnoreCase(dataType)) {
            return;
        }

        jdbcTemplate.execute("ALTER TABLE wallet_transactions MODIFY COLUMN type VARCHAR(40) NOT NULL");
        log.info("Migrated wallet_transactions.type from ENUM to VARCHAR(40) for PAYOS_TOPUP");
    }
}
